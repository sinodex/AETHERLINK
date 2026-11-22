# Installation and setup

This guide describes the dependencies and the recommended first-time setup for AetherLink.

## 1. Download AetherLink

Download or clone the AetherLink repository. The project has two parts:

- `android/` contains the Android receiver app.
- `windows/` contains the Windows sender.

The Android phone must be connected to the PC by USB, and USB debugging must be enabled. Android platform-tools, including `adb`, are required.

## 2. Install the dependencies

The Windows sender requires Windows 10 or newer, the .NET 8 SDK, NAudio 2.2.1, and Concentus 2.2.2. The Android app requires Android 8.0 / API 26 or newer, Kotlin, Jetpack Compose Material 3, and `org.concentus:Concentus:1.0.2`.

The microphone bridge additionally requires [VB-Cable](https://vb-audio.com/Cable/). VB-Cable is not bundled with AetherLink. The main Windows-to-Android Bluetooth audio path does not require VB-Cable.

## 3. Install VB-Cable

1. Download VB-Cable from the [official VB-Audio website](https://vb-audio.com/Cable/).
2. Extract the downloaded archive.
3. Run the appropriate setup program as administrator. On 64-bit Windows, use the 64-bit installer when provided.
4. Follow the installer steps so that the VB-Audio virtual audio driver is installed.
5. Restart Windows after the driver installation.

## 4. Configure Windows sound devices

Open the Windows sound settings by right-clicking the speaker icon in the lower-right corner of the taskbar and opening **Sound settings** or **More sound settings**.

For the phone microphone bridge, AetherLink uses the following VB-Cable direction:

| Purpose | Windows device |
|---|---|
| AetherLink plays the phone microphone into the virtual cable | `CABLE Input (VB-Audio Virtual Cable)` — playback/output device |
| Desktop applications receive the phone microphone | `CABLE Output (VB-Audio Virtual Cable)` — recording/input device |

In the application that should use the phone microphone, select `CABLE Output (VB-Audio Virtual Cable)` as its microphone/input device. In AetherLink, the corresponding defaults are `CABLE Input` for `--mic-device` and `CABLE Output` for `--mic-capture`.

Some VB-Audio products expose additional variants, such as 16-channel or Hi-Fi Cable devices. Use the exact device created by the VB-Cable package you installed and select matching input/output devices; do not mix an unrelated cable variant with the standard `CABLE Input` / `CABLE Output` pair.

For normal Bluetooth audio forwarding, leave the Windows playback device configured as desired. AetherLink captures the Windows audio loopback and sends it to the Android phone, which then plays it through the Bluetooth headphones. VB-Cable is only needed for the microphone return path.

## 5. Start AetherLink

Connect the phone, enable USB debugging, and forward the TCP port:

```powershell
adb forward tcp:5005 tcp:5005
```

Start the Windows sender:

```powershell
dotnet run --project windows/AudioSender
```

Start the Android app, grant the requested microphone, Bluetooth, and notification permissions, and press **Start**. Then connect the Bluetooth headphones to the phone.

For microphone use, select the VB-Cable output device in the desktop application that should receive the phone microphone. See [`windows/README.md`](../windows/README.md) for the available microphone options.
