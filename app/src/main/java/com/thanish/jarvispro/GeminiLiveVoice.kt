package com.thanish.jarvispro

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@OptIn(PublicPreviewAPI::class)
class GeminiLiveVoice(
    private val onStatus: (String) -> Unit = {}
) {
    private var session: LiveSession? = null
    private var starting = false

    private fun createModel() = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
        // Current Gemini Developer API Live model; 3.1 replaces the older 2.5 preview.
        modelName = "gemini-3.1-flash-live-preview",
        generationConfig = liveGenerationConfig {
            responseModality = ResponseModality.AUDIO
            speechConfig = SpeechConfig(voice = Voice("Puck"))
        },
        systemInstruction = content {
            text(
                "You are JARVIS PRO, a fast and helpful Telugu-English voice assistant. " +
                    "Understand Telugu, English, and mixed Telugu-English. Reply naturally in " +
                    "the same language the user speaks. Keep spoken answers concise and direct. " +
                    "Do not use markdown, headings, or long introductions."
            )
        }
    )

    private fun errorDetails(error: Throwable): String {
        val chain = mutableListOf<String>()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 5) {
            val message = current.message?.replace(Regex("\\s+"), " ")?.take(180)
            chain += (current.javaClass.simpleName + if (message.isNullOrBlank()) "" else ": $message")
            current = current.cause
            depth++
        }
        return chain.joinToString(" <- ").take(420)
    }

    /**
     * Do not keep an idle WebSocket open while Vosk waits for the wake phrase.
     * The Live socket is created only after "Hey Jarvis" is detected.
     */
    suspend fun prepare() {
        onStatus("Ready • Gemini Live connects after wake word")
    }

    suspend fun start(enableInterruptions: Boolean = true) {
        if (starting) return
        starting = true
        try {
            onStatus("Connecting to Gemini Live…")
            var live: LiveSession? = null
            var lastError: Exception? = null

            // A short retry handles transient network/WebSocket startup failures.
            for (attempt in 0..1) {
                try {
                    live = createModel().connect()
                    lastError = null
                    break
                } catch (error: Exception) {
                    lastError = error
                    Log.e("GeminiLiveVoice", "Live connect attempt ${attempt + 1} failed", error)
                    if (attempt == 0) delay(800)
                }
            }
            if (live == null) throw (lastError ?: IllegalStateException("Live connection failed"))

            session = live
            live!!.startAudioConversation(enableInterruptions = enableInterruptions)
            onStatus("Gemini Live voice conversation active")
        } catch (error: Exception) {
            Log.e("GeminiLiveVoice", "Live voice start failed", error)
            try { session?.close() } catch (_: Exception) {}
            session = null
            onStatus("Gemini Live failed • ${errorDetails(error)}")
            throw error
        } finally {
            starting = false
        }
    }

    suspend fun stop() {
        val live = session
        session = null
        if (live != null) {
            withContext(Dispatchers.IO) {
                try {
                    live.stopAudioConversation()
                    live.close()
                } catch (error: Exception) {
                    Log.w("GeminiLiveVoice", "Error closing Live session", error)
                }
            }
        }
    }
}
