package com.nexar.assistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nexar.assistant.MainActivity
import com.nexar.assistant.NexarApplication
import com.nexar.assistant.R
import com.nexar.assistant.screen.capture.NexarScreenCaptureManager
import com.nexar.assistant.utils.NexarLogger

/**
 * Foreground service required by Android 10+ when performing MediaProjection screen capture.
 *
 * This service is ONLY started after the user explicitly approves the MediaProjection
 * permission dialog (RESULT_OK returned by the system). It must never be started at
 * app launch or before user consent.
 *
 * Lifecycle:
 *   User taps "Start screen share"
 *     -> Android MediaProjection permission dialog shown
 *       -> User approves (RESULT_OK + data)
 *         -> NexarViewModel.onScreenShareGranted() calls startScreenCaptureService()
 *           -> this service starts, calls startForeground with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
 *             -> NexarScreenCaptureManager.start() is called with the granted projection data
 *   User taps "Stop screen share" (or service is stopped externally)
 *     -> NexarViewModel.stopScreenShare() calls stopScreenCaptureService()
 *       -> NexarScreenCaptureManager.stop() is called
 *         -> this service stops itself
 */
class ScreenCaptureForegroundService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureFgService"
        private const val NOTIFICATION_ID = 1002
        const val ACTION_START = "com.nexar.assistant.SCREEN_CAPTURE_START"
        const val ACTION_STOP  = "com.nexar.assistant.SCREEN_CAPTURE_STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_PROJECTION_DATA = "projection_data"

        private var instance: ScreenCaptureForegroundService? = null
        fun getInstance(): ScreenCaptureForegroundService? = instance

        /** Convenience: build and return the start Intent with the MediaProjection grant data. */
        fun buildStartIntent(context: Context, resultCode: Int, data: Intent): Intent =
            Intent(context, ScreenCaptureForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_PROJECTION_DATA, data)
            }

        /** Convenience: build the stop Intent. */
        fun buildStopIntent(context: Context): Intent =
            Intent(context, ScreenCaptureForegroundService::class.java).apply {
                action = ACTION_STOP
            }
    }

    private var screenCaptureManager: NexarScreenCaptureManager? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        NexarLogger.d(TAG, "ScreenCaptureForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val projectionData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_PROJECTION_DATA)
                }

                if (resultCode == android.app.Activity.RESULT_OK && projectionData != null) {
                    startCapture(resultCode, projectionData)
                } else {
                    NexarLogger.e(TAG, "Invalid MediaProjection grant data — stopping service")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopCapture()
                stopSelf()
            }
            else -> {
                // Unknown action — do not start capture; stop cleanly
                NexarLogger.w(TAG, "Unknown action: ${intent?.action} — stopping")
                stopSelf()
            }
        }
        return START_NOT_STICKY  // Do not restart automatically; capture requires fresh user consent
    }

    private fun startCapture(resultCode: Int, projectionData: Intent) {
        // Must call startForeground with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        // before any MediaProjection API call on Android 10+
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        screenCaptureManager = NexarScreenCaptureManager(applicationContext)
        screenCaptureManager?.start(resultCode, projectionData)
        NexarLogger.d(TAG, "Screen capture started via foreground service")
    }

    private fun stopCapture() {
        screenCaptureManager?.stop()
        screenCaptureManager = null
        NexarLogger.d(TAG, "Screen capture stopped")
    }

    /** Called by NexarViewModel.stopScreenShare() to get the manager and stop it cleanly. */
    fun getScreenCaptureManager(): NexarScreenCaptureManager? = screenCaptureManager

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            buildStopIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, NexarApplication.NOTIFICATION_CHANNEL_MEDIA)
            .setContentTitle("NEXAR Screen Share")
            .setContentText("Screen sharing is active")
            .setSmallIcon(R.drawable.ic_nexar_logo)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopIntent
            )
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopCapture()
        instance = null
        super.onDestroy()
        NexarLogger.d(TAG, "ScreenCaptureForegroundService destroyed")
    }
}
