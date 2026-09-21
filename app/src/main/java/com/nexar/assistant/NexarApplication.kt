package com.nexar.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.nexar.assistant.di.NexarContainer

class NexarApplication : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "nexar_assistant_channel"
        const val NOTIFICATION_CHANNEL_MEDIA = "nexar_media_channel"
        lateinit var instance: NexarApplication
            private set
    }

    lateinit var container: NexarContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = NexarContainer(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val assistantChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }

            val mediaChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_MEDIA,
                "NEXAR Media",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "NEXAR media projection notifications"
                setShowBadge(false)
            }

            manager.createNotificationChannel(assistantChannel)
            manager.createNotificationChannel(mediaChannel)
        }
    }
}
