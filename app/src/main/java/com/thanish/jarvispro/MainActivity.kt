package com.thanish.jarvispro

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.graphics.Color
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var status: TextView
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(4, 8, 22)
        window.navigationBarColor = Color.rgb(4, 8, 22)

        tts = TextToSpeech(this, this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 32, 28, 32)
            setBackgroundColor(Color.rgb(4, 8, 22))
        }

        val title = TextView(this).apply {
            text = "J.A.R.V.I.S"
            textSize = 30f
            setTextColor(Color.rgb(0, 220, 255))
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            text = "VOICE ENGINE STARTING..."
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 28)
        }

        val mic = Button(this).apply {
            text = "🎙 SPEAK TO JARVIS"
            setOnClickListener { startListening() }
        }

        val test = Button(this).apply {
            text = "TEST TELUGU VOICE"
            setOnClickListener {
                speak("నమస్కారం! నేను జార్విస్. మీకు ఎలా సహాయం చేయగలను?")
            }
        }

        val access = Button(this).apply {
            text = "ACCESSIBILITY SETTINGS"
            setOnClickListener {
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(mic)
        layout.addView(test)
        layout.addView(access)
        setContentView(layout)
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            val languageResult = tts?.setLanguage(Locale("te", "IN"))
            ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                    languageResult != TextToSpeech.LANG_NOT_SUPPORTED
            status.text = if (ttsReady) {
                "JARVIS READY • Telugu voice available"
            } else {
                "JARVIS READY • Telugu TTS not installed"
            }
        } else {
            status.text = "Text-to-Speech initialization failed"
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            Toast.makeText(this, "Telugu voice is not available on this phone", Toast.LENGTH_LONG).show()
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_TTS")
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
            override fun onReadyForSpeech(params: Bundle?) {
                status.text = "LISTENING... మాట్లాడండి"
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                status.text = "PROCESSING..."
            }
            override fun onError(error: Int) {
                status.text = "Voice error: $error • Try again"
            }
            override fun onResults(results: Bundle?) {
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = words?.firstOrNull() ?: "మాటలు గుర్తించబడలేదు"
                status.text = "YOU SAID:\n$spoken"
                speak("మీరు చెప్పారు: $spoken")
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening()
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
