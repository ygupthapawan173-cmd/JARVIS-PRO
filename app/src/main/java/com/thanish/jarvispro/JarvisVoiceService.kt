package com.thanish.jarvispro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class JarvisVoiceService : Service(), TextToSpeech.OnInitListener {
    private var detector: WakeWordDetector? = null
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val handlingWake = AtomicBoolean(false)
    private val channelId = "jarvis_voice_listener"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val liveVoice = GeminiLiveVoice(::updateNotification)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("te", "IN"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ttsReady) tts?.setLanguage(Locale.getDefault())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListener()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startListener()
        }
        return START_STICKY
    }

    private fun startListener() {
        isStopping = false
        val notification = buildNotification("Preparing offline wake-word model…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Thread {
            try {
                val model = VoskModelManager.ensureModel(this) { message -> updateNotification(message) }
                handler.post { startWakeDetector(model.absolutePath) }
            } catch (e: Exception) {
                updateNotification("Wake-word setup failed: ${e.message ?: "unknown error"}")
                stopSelf()
            }
        }.start()
    }

    private fun startWakeDetector(modelPath: String) {
        if (isStopping || handlingWake.get()) return
        recognizer?.destroy()
        recognizer = null
        detector?.stop()
        detector = WakeWordDetector(modelPath, { phrase ->
            if (handlingWake.compareAndSet(false, true)) {
                handler.post {
                    updateNotification("Hey Jarvis detected • Listening for your command…")
                    val wakeDetector = detector
                    detector = null
                    serviceScope.launch {
                        // AudioRecord must be fully released before Gemini opens the mic.
                        val microphoneReleased = kotlinx.coroutines.withContext(Dispatchers.IO) {
                            wakeDetector?.stopAndWait(5000) ?: true
                        }
                        if (isStopping) return@launch
                        if (!microphoneReleased) {
                            updateNotification("Microphone is still busy • Switching to speech fallback")
                            startCommandRecognition(modelPath)
                            return@launch
                        }
                        try {
                            liveVoice.start(enableInterruptions = true)
                        } catch (_: Exception) {
                            // Keep the existing speech-to-text + REST path as a fallback.
                            startCommandRecognition(modelPath)
                        }
                    }
                }
            }
        }, ::updateNotification)
        detector?.start()
        // Establish the network session before the user says the wake phrase.
        serviceScope.launch { liveVoice.prepare() }
    }

    private fun startCommandRecognition(modelPath: String) {
        if (isStopping) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("Speech recognition unavailable • Check Google speech services")
            resumeWake(modelPath)
            return
        }
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    updateNotification("JARVIS listening • Speak now")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { updateNotification("JARVIS processing your request…") }
                override fun onError(error: Int) {
                    updateNotification("Could not hear command (error $error) • Say Hey Jarvis again")
                    recognizer?.destroy()
                    recognizer = null
                    resumeWake(modelPath)
                }
                override fun onResults(results: Bundle?) {
                    val command = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                    recognizer?.destroy()
                    recognizer = null
                    if (command.isBlank()) {
                        updateNotification("No command heard • Say Hey Jarvis again")
                        resumeWake(modelPath)
                    } else answerCommand(command, modelPath)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "te-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "te-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "JARVIS is listening")
            }
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            updateNotification("Voice recognition failed: ${e.message ?: "unknown"}")
            resumeWake(modelPath)
        }
    }

    private fun answerCommand(command: String, modelPath: String) {
        updateNotification("JARVIS AI is thinking…")
        executor.execute {
            try {
                val key = SecureKeyStore.load(this)
                    ?: throw IllegalStateException("Gemini API key missing. Open JARVIS and save the key.")
                val response = GeminiApi.generate(key, command, false)
                handler.post { speakResponse(response, modelPath) }
            } catch (e: Exception) {
                val message = e.message ?: "AI request failed"
                handler.post { speakResponse("క్షమించండి. $message", modelPath) }
            }
        }
    }

    private fun speakResponse(message: String, modelPath: String) {
        if (isStopping) return
        updateNotification("JARVIS replying…")
        if (tts == null || !ttsReady) {
            updateNotification("Reply ready, but Telugu Text-to-Speech is unavailable")
            resumeWake(modelPath)
            return
        }
        val utteranceId = "jarvis_${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) handler.post { resumeWake(modelPath) }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) handler.post { resumeWake(modelPath) }
            }
        })
        tts?.speak(message.take(3000), TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun resumeWake(modelPath: String) {
        if (isStopping) return
        handlingWake.set(false)
        handler.postDelayed({ startWakeDetector(modelPath) }, 700)
        updateNotification("Ready • Say Hey Jarvis")
    }

    @Volatile private var isStopping = false

    private fun stopListener() {
        isStopping = true
        handlingWake.set(false)
        handler.removeCallbacksAndMessages(null)
        detector?.stop()
        detector = null
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        // Stop the Live session without posting a new notification after the
        // foreground notification has been removed. The old behavior recreated
        // a stale "Gemini Live voice stopped" notification during service shutdown.
        serviceScope.launch { liveVoice.stop() }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createChannel() {
        val channel = NotificationChannel(channelId, "JARVIS voice assistant", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(message: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("JARVIS PRO • Voice assistant")
            .setContentText(message)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    override fun onDestroy() {
        stopListener()
        tts?.shutdown()
        executor.shutdownNow()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.thanish.jarvispro.action.START_WAKE"
        const val ACTION_STOP = "com.thanish.jarvispro.action.STOP_WAKE"
        private const val NOTIFICATION_ID = 731
    }
}
