using System.Diagnostics;
using System.Runtime.InteropServices;
using Concentus;
using NAudio.CoreAudioApi;
using NAudio.CoreAudioApi.Interfaces;
using NAudio.Wave;

namespace AudioBridge;

/// <summary>
/// Plays the phone microphone into a virtual cable (VB-Cable) and detects whether
/// an app is currently recording from that cable, so the phone only records when needed.
/// </summary>
sealed class MicBridge : IDisposable
{
    readonly IOpusDecoder decoder = OpusCodecFactory.CreateDecoder(Protocol.SampleRate, 1);
    readonly short[] pcm = new short[5760];   // up to 120 ms of mono audio
    readonly BufferedWaveProvider? buffer;
    readonly WasapiOut? output;
    readonly MMDevice? captureDevice;

    public MicBridge(Options options)
    {
        var enumerator = new MMDeviceEnumerator();

        // Playback side of the virtual cable: this is where the phone microphone audio goes.
        var outputDevice = enumerator.EnumerateAudioEndPoints(DataFlow.Render, DeviceState.Active)
            .FirstOrDefault(d => d.FriendlyName.Contains(options.MicOutputName, StringComparison.OrdinalIgnoreCase));
        if (outputDevice != null)
        {
            buffer = new BufferedWaveProvider(new WaveFormat(Protocol.SampleRate, 16, 1))
            {
                DiscardOnBufferOverflow = true,
                BufferDuration = TimeSpan.FromMilliseconds(200)
            };
            output = new WasapiOut(outputDevice, AudioClientShareMode.Shared, true, 20);
            output.Init(buffer);
            output.Play();
            Console.WriteLine($"Mikrofon-Ausgabe: {outputDevice.FriendlyName}");
        }
        else
        {
            Console.WriteLine($"Kein Gerät \"{options.MicOutputName}\" gefunden – Handy-Mikro wird nicht ausgegeben (VB-Cable installieren).");
        }

        // Recording side of the virtual cable: this is the "microphone" other apps use.
        captureDevice = enumerator.EnumerateAudioEndPoints(DataFlow.Capture, DeviceState.Active)
            .FirstOrDefault(d => d.FriendlyName.Contains(options.MicCaptureName, StringComparison.OrdinalIgnoreCase));
        if (captureDevice == null)
            Console.WriteLine($"Kein Aufnahmegerät \"{options.MicCaptureName}\" gefunden – Mikro läuft dann dauerhaft (oder --mic-capture Name angeben).");
        else
            WarnIfListening(captureDevice);
    }

    /// <summary>Decodes one microphone frame from the phone and queues it for playback.</summary>
    public void Play(ReadOnlySpan<byte> opusFrame)
    {
        if (buffer == null) return;
        int samples = decoder.Decode(opusFrame, pcm, pcm.Length, false);
        var bytes = MemoryMarshal.AsBytes(pcm.AsSpan(0, samples)).ToArray();
        buffer.AddSamples(bytes, 0, bytes.Length);
    }

    /// <summary>True if the phone microphone should be recording right now.</summary>
    public bool IsWanted(bool always) =>
        buffer != null && (always || captureDevice == null || IsRecordedByApp(captureDevice));

    /// <summary>True while some app (not Windows itself) has an active recording session on the virtual microphone.</summary>
    static bool IsRecordedByApp(MMDevice device)
    {
        try
        {
            var manager = device.AudioSessionManager;
            manager.RefreshSessions();
            var sessions = manager.Sessions;
            for (int i = 0; i < sessions.Count; i++)
            {
                var session = sessions[i];
                if (session.State != AudioSessionState.AudioSessionStateActive) continue;

                uint pid = session.GetProcessID;
                if (pid == (uint)Environment.ProcessId) continue;
                string name = "";
                try { name = Process.GetProcessById((int)pid).ProcessName; } catch { }
                if (name.Equals("audiodg", StringComparison.OrdinalIgnoreCase)) continue;  // Windows' own "listen to this device"
                return true;
            }
        }
        catch { }
        return false;
    }

    /// <summary>"Listen to this device" on the virtual microphone plays your own voice back to you.</summary>
    static void WarnIfListening(MMDevice device)
    {
        try
        {
            var listenKey = new PropertyKey(new Guid("24dbb0fc-9311-4b3d-9cf0-18ff155639d4"), 1);
            if (device.Properties.Contains(listenKey) && device.Properties[listenKey].Value is bool listening && listening)
            {
                Console.WriteLine($"!! ACHTUNG: Bei \"{device.FriendlyName}\" ist 'Dieses Gerät wiedergeben' AN – deshalb hörst du dich selbst.");
                Console.WriteLine("   Windows: Sound-Einstellungen > Aufnahme > Gerät > Eigenschaften > Abhören > Haken entfernen.");
            }
        }
        catch { }
    }

    public void Dispose() => output?.Dispose();
}
