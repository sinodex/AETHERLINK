package com.aetherlink

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings as AndroidSettings
import org.json.JSONObject
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Foreground service that waits for the PC, plays the PC audio on the phone (and so on the
 * Bluetooth headphones), optionally streams the phone microphone back and reports status.
 */
class ReceiverService : Service() {
    @Volatile private var running = false
    private var server: ServerSocket? = null
    private val headphones by lazy { HeadphoneInfo(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        headphones.start()
        if (!running) {
            running = true
            thread(name = "accept") { acceptLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        server?.close()
        headphones.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val channel = NotificationChannel("bridge", "Audio Bridge", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification = Notification.Builder(this, "bridge")
            .setContentTitle("Audio Bridge aktiv")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            // The microphone service type may only be requested once the permission has been granted.
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            val micGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (micGranted) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(1, notification, type)
        } else {
            startForeground(1, notification)
        }
    }

    private fun acceptLoop() {
        server = ServerSocket(Protocol.PORT)
        while (running) {
            try {
                server!!.accept().use { handleSession(it) }
            } catch (_: Exception) {
                // connection lost or refused: wait for the next one
            }
        }
    }

    /** One PC connection from handshake until the PC disconnects. */
    private fun handleSession(socket: Socket) {
        socket.tcpNoDelay = true
        val input = DataInputStream(socket.getInputStream().buffered(4096))
        val output = socket.getOutputStream()

        // Several threads send (status, microphone), so writes are serialized.
        val sendLock = Any()
        fun send(type: Int, data: ByteArray, length: Int = data.size) = synchronized(sendLock) {
            output.write(byteArrayOf(type.toByte(), length.toByte(), (length shr 8).toByte()))
            output.write(data, 0, length)
            output.flush()
        }

        // Handshake: "PCB2", sample rate (int32, little endian), channel count.
        val magic = ByteArray(4).also { input.readFully(it) }
        require(String(magic) == "PCB2") { "unknown protocol" }
        val header = ByteArray(5).also { input.readFully(it) }
        val rate = (header[0].toInt() and 255) or ((header[1].toInt() and 255) shl 8) or
                ((header[2].toInt() and 255) shl 16) or ((header[3].toInt() and 255) shl 24)
        val channels = header[4].toInt()

        val stats = StreamStats()
        // Any component that fails ends the whole session by closing the socket.
        val sessionAlive = AtomicBoolean(true)
        val endSession = { sessionAlive.set(false); try { socket.close() } catch (_: Exception) { } }

        val playback = PlaybackEngine(rate, channels, stats, endSession)
        val mic = MicStreamer(this, ::send, endSession)
        playback.start()
        mic.start()

        // Status report every 2 seconds.
        val statusThread = thread(name = "status") {
            try {
                while (sessionAlive.get()) {
                    send(Protocol.INFO, statusJson(stats, mic.requested))
                    Thread.sleep(2000)
                }
            } catch (_: Exception) {
                endSession()
            }
        }

        // Main receive loop: audio goes to the player, control messages switch the microphone.
        val payload = ByteArray(1500)
        try {
            while (running) {
                val type = input.readUnsignedByte()
                val length = input.readUnsignedByte() or (input.readUnsignedByte() shl 8)
                input.readFully(payload, 0, length)
                when (type) {
                    Protocol.AUDIO -> playback.offer(payload.copyOf(length))
                    Protocol.MIC_CONTROL -> if (length >= 1) mic.requested = payload[0].toInt() == 1
                }
            }
        } finally {
            sessionAlive.set(false)
            statusThread.interrupt()
            playback.stop()
            mic.stop()
        }
    }

    private fun statusJson(stats: StreamStats, micActive: Boolean): ByteArray {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("name", AndroidSettings.Global.getString(contentResolver, "device_name") ?: Build.MODEL)
            .put("android", Build.VERSION.RELEASE)
            .put("battery", if (level >= 0) level * 100 / scale else -1)
            .put("charging", charging)
            .put("bt", headphones.toJson() ?: JSONObject.NULL)
            .put("mic", Settings.micEnabled && micActive)
            .put("buf_ms", stats.bufferMs)
            .put("underruns", stats.underruns)
            .put("dropped", stats.dropped)
            .put("out_ms", stats.outputMs)
            .toString().toByteArray()
    }
}
