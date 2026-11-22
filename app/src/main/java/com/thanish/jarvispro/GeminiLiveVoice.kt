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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(PublicPreviewAPI::class)
class GeminiLiveVoice(
    private val onStatus: (String) -> Unit = {}
) {
    private var session: LiveSession? = null
    private var connecting = false

    private fun createModel() = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
        modelName = "gemini-2.5-flash-native-audio-preview-12-2025",
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
        var current: Throwable? = error
        var depth = 0
        while (current?.cause != null && depth < 4) {
            current = current.cause
            depth++
        }
        val type = current?.javaClass?.simpleName ?: error.javaClass.simpleName
        val message = current?.message ?: error.message ?: "No error details"
        return "$type: $message".replace(Regex("\\s+"), " ").take(220)
    }

    /** Preconnect before the wake phrase so the user does not wait for a new socket. */
    suspend fun prepare() {
        if (session?.isClosed() == false || connecting) return
        connecting = true
        try {
            onStatus("Preparing Gemini Live connection…")
            session = createModel().connect()
            onStatus("Gemini Live ready • Say Hey Jarvis")
        } catch (error: Exception) {
            Log.e("GeminiLiveVoice", "Live preconnect failed", error)
            onStatus("Live setup failed • ${errorDetails(error)}")
        } finally {
            connecting = false
        }
    }

    suspend fun start(enableInterruptions: Boolean = true) {
        var live = session
        if (live == null || live.isClosed()) {
            onStatus("Connecting to Gemini Live…")
            live = createModel().connect()
            session = live
        }
        try {
            live.startAudioConversation(enableInterruptions = enableInterruptions)
            onStatus("Gemini Live voice conversation active")
        } catch (error: Exception) {
            Log.e("GeminiLiveVoice", "Live voice start failed", error)
            try { live.close() } catch (_: Exception) {}
            session = null
            onStatus("Gemini Live error • ${errorDetails(error)}")
            throw error
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
