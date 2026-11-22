# AetherLink

AetherLink is an early-stage Bluetooth audio bridge. It forwards Windows system audio over USB to an Android phone, which plays it through connected Bluetooth headphones. It can also return the phone microphone to Windows for use by desktop applications. Support for Linux devices may be added in the future; it is not part of the current implementation.

## Project status

This repository contains the Android source and the Windows sender source. The Android build scaffolding (Gradle wrapper and module build files) is not included in the current source package, so the Android package/namespace must remain `com.aetherlink` until that configuration is added or updated consistently.

## Repository layout

- `android/` — Android receiver app and microphone streamer
- `windows/` — .NET 8 Windows sender
- `docs/` — protocol and troubleshooting documentation

## Requirements

### Android

- Android 8.0 / API 26 or newer
- Kotlin and Jetpack Compose Material 3
- `org.concentus:Concentus:1.0.2`
- USB debugging and Android platform-tools (`adb`)

### Windows

- Windows 10 or newer
- .NET 8 SDK
- NAudio 2.2.1 and Concentus 2.2.2
- [VB-Cable](https://vb-audio.com/Cable/) is required for microphone bridging; it is not included

For installation, dependencies, VB-Cable setup, Windows sound-device selection, and microphone routing, see [`docs/INSTALLATION.md`](docs/INSTALLATION.md). Additional details are available in [`windows/README.md`](windows/README.md), [`android/README.md`](android/README.md), [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md), and [`RELEASE_NOTES.md`](RELEASE_NOTES.md).

## Package name

The Android Kotlin sources and directory structure use `com.aetherlink`. When the missing Android Gradle files are added, set the Gradle namespace and application ID to `com.aetherlink` as well.

## AI Usage

The first prototypes of the Android and Windows apps were written by the project creator without AI.
The current audio-streaming code (Android app and Windows) was written with the help of AI
and then tested and adjusted by the project creator.

AI tools were also used for debugging, reviewing the project and build structure, organizing files
and improving the readability of the documentation.

All final project decisions, code changes and development direction remain the responsibility of
the project creator.

## License

No license file is included yet. Until a license is added, the source should be treated as all rights reserved by the copyright holder.
