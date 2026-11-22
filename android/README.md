# Android app

Receives the PC audio over USB (adb forward) and plays it on the phone, so it reaches the paired
Bluetooth headphones. It can also send the phone microphone back to the PC and reports battery,
headphone and buffer information.

## Requirements

- Android 8.0 (API 26) or newer, Kotlin, Jetpack Compose (Material 3)
- Opus decoder/encoder: `org.concentus:Concentus` (pure Java)

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("org.concentus:Concentus:1.0.2")
}
```

The sources use the package `com.aetherlink`; use the same namespace in Gradle or adjust the package lines.

## Structure

| File | Purpose |
|------|---------|
| `ReceiverService.kt` | foreground service: accepts the PC, receives frames, reports status |
| `PlaybackEngine.kt` | Opus decoding and low-latency playback with an adaptive buffer |
| `MicStreamer.kt` | phone microphone → PC, only while the PC asks for it |
| `HeadphoneInfo.kt` | name, MAC, battery and profiles of the connected Bluetooth headphones |
| `Protocol.kt`, `Settings.kt` | protocol constants and user settings |
| `MainActivity.kt` | UI: start/stop, microphone switch and source, minimum buffer |

Protocol: [`docs/PROTOCOL.md`](../docs/PROTOCOL.md).
Known limitations and required dependencies: [`RELEASE_NOTES.md`](../RELEASE_NOTES.md).

## Run

1. Open the app, allow microphone, Bluetooth and notification permissions, press **Start**.
2. Connect the headphones to the phone.
3. On the PC run the Windows sender (see `windows/README.md`).
