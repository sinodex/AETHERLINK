Release notes

v0.0.0.1 — Code update / placeholder pre-release

This is a small placeholder release for sharing the current code and documentation updates. It is intentionally numbered v0.0.0.1 as a temporary, informal pre-release version.

This release is not a finished application build. A tested Android/Windows build and the complete source-code release will be provided later. Until then, this package should be treated as an interim code snapshot for updates and review, not as a production-ready release.

Current release

Known issues

Audio playback may have an approximate delay of half a second to one second. The phone microphone path can also be delayed by a similar amount. If you find a reliable fix or improvement for this latency, contributions are welcome.

There are no other known issues at this time.

Required dependency

The Windows microphone bridge requires 

VB-Cable. Install VB-Cable separately before using the microphone features; it is not bundled with AetherLink. Without VB-Cable, the microphone bridge cannot work. VB-Cable is not required for the main Bluetooth audio playback path from Windows to Android.

The remaining runtime and build dependencies are documented in README.md and windows/README.md.

