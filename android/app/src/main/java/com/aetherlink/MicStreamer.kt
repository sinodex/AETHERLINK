package com.aetherlink

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import org.concentus.OpusApplication
import org.concentus.OpusEncoder
import kotlin.concurrent.thread

/**
 * Records the phone microphone and sends it to the PC as Opus frames.
 * It only records while [requested] is true (the PC sets it when an app uses the virtual microphone).
 */
class MicStreamer(
    private val context: Context,
    private val send: (type: Int, data: ByteArray, length: Int) -> Unit,
    private val onFailure: () -> Unit
) {
    companion object {
        private const val RATE = 48000
        private const val FRAME = 480  // 10 ms
    }

    @Volatile var requested = false
    @Volatile private var running = false
    @Volatile private var communicationModeChanged = false
    private var worker: Thread? = null

    fun start() {
        running = true
        worker = thread(name = "mic") { run() }
    }

    fun stop() {
        running = false
        requested = false
        worker?.interrupt()
        try { worker?.join(1500) } catch (_: InterruptedException) { }
        if (communicationModeChanged) resetCommunicationMode()
    }

    private fun hasPermission() =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun run() {
        val audioManager = context.getSystemService(AudioManager::class.java)
        var recorder: AudioRecord? = null
        var activeSource = Int.MIN_VALUE
        val encoder = OpusEncoder(RATE, 1, OpusApplication.OPUS_APPLICATION_VOIP).apply { bitrate = 32000 }
        val pcm = ShortArray(FRAME)
        val packet = ByteArray(400)

        fun stopRecording() {
            // stop() and release() are separate so a failing stop() can never leave the microphone open.
            try { recorder?.stop() } catch (_: Exception) { }
            try { recorder?.release() } catch (_: Exception) { }
            recorder = null
            if (communicationModeChanged) resetCommunicationMode()
        }

        fun startRecording(source: Int) {
            if (source == Settings.MIC_BLUETOOTH) useBluetoothHeadsetMic(audioManager)
            val r = createRecorder()
            if (source > 0) {
                audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                    .firstOrNull { it.id == source }?.let { r.preferredDevice = it }
            }
            r.startRecording()
            recorder = r
            activeSource = source
        }

        try {
            while (running) {
                if (!(Settings.micEnabled && requested && hasPermission())) {
                    stopRecording()
                    Thread.sleep(100)
                    continue
                }

                // (Re)start when the microphone was off or the user picked another source.
                val source = Settings.micSource
                if (recorder != null && activeSource != source) stopRecording()
                if (recorder == null) startRecording(source)
                val r = recorder ?: continue

                var got = 0
                while (got < FRAME && running && requested) {
                    val n = r.read(pcm, got, FRAME - got)
                    if (n <= 0) break
                    got += n
                }
                if (got == FRAME) {
                    val length = encoder.encode(pcm, 0, FRAME, packet, 0, packet.size)
                    send(Protocol.MIC, packet, length)
                } else {
                    Thread.sleep(10)
                }
            }
        } catch (_: InterruptedException) {
            // normal shutdown
        } catch (_: Exception) {
            onFailure()
        } finally {
            stopRecording()
        }
    }

    @Suppress("MissingPermission")
    private fun createRecorder(): AudioRecord {
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 3840))
    }

    /** Routes recording to the Bluetooth headset. Android then switches the headset to call mode (lower audio quality). */
    @Suppress("DEPRECATION")
    private fun useBluetoothHeadsetMic(audioManager: AudioManager) {
        communicationModeChanged = true  // set first so cleanup still happens if something below fails
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= 31) {
            audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }?.let { audioManager.setCommunicationDevice(it) }
        } else {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        }
    }

    /** Leaves call mode so the headset stops playing back the user's own voice. */
    @Suppress("DEPRECATION")
    private fun resetCommunicationMode() {
        val audioManager = context.getSystemService(AudioManager::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 31) audioManager.clearCommunicationDevice()
            else { audioManager.stopBluetoothSco(); audioManager.isBluetoothScoOn = false }
        } catch (_: Exception) { }
        try { audioManager.mode = AudioManager.MODE_NORMAL } catch (_: Exception) { }
        communicationModeChanged = false
    }
}
