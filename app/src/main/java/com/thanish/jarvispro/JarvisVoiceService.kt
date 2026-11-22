package com.thanish.jarvispro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class JarvisVoiceService : Service() {
    private var detector: WakeWordDetector? = null
    private val channelId = "jarvis_voice_listener"

    override fun onCreate() {
        super.onCreate()
        createChannel()
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
        val notification = buildNotification("Preparing offline wake-word model…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Thread {
            try {
                val model = VoskModelManager.ensureModel(this) { message -> updateNotification(message) }
                detector?.stop()
                detector = WakeWordDetector(model.absolutePath, { phrase ->
                    updateNotification("Wake word detected: $phrase • Tap to open JARVIS")
                    val open = Intent(this, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                    // Android may block background activity launches; notification remains available.
                    try { startActivity(open) } catch (_: Exception) {}
                }, ::updateNotification)
                detector?.start()
            } catch (e: Exception) {
                updateNotification("Wake-word setup failed: ${e.message ?: "unknown error"}")
                stopSelf()
            }
        }.start()
    }

    private fun stopListener() {
        detector?.stop()
        detector = null
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
            .setContentTitle("JARVIS PRO • Wake-word mode")
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
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.thanish.jarvispro.action.START_WAKE"
        const val ACTION_STOP = "com.thanish.jarvispro.action.STOP_WAKE"
        private const val NOTIFICATION_ID = 731
    }
}
