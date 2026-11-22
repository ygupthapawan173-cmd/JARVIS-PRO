package com.thanish.jarvispro

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.vosk.Model
import org.vosk.Recognizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Offline wake-word recognizer. Uses the English Vosk model; Telugu wake words
 * require a separate Telugu acoustic model and are not claimed here.
 */
class WakeWordDetector(
    private val modelPath: String,
    private val onWakeWord: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var recorder: AudioRecord? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        worker = Thread({ listenLoop() }, "JarvisWakeWord").apply { start() }
    }

    private fun listenLoop() {
        var model: Model? = null
        var recognizer: Recognizer? = null
        try {
            model = Model(modelPath)
            recognizer = Recognizer(model, 16000.0f, "[\"hey jarvis\", \"jarvis\", \"[unk]\"]")
            val minBuffer = AudioRecord.getMinBufferSize(
                16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            require(minBuffer > 0) { "Microphone audio format is unsupported" }
            val audio = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer * 2, 8192)
            )
            recorder = audio
            require(audio.state == AudioRecord.STATE_INITIALIZED) { "Could not initialize microphone" }
            val buffer = ShortArray(4096)
            audio.startRecording()
            onStatus("Wake word listening • Say Hey Jarvis")
            while (running.get()) {
                val count = audio.read(buffer, 0, buffer.size)
                if (count > 0) {
                    val bytes = ByteArray(count * 2)
                    for (i in 0 until count) {
                        val sample = buffer[i].toInt()
                        bytes[i * 2] = (sample and 0xff).toByte()
                        bytes[i * 2 + 1] = ((sample shr 8) and 0xff).toByte()
                    }
                    if (recognizer.acceptWaveForm(bytes, bytes.size)) {
                        val result = recognizer.result
                        if (isWake(result)) onWakeWord(result)
                    } else {
                        val partial = recognizer.partialResult
                        if (isWake(partial)) onWakeWord(partial)
                    }
                }
            }
            audio.stop()
        } catch (e: Exception) {
            onStatus("Wake-word error: ${e.message ?: "unknown"}")
        } finally {
            running.set(false)
            try { recorder?.release() } catch (_: Exception) {}
            recorder = null
            try { recognizer?.close() } catch (_: Exception) {}
            try { model?.close() } catch (_: Exception) {}
        }
    }

    private fun isWake(json: String): Boolean {
        val value = json.lowercase(Locale.ROOT)
        return value.contains("hey jarvis") || Regex("\\bjarvis\\b").containsMatchIn(value)
    }

    fun stop() {
        running.set(false)
        try { recorder?.stop() } catch (_: Exception) {}
        worker?.interrupt()
        worker = null
    }
}
