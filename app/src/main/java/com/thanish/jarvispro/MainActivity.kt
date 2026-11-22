package com.thanish.jarvispro

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(4, 8, 22)
        window.navigationBarColor = Color.rgb(4, 8, 22)
        val bg = Color.rgb(4, 8, 22)
        val cyan = Color.rgb(0, 220, 255)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 32, 28, 32)
            setBackgroundColor(bg)
        }
        val title = TextView(this).apply {
            text = "J.A.R.V.I.S"
            textSize = 30f
            setTextColor(cyan)
            gravity = Gravity.CENTER
        }
        val status = TextView(this).apply {
            text = "PRO  •  SYSTEM READY\n\nVoice + AI modules are not connected yet."
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 32)
        }
        val mic = Button(this).apply {
            text = "ENABLE MICROPHONE"
            setOnClickListener {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
                } else Toast.makeText(this@MainActivity, "Microphone permission granted", Toast.LENGTH_SHORT).show()
            }
        }
        val access = Button(this).apply {
            text = "ACCESSIBILITY SETTINGS"
            setOnClickListener {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        layout.addView(title)
        layout.addView(status)
        layout.addView(mic)
        layout.addView(access)
        setContentView(layout)
    }
}
