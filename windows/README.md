# Windows sender

Captures everything the PC plays (WASAPI loopback), encodes it with Opus and sends it to the phone app.
It can also play the phone microphone into a virtual cable so apps can use it as a microphone.

## Requirements

- Windows 10 or newer, .NET 8 SDK
- Packages: [NAudio](https://github.com/naudio/NAudio) 2.2.1 (MIT), [Concentus](https://github.com/lostromb/concentus) 2.2.2 (BSD-style licence)
- [VB-Cable](https://vb-audio.com/Cable/) is required for microphone bridging (not included); without it, the microphone bridge cannot work
- `adb` (Android platform-tools) and USB debugging on the phone

For the complete installation sequence and Windows sound-device setup, see [`../docs/INSTALLATION.md`](../docs/INSTALLATION.md).

## Run

```
adb forward tcp:5005 tcp:5005
dotnet run --project AudioSender
```

Windows should output at 48000 Hz / 2 channels; if it does not, the sender converts automatically.

| Option | Meaning |
|--------|---------|
| `--mic-device "CABLE Input"` | device the phone microphone is played into |
| `--mic-capture "CABLE Output"` | device apps record from; watched to see when the microphone is needed |
| `--mic-always` | keep the phone microphone on |
| `<port>` | TCP port, default 5005 |

## Structure

| File | Purpose |
|------|---------|
| `Program.cs` | start-up and reconnect loop |
| `Session.cs` | one phone connection: receive loop, microphone requests, capture |
| `LoopbackSender.cs` | loopback capture → Opus → phone |
| `MicBridge.cs` | phone microphone → virtual cable, detects apps using it |
| `PhoneConnection.cs`, `Protocol.cs` | framed TCP messages |
| `PhoneInfoPrinter.cs` | prints the phone's status |
| `Options.cs`, `Native.cs` | command line, timer resolution |
