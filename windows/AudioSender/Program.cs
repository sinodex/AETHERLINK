using System.Diagnostics;
using AudioBridge;

var options = Options.Parse(args);

// Finer timers and a higher priority keep the capture loop from stalling.
Native.timeBeginPeriod(1);
try { Process.GetCurrentProcess().PriorityClass = ProcessPriorityClass.High; } catch { }

using var mic = new MicBridge(options);

// Reconnect forever: the phone app may be started after the sender or restarted at any time.
while (true)
{
    try
    {
        await Session.RunAsync(options, mic);
    }
    catch (Exception ex)
    {
        Console.WriteLine($"Getrennt: {ex.Message} – neuer Versuch in 2 s");
        await Task.Delay(2000);
    }
}
