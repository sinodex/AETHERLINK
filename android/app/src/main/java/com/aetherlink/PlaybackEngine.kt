package com.aetherlink

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import org.concentus.OpusDecoder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Live numbers that are reported to the PC for diagnostics. */
class StreamStats {
    @Volatile var bufferMs = 0
    @Volatile var underruns = 0
    @Volatile var dropped = 0
    @Volatile var outputMs = -1

    fun reset() {
        bufferMs = 0; underruns = 0; dropped = 0; outputMs = -1
    }
}

/**
 * Decodes the Opus frames from the PC and plays them. The AudioTrack buffer is kept as small as
 * possible and adapts itself: it grows after an underrun and shrinks again after a calm period.
 */
class PlaybackEngine(
    private val rate: Int,
    private val channels: Int,
    private val stats: StreamStats,
    private val onFailure: () -> Unit
) {
    private val queue = ArrayBlockingQueue<ByteArray>(100)
    @Volatile private var running = false
    private var worker: Thread? = null

    fun start() {
        running = true
        worker = thread(name = "player") { run() }
    }

    /** Queues one Opus frame; when the queue is full the oldest frame is dropped. */
    fun offer(frame: ByteArray) {
        if (!queue.offer(frame)) {
            queue.poll()
            queue.offer(frame)
            stats.dropped++
        }
    }

    fun stop() {
        running = false
        worker?.interrupt()
        try { worker?.join(500) } catch (_: InterruptedException) { }
    }

    private fun run() {
        var track: AudioTrack? = null
        try {
            val decoder = OpusDecoder(rate, channels)
            val mask = if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
            val minBuf = AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT)
            val framesPerMs = rate / 1000
            val frameSize = rate / 100  // 10 ms per Opus frame

            // Large capacity (500 ms of 16-bit PCM) but only a small part of it is used, see setBufferSizeInFrames below.
            val capacity = maxOf(minBuf * 2, rate * channels)
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate)
                    .setChannelMask(mask).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setBufferSizeInBytes(capacity).build()
            track = t

            var target = maxOf(Settings.minBufferMs, 40)
            t.setBufferSizeInFrames(target * framesPerMs)
            stats.bufferMs = target
            t.play()

            val pcm = ShortArray(frameSize * channels)
            val silence = ShortArray(frameSize * channels)
            val timestamp = AudioTimestamp()
            var framesWritten = 0L
            var lastMeasure = 0L
            var prevUnderruns = -1
            var lastGood = System.nanoTime()
            var lastFrame = System.nanoTime()
            var idle = true

            while (running) {
                val frame = queue.poll(20, TimeUnit.MILLISECONDS)
                val now = System.nanoTime()

                // No data from the PC: after a short pause keep writing silence for a while so the
                // Bluetooth stream does not go to sleep (waking it up again costs noticeable delay).
                if (frame == null) {
                    val idleMs = (now - lastFrame) / 1_000_000
                    if (idleMs >= 200) idle = true
                    if (idleMs in 200..30_000) {
                        val w = t.write(silence, 0, silence.size)
                        if (w > 0) framesWritten += w / channels
                    }
                    continue
                }
                lastFrame = now
                if (idle) { idle = false; prevUnderruns = t.underrunCount }

                // Decode every frame, even those we skip, so the decoder state stays valid.
                val samples = try { decoder.decode(frame, 0, frame.size, pcm, 0, frameSize, false) } catch (_: Exception) { continue }
                if (queue.size > 6) { stats.dropped++; continue }  // too far behind: catch up by skipping

                val written = t.write(pcm, 0, samples * channels)
                if (written > 0) framesWritten += written / channels

                // Estimate how long a frame written now takes until it is audible (buffer + path to the headphones).
                if (now - lastMeasure > 1_000_000_000L && t.getTimestamp(timestamp)) {
                    lastMeasure = now
                    val pendingSec = (framesWritten - timestamp.framePosition).toDouble() / rate -
                            (System.nanoTime() - timestamp.nanoTime) / 1e9
                    stats.outputMs = (pendingSec * 1000).toInt().coerceAtLeast(0)
                }

                // Adapt the buffer: +20 ms after an underrun, -10 ms after 3 s without one (never below the user's minimum).
                val floor = Settings.minBufferMs.coerceIn(10, 300)
                val underruns = t.underrunCount
                if (prevUnderruns < 0) prevUnderruns = underruns
                if (underruns > prevUnderruns) {
                    prevUnderruns = underruns
                    target = minOf(500, target + 20)
                    lastGood = now
                    t.setBufferSizeInFrames(target * framesPerMs)
                } else if (target < floor) {
                    target = floor
                    t.setBufferSizeInFrames(target * framesPerMs)
                } else if (now - lastGood > 3_000_000_000L && target > floor) {
                    target = maxOf(floor, target - 10)
                    lastGood = now
                    t.setBufferSizeInFrames(target * framesPerMs)
                }
                stats.bufferMs = target
                stats.underruns = underruns
            }
        } catch (_: InterruptedException) {
            // normal shutdown
        } catch (_: Exception) {
            onFailure()
        } finally {
            try { track?.stop(); track?.release() } catch (_: Exception) { }
        }
    }
}
