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

/**
 * Gemini Live native-audio session.
 *
 * Firebase app configuration (google-services.json) must be added before this
 * can connect at runtime. This class deliberately does not use the legacy
 * REST Gemini key stored by SecureKeyStore.
 */
@OptIn(PublicPreviewAPI::class)
class GeminiLiveVoice(
    private val onStatus: (String) -> Unit = {}
) {
    private var session: LiveSession? = null

    suspend fun start(enableInterruptions: Boolean = true) {
        if (session?.isClosed() == false) {
            onStatus("Gemini Live session already connected")
            return
        }

        onStatus("Connecting to Gemini Live…")
        try {
            val instruction = content {
                text(
                    "You are JARVIS PRO, a fast and helpful Telugu-English voice assistant. " +
                    "Understand Telugu, English, and mixed Telugu-English. Reply naturally in " +
                    "the same language the user speaks. Keep spoken answers concise and direct. " +
                    "Do not use markdown, headings, or long introductions."
                )
            }

            val model = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
                modelName = "gemini-2.5-flash-native-audio-preview-12-2025",
                generationConfig = liveGenerationConfig {
                    responseModality = ResponseModality.AUDIO
                    speechConfig = SpeechConfig(voice = Voice("Puck"))
                },
                systemInstruction = instruction
            )

            val live = model.connect()
            session = live
            live.startAudioConversation(enableInterruptions = enableInterruptions)
            onStatus("Gemini Live voice conversation active")
        } catch (error: Exception) {
            Log.e("GeminiLiveVoice", "Live voice connection failed", error)
            onStatus("Gemini Live error: ${error.message ?: error.javaClass.simpleName}")
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
        onStatus("Gemini Live voice stopped")
    }
}
