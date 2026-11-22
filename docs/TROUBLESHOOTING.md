# Troubleshooting

## The audio is delayed

The Windows capture (20 ms), Opus (10 ms) and the adaptive phone buffer (about 40–100 ms) add roughly
100–150 ms. Most of the rest is Bluetooth: standard SBC/AAC headphones add about 150–250 ms, which no app can remove.

- The sender prints `Handy->Kopfhörer gesamt`, the time from the phone to the headphones. If that value is already high, the headphones or the codec are the limit.
- In the phone's developer options try another Bluetooth audio codec (SBC can be faster than AAC).
- `aptX Low Latency` or LE Audio headphones are much faster.
- If only video sync bothers you, give your video player a negative audio delay (for example `-300 ms` in VLC or mpv).
- The **minimum buffer** slider in the app trades delay against crackling. The buffer adapts itself, the slider only sets its lower limit.

## I hear my own voice

- Windows: *Sound settings → Recording → CABLE Output → Properties → Listen → "Listen to this device"* must be off. The sender warns when it is on.
- Do not set *CABLE Input* as the default playback device.
- On the phone choose **Handy-Mikro (eingebaut)** as source. The Bluetooth headset microphone switches the headphones to call mode, and many headsets then play your own voice back.

## The microphone does nothing

- Install [VB-Cable](https://vb-audio.com/Cable/) and restart Windows. The phone microphone is played into *CABLE Input*; apps must record from *CABLE Output*.
- Allow the microphone permission in the phone app before pressing *Start*.
- Use `--mic-always` to keep the microphone on without waiting for an app to use it.
