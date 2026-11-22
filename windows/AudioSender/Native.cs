using System.Runtime.InteropServices;

namespace AudioBridge;

static class Native
{
    /// <summary>Raises the timer resolution; without it Windows timers tick only every ~15 ms.</summary>
    [DllImport("winmm.dll")]
    public static extern uint timeBeginPeriod(uint milliseconds);
}
