namespace AudioBridge;

/// <summary>Constants of the wire protocol shared with the Android app (see docs/PROTOCOL.md).</summary>
static class Protocol
{
    public const int SampleRate = 48000;
    public const int Channels = 2;
    public const int FrameSamples = 480;   // 10 ms per Opus frame
    public const int MaxPayload = 1500;

    // Message types
    public const byte Audio = 1;           // PC -> phone: Opus frame of the PC audio
    public const byte Mic = 2;             // phone -> PC: Opus frame of the phone microphone
    public const byte Info = 3;            // phone -> PC: JSON with device, headphone and buffer status
    public const byte MicControl = 4;      // PC -> phone: 1 = start microphone, 0 = stop

    /// <summary>"PCB2" + sample rate (int32) + channel count (byte).</summary>
    public static byte[] CreateHandshake()
    {
        var handshake = new byte[9];
        "PCB2"u8.CopyTo(handshake);
        BitConverter.GetBytes(SampleRate).CopyTo(handshake, 4);
        handshake[8] = Channels;
        return handshake;
    }
}
