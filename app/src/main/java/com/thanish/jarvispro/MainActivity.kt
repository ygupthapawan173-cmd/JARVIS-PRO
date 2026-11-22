package com.thanish.jarvispro

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.graphics.Color
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView
    private var statusMessageForStartup: String? = null
    private lateinit var prompt: EditText
    private lateinit var output: TextView
    private lateinit var apiKey: EditText
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var ttsReady = false
    private var latestCode = ""
    private var pendingFiles: Map<String, String> = emptyMap()
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize the debug-only App Check provider before Firebase AI is used.
        try {
            Class.forName("com.thanish.jarvispro.DebugAppCheckInitializer")
                .getMethod("initialize", android.content.Context::class.java)
                .invoke(null, this)
        } catch (error: Exception) {
            android.util.Log.e("JARVIS-Firebase", "Debug App Check initialization failed", error)
            statusMessageForStartup = "Firebase debug setup: ${error.cause?.message ?: error.message}"
        }
        window.statusBarColor = Color.rgb(4, 8, 22)
        window.navigationBarColor = Color.rgb(4, 8, 22)
        tts = TextToSpeech(this, this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 24)
            setBackgroundColor(Color.rgb(4, 8, 22))
        }
        val scroll = ScrollView(this).apply { addView(layout) }
        val title = TextView(this).apply {
            text = "J.A.R.V.I.S PRO"
            textSize = 27f
            setTextColor(Color.rgb(0, 220, 255))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 18)
        }
        status = TextView(this).apply {
            text = statusMessageForStartup ?: "VOICE + AI SYSTEM STARTING"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 14)
        }
        apiKey = EditText(this).apply {
            hint = "Gemini API key (encrypted on this device)"
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            inputType = 129
        }
        val saveKey = Button(this).apply {
            text = "SAVE API KEY SECURELY"
            setOnClickListener {
                val key = apiKey.text.toString().trim()
                if (key.isBlank()) status.text = "Enter your Gemini API key first"
                else try {
                    SecureKeyStore.save(this@MainActivity, key)
                    apiKey.text.clear()
                    status.text = "API key saved encrypted on device"
                } catch (e: Exception) {
                    status.text = "Secure key save failed: ${e.message}"
                }
            }
        }
        prompt = EditText(this).apply {
            hint = "Ask JARVIS or describe code you want to create..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            minLines = 2
            gravity = Gravity.TOP
        }
        val ask = Button(this).apply {
            text = "ASK JARVIS AI"
            setOnClickListener { askAi(codeMode = false) }
        }
        val code = Button(this).apply {
            text = "SELF-CODING: GENERATE FILES"
            setOnClickListener { askAi(codeMode = true) }
        }
        val saveCode = Button(this).apply {
            text = "SAVE RAW AI OUTPUT"
            setOnClickListener {
                if (latestCode.isBlank()) status.text = "Generate code first"
                else try {
                    val file = CodeWorkspace.save(this@MainActivity, latestCode)
                    status.text = "Raw output saved: ${file.name}"
                } catch (e: Exception) {
                    status.text = "Workspace save failed: ${e.message}"
                }
            }
        }
        val approveFiles = Button(this).apply {
            text = "APPROVE & SAVE FILES TO STAGING"
            setOnClickListener {
                if (pendingFiles.isEmpty()) {
                    status.text = "Generate and validate files first"
                } else try {
                    val saved = CodeWorkspace.saveApprovedFiles(this@MainActivity, pendingFiles)
                    pendingFiles = emptyMap()
                    status.text = "Approved ${saved.size} file(s) in private staging folder"
                    output.text = "Saved for review only:\n" + saved.joinToString("\n") +
                        "\n\nNo project files were changed. Nothing was executed or built."
                } catch (e: Exception) {
                    status.text = "Staging save failed: ${e.message}"
                }
            }
        }
        output = TextView(this).apply {
            text = "AI response and generated files will appear here."
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(10, 18, 10, 18)
        }
        val mic = Button(this).apply {
            text = "🎙 SPEAK TO JARVIS"
            setOnClickListener { startListening() }
        }
        val test = Button(this).apply {
            text = "TEST TELUGU VOICE"
            setOnClickListener { speak("నమస్కారం! నేను జార్విస్. మీకు ఎలా సహాయం చేయగలను?") }
        }
        val commandHelp = Button(this).apply {
            text = "VOICE COMMANDS"
            setOnClickListener {
                output.text = "Try: Open YouTube, Open WhatsApp, Flashlight on/off, Volume up/down, Stop listening.\\nTap SPEAK TO JARVIS and say a command."
            }
        }
        val wakeStart = Button(this).apply {
            text = "START ALWAYS-ON WAKE WORD"
            setOnClickListener { requestWakePermissionsAndStart() }
        }
        val wakeStop = Button(this).apply {
            text = "STOP WAKE WORD LISTENER"
            setOnClickListener {
                startService(Intent(this@MainActivity, JarvisVoiceService::class.java).setAction(JarvisVoiceService.ACTION_STOP))
                status.text = "Wake-word listener stopped"
            }
        }
        val access = Button(this).apply {
            text = "ACCESSIBILITY SETTINGS"
            setOnClickListener { startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        listOf(title, status, apiKey, saveKey, prompt, ask, code, approveFiles, saveCode, output, mic, commandHelp, wakeStart, wakeStop, test, access)
            .forEach { layout.addView(it) }
        setContentView(scroll)
    }

    private fun askAi(codeMode: Boolean) {
        val request = prompt.text.toString().trim()
        if (request.isBlank()) {
            status.text = "Type your request first"
            return
        }
        val key = try { SecureKeyStore.load(this) } catch (_: Exception) { null }
        if (key.isNullOrBlank()) {
            status.text = "Save your Gemini API key first"
            return
        }
        status.text = "JARVIS AI THINKING..."
        if (codeMode) {
            pendingFiles = emptyMap()
            latestCode = ""
        }
        executor.execute {
            try {
                val result = GeminiApi.generate(key, request, codeMode)
                val parsed = if (codeMode) CodeWorkspace.parseFiles(result) else emptyMap()
                runOnUiThread {
                    latestCode = if (codeMode) result else ""
                    pendingFiles = parsed
                    output.text = if (codeMode) {
                        if (parsed.isEmpty()) {
                            "AI response received, but no valid file bundle was detected.\n\n$result"
                        } else {
                            "Files ready for approval:\n" + parsed.keys.joinToString("\n") +
                                "\n\nPreview of first file:\n" + parsed.values.first().take(5000)
                        }
                    } else result
                    status.text = if (codeMode) {
                        if (parsed.isEmpty()) "CODE RECEIVED • FILE FORMAT INVALID" else " ${parsed.size} FILE(S) READY • APPROVAL REQUIRED"
                    } else "AI RESPONSE READY"
                    if (!codeMode) speak(result)
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "AI error: ${e.message ?: "Request failed"}" }
            }
        }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            val languageResult = tts?.setLanguage(Locale("te", "IN"))
            ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                    languageResult != TextToSpeech.LANG_NOT_SUPPORTED
            status.text = if (ttsReady) "JARVIS READY • Telugu TTS available" else "JARVIS READY • Install Telugu TTS voice"
        } else status.text = "Text-to-Speech initialization failed"
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "JARVIS_TTS")
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Speech recognition service unavailable"
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "LISTENING... మాట్లాడండి" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "PROCESSING..." }
            override fun onError(error: Int) { status.text = "Voice error: $error • Try again" }
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?: "మాటలు గుర్తించబడలేదు"
                prompt.setText(spoken)
                status.text = "VOICE INPUT RECEIVED"
                if (!handleVoiceCommand(spoken)) askAi(codeMode = false)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "te-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "te-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer?.startListening(intent)
    }


    private fun handleVoiceCommand(raw: String): Boolean {
        val command = raw.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        fun reply(message: String) { status.text = message; speak(message) }

        if (command.contains("stop listening") || command.contains("stop jarvis")) {
            recognizer?.cancel()
            reply("Listening stopped")
            return true
        }
        if (command.contains("flashlight on") || command.contains("torch on") || command.contains("ఫ్లాష్ లైట్ ఆన్") || command.contains("టార్చ్ ఆన్")) return setFlashlight(true, ::reply)
        if (command.contains("flashlight off") || command.contains("torch off") || command.contains("ఫ్లాష్ లైట్ ఆఫ్") || command.contains("టార్చ్ ఆఫ్")) return setFlashlight(false, ::reply)

        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        if (command.contains("volume up") || command.contains("increase volume") || command.contains("వాల్యూమ్ పెంచు")) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            reply("Volume increased"); return true
        }
        if (command.contains("volume down") || command.contains("decrease volume") || command.contains("వాల్యూమ్ తగ్గించు")) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            reply("Volume decreased"); return true
        }

        val app = when {
            command.contains("open youtube") || command == "youtube" -> "com.google.android.youtube"
            command.contains("open whatsapp") || command == "whatsapp" -> "com.whatsapp"
            command.contains("open instagram") || command == "instagram" -> "com.instagram.android"
            command.contains("open chrome") || command == "chrome" -> "com.android.chrome"
            command.contains("open settings") -> "com.android.settings"
            else -> null
        }
        if (app != null) {
            val launch = packageManager.getLaunchIntentForPackage(app)
            if (launch == null) reply("That app is not installed") else {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
                reply("Opening " + app.substringAfterLast("."))
            }
            return true
        }
        return false
    }

    private fun setFlashlight(enabled: Boolean, reply: (String) -> Unit): Boolean {
        return try {
            val camera = getSystemService(CAMERA_SERVICE) as CameraManager
            val cameraId = camera.cameraIdList.firstOrNull { id ->
                camera.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId == null) reply("Flashlight is not available") else {
                camera.setTorchMode(cameraId, enabled)
                reply(if (enabled) "Flashlight on" else "Flashlight off")
            }
            true
        } catch (e: Exception) {
            reply("Flashlight control failed")
            true
        }
    }

    private fun requestWakePermissionsAndStart() {
        val needed = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), 101)
        } else startWakeService()
    }

    private fun startWakeService() {
        try {
            val service = Intent(this, JarvisVoiceService::class.java).setAction(JarvisVoiceService.ACTION_START)
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
            status.text = "Starting wake-word listener… keep JARVIS open until notification appears"
        } catch (e: Exception) {
            status.text = "Could not start wake-word service: ${e.message}"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startWakeService()
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        executor.shutdownNow()
        super.onDestroy()
    }
}
