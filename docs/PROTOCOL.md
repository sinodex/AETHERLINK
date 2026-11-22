# Wire protocol

The phone app listens on TCP port `5005`. The Windows sender connects to it through the USB cable with
`adb forward tcp:5005 tcp:5005`. All numbers are little-endian.

## Handshake (PC → phone, once)

| Bytes | Meaning |
|-------|---------|
| 4 | ASCII `PCB2` |
| 4 | sample rate (int32), currently 48000 |
| 1 | channel count, currently 2 |

## Messages (both directions)

Every message is `type (1 byte) | length (2 bytes) | payload`.

| Type | Direction | Payload |
|------|-----------|---------|
| 1 | PC → phone | Opus frame, 10 ms, 48 kHz, stereo (the PC audio) |
| 2 | phone → PC | Opus frame, 10 ms, 48 kHz, mono (the phone microphone) |
| 3 | phone → PC | JSON status, sent every 2 s: phone, battery, headphones, buffer statistics |
| 4 | PC → phone | 1 byte: `1` = start the microphone, `0` = stop it |

The microphone only records while the PC has asked for it (type 4), which the sender does when an app
is recording from the virtual microphone.
