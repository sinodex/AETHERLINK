package com.aetherlink

/** Values the user can change in the UI; read by the streaming service at any time. */
object Settings {
    const val MIC_BUILTIN = 0
    const val MIC_BLUETOOTH = -2

    /** Master switch for the phone microphone. It still only records while the PC asks for it. */
    @Volatile var micEnabled = true

    /** [MIC_BUILTIN], [MIC_BLUETOOTH] or the id of a wired/USB AudioDeviceInfo. */
    @Volatile var micSource = MIC_BUILTIN

    /** Lower limit of the playback buffer in ms: smaller means less delay but more risk of crackling. */
    @Volatile var minBufferMs = 40
}
