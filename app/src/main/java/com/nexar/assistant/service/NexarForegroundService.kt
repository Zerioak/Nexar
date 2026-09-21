package com.nexar.assistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nexar.assistant.MainActivity
import com.nexar.assistant.NexarApplication
import com.nexar.assistant.R
import com.nexar.assistant.utils.NexarLogger

class NexarForegroundService : Service() {

    companion object {
        private const val TAG = "NexarFgService"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.nexar.assistant.START"
        const val ACTION_STOP = "com.nexar.assistant.STOP"
        const val ACTION_SHOW_ORB = "com.nexar.assistant.SHOW_ORB"
        const val ACTION_HIDE_ORB = "com.nexar.assistant.HIDE_ORB"

        private var instance: NexarForegroundService? = null
        fun getInstance(): NexarForegroundService? = instance
    }

    private val binder = LocalBinder()
    private var orbWindowManager: NexarOrbWindowManager? = null
    private var isOrbVisible = false

    inner class LocalBinder : Binder() {
        fun getService(): NexarForegroundService = this@NexarForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        NexarLogger.d(TAG, "NexarForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundNotification()
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SHOW_ORB -> {
                showFloatingOrb()
            }
            ACTION_HIDE_ORB -> {
                hideFloatingOrb()
            }
            else -> {
                startForegroundNotification()
            }
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        NexarLogger.d(TAG, "Foreground service started")
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, NexarForegroundService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NexarApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("NEXAR Assistant")
            .setContentText("Your AI assistant is active")
            .setSmallIcon(R.drawable.ic_nexar_logo)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_view,
                "Open",
                openIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopIntent
            )
            .build()
    }

    fun showFloatingOrb() {
        if (isOrbVisible) return
        try {
            orbWindowManager = NexarOrbWindowManager(this)
            orbWindowManager?.show()
            isOrbVisible = true
            NexarLogger.d(TAG, "Floating orb shown")
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Failed to show floating orb", e)
        }
    }

    fun hideFloatingOrb() {
        try {
            orbWindowManager?.hide()
            orbWindowManager = null
            isOrbVisible = false
            NexarLogger.d(TAG, "Floating orb hidden")
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Error hiding orb", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        hideFloatingOrb()
        instance = null
        super.onDestroy()
        NexarLogger.d(TAG, "NexarForegroundService destroyed")
    }
}
