using Concentus;
using Concentus.Enums;
using NAudio.CoreAudioApi;
using NAudio.Wave;

namespace AudioBridge;

/// <summary>Captures everything the PC plays (WASAPI loopback), encodes it with Opus and sends it to the phone.</summary>
sealed class LoopbackSender : IDisposable
{
    // Event mode gives the lowest latency; if the system refuses it we fall back to polling for good.
    static bool preferEventSync = true;

    readonly PhoneConnection phone;
    readonly Action<Exception> onFailure;
    readonly IOpusEncoder encoder;
    readonly short[] pcm = new short[Protocol.FrameSamples * Protocol.Channels];
    readonly byte[] packet = new byte[1275];   // largest possible Opus packet
    int filled;
    LowLatencyLoopback? capture;

    public LoopbackSender(PhoneConnection phone, Action<Exception> onFailure)
    {
        this.phone = phone;
        this.onFailure = onFailure;
        encoder = OpusCodecFactory.CreateEncoder(Protocol.SampleRate, Protocol.Channels,
            OpusApplication.OPUS_APPLICATION_RESTRICTED_LOWDELAY);
        encoder.Bitrate = 192_000;
    }

    public void Start()
    {
        var enumerator = new MMDeviceEnumerator();
        foreach (var eventSync in preferEventSync ? new[] { true, false } : new[] { false })
        {
            var device = enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
            var candidate = new LowLatencyLoopback(device, eventSync, 20);

            // Windows may run the device at another rate or layout; ask for 48 kHz stereo float instead.
            var format = candidate.WaveFormat;
            if (format.SampleRate != Protocol.SampleRate || format.Channels != Protocol.Channels
                || format.Encoding != WaveFormatEncoding.IeeeFloat)
            {
                Console.WriteLine($"Windows-Format ist {format} – wandle auf 48 kHz Stereo um.");
                candidate.WaveFormat = WaveFormat.CreateIeeeFloatWaveFormat(Protocol.SampleRate, Protocol.Channels);
            }

            candidate.DataAvailable += OnData;
            candidate.RecordingStopped += (_, e) =>
            {
                if (e.Exception != null) onFailure(e.Exception);
            };

            try
            {
                candidate.StartRecording();
                capture = candidate;
                if (!eventSync && preferEventSync)
                {
                    preferEventSync = false;
                    Console.WriteLine("Event-Modus nicht verfügbar – nutze Polling (20 ms).");
                }
                return;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Audio-Capture ({(eventSync ? "Event" : "Polling")}) fehlgeschlagen: {ex.Message}");
                candidate.Dispose();
            }
        }
        throw new Exception("Windows-Audio konnte nicht aufgenommen werden. Stelle die Ausgabe auf 48000 Hz / 2 Kanäle.");
    }

    void OnData(object? sender, WaveInEventArgs e)
    {
        try
        {
            // Convert float samples to 16-bit and send one Opus frame every time 10 ms have been collected.
            int samples = e.BytesRecorded / 4;
            for (int i = 0; i < samples; i++)
            {
                float sample = BitConverter.ToSingle(e.Buffer, i * 4);
                pcm[filled++] = (short)(Math.Clamp(sample, -1f, 1f) * 32767);
                if (filled < pcm.Length) continue;

                int length = encoder.Encode(pcm, Protocol.FrameSamples, packet, packet.Length);
                phone.Send(Protocol.Audio, packet.AsSpan(0, length));
                filled = 0;
            }
        }
        catch (Exception ex)
        {
            onFailure(ex);
        }
    }

    public void Dispose()
    {
        try
        {
            capture?.StopRecording();
            capture?.Dispose();
        }
        catch { }
    }
}

/// <summary>WASAPI capture of the default output device (loopback) with a configurable small buffer.</summary>
sealed class LowLatencyLoopback : WasapiCapture
{
    public LowLatencyLoopback(MMDevice device, bool useEventSync, int bufferMs) : base(device, useEventSync, bufferMs) { }

    protected override AudioClientStreamFlags GetAudioClientStreamFlags() =>
        AudioClientStreamFlags.Loopback | base.GetAudioClientStreamFlags();
}
