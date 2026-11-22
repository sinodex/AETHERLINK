namespace AudioBridge;

/// <summary>One connection to the phone: runs until anything fails, then the caller reconnects.</summary>
static class Session
{
    public static async Task RunAsync(Options options, MicBridge mic)
    {
        using var phone = await PhoneConnection.ConnectAsync(options.Port);
        Console.WriteLine("Verbunden mit Handy.");

        // The first failure of any component ends the session.
        var failed = new TaskCompletionSource();
        void Fail(Exception ex) => failed.TrySetException(ex);

        _ = Task.Run(() => ReceiveLoop(phone, mic, Fail));
        _ = Task.Run(() => MicRequestLoop(phone, mic, options.MicAlways, failed.Task, Fail));

        using var sender = new LoopbackSender(phone, Fail);
        sender.Start();
        await failed.Task;
    }

    /// <summary>Reads status and microphone frames coming from the phone.</summary>
    static void ReceiveLoop(PhoneConnection phone, MicBridge mic, Action<Exception> fail)
    {
        try
        {
            var payload = new byte[4096];
            var printer = new PhoneInfoPrinter();
            while (true)
            {
                var (type, length) = phone.Receive(payload);
                if (type == Protocol.Info) printer.Print(payload.AsMemory(0, length));
                else if (type == Protocol.Mic) mic.Play(payload.AsSpan(0, length));
            }
        }
        catch (Exception ex)
        {
            fail(ex);
        }
    }

    /// <summary>Asks the phone to record only while an app on the PC is using the virtual microphone.</summary>
    static async Task MicRequestLoop(PhoneConnection phone, MicBridge mic, bool always, Task stop, Action<Exception> fail)
    {
        bool requested = false;
        int quietPolls = 0;
        try
        {
            while (!stop.IsCompleted)
            {
                bool wanted = mic.IsWanted(always);
                quietPolls = wanted ? 0 : quietPolls + 1;

                // Keep the microphone on for ~2 s after the app stops, so it does not flicker.
                bool desired = wanted || (requested && quietPolls < 6);
                if (desired != requested)
                {
                    requested = desired;
                    phone.Send(Protocol.MicControl, new byte[] { (byte)(desired ? 1 : 0) });
                    Console.WriteLine(desired
                        ? "Eine App nutzt das Mikrofon -> Handy-Mikro AN"
                        : "Mikrofon wird nicht mehr genutzt -> Handy-Mikro AUS");
                }
                await Task.Delay(300);
            }
        }
        catch (Exception ex)
        {
            fail(ex);
        }
    }
}
