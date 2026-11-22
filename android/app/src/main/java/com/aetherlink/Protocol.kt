package com.aetherlink

/** Constants of the wire protocol shared with the Windows sender (see docs/PROTOCOL.md). */
object Protocol {
    const val PORT = 5005

    // Message types
    const val AUDIO = 1        // PC -> phone: Opus frame of the PC audio
    const val MIC = 2          // phone -> PC: Opus frame of the phone microphone
    const val INFO = 3         // phone -> PC: JSON with device, headphone and buffer status
    const val MIC_CONTROL = 4  // PC -> phone: 1 = start microphone, 0 = stop
}
