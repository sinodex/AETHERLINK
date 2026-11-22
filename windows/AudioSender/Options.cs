namespace AudioBridge;

/// <summary>Command-line options of the Windows sender.</summary>
sealed record Options(int Port, string MicOutputName, string MicCaptureName, bool MicAlways)
{
    public static Options Parse(string[] args)
    {
        int port = 5005;
        string micOutput = "CABLE Input";    // the phone microphone is played into this device
        string micCapture = "CABLE Output";  // ...and apps record it from this one
        bool micAlways = false;

        for (int i = 0; i < args.Length; i++)
        {
            switch (args[i])
            {
                case "--mic-device" when i + 1 < args.Length: micOutput = args[++i]; break;
                case "--mic-capture" when i + 1 < args.Length: micCapture = args[++i]; break;
                case "--mic-always": micAlways = true; break;
                default: if (int.TryParse(args[i], out var p)) port = p; break;
            }
        }
        return new Options(port, micOutput, micCapture, micAlways);
    }
}
