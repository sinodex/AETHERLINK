using System.Net.Sockets;

namespace AudioBridge;

/// <summary>TCP link to the phone (through "adb forward") carrying framed messages in both directions.</summary>
sealed class PhoneConnection : IDisposable
{
    readonly TcpClient client;
    readonly NetworkStream stream;
    readonly object sendLock = new();
    readonly byte[] sendBuffer = new byte[3 + Protocol.MaxPayload];
    readonly byte[] header = new byte[3];

    PhoneConnection(TcpClient client)
    {
        this.client = client;
        stream = client.GetStream();
    }

    public static async Task<PhoneConnection> ConnectAsync(int port)
    {
        var client = new TcpClient { NoDelay = true };
        try
        {
            await client.ConnectAsync("127.0.0.1", port);
            var phone = new PhoneConnection(client);
            lock (phone.sendLock) phone.stream.Write(Protocol.CreateHandshake());
            return phone;
        }
        catch
        {
            client.Dispose();
            throw;
        }
    }

    /// <summary>Sends one message. Safe to call from several threads.</summary>
    public void Send(byte type, ReadOnlySpan<byte> payload)
    {
        lock (sendLock)
        {
            // Header and payload go out in a single write so each frame is one TCP segment.
            sendBuffer[0] = type;
            sendBuffer[1] = (byte)payload.Length;
            sendBuffer[2] = (byte)(payload.Length >> 8);
            payload.CopyTo(sendBuffer.AsSpan(3));
            stream.Write(sendBuffer, 0, 3 + payload.Length);
        }
    }

    /// <summary>Blocks until the next message arrives and returns its type and payload length.</summary>
    public (byte Type, int Length) Receive(byte[] payload)
    {
        stream.ReadExactly(header);
        int length = header[1] | (header[2] << 8);
        stream.ReadExactly(payload, 0, length);
        return (header[0], length);
    }

    public void Dispose() => client.Dispose();
}
