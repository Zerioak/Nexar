package com.nexar.assistant.notifications

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.nexar.assistant.utils.NexarLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NexarNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NexarNotifListener"
        private var instance: NexarNotificationListenerService? = null

        fun getInstance(): NexarNotificationListenerService? = instance

        private val _isActive = MutableStateFlow(false)
        val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    }

    private val notifications = mutableMapOf<String, NotificationInfo>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        _isActive.value = true
        NexarLogger.d(TAG, "Notification listener connected")
        // Load existing notifications
        try {
            activeNotifications?.forEach { sbn ->
                processNotification(sbn)
            }
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Error loading existing notifications", e)
        }
    }

    override fun onListenerDisconnected() {
        instance = null
        _isActive.value = false
        notifications.clear()
        super.onListenerDisconnected()
        NexarLogger.d(TAG, "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { processNotification(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let {
            notifications.remove(it.key)
        }
    }

    private fun processNotification(sbn: StatusBarNotification) {
        try {
            val notification = sbn.notification
            val extras = notification.extras

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""

            if (title.isBlank() && text.isBlank()) return

            val appName = getAppName(sbn.packageName)

            val info = NotificationInfo(
                key = sbn.key,
                appName = appName,
                title = title,
                text = text,
                packageName = sbn.packageName,
                timestamp = sbn.postTime
            )
            notifications[sbn.key] = info
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Error processing notification", e)
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast(".")
        }
    }

    fun getCachedNotifications(): List<NotificationInfo> {
        return notifications.values
            .sortedByDescending { it.timestamp }
            .take(20) // Limit to 20 most recent
    }

    fun openNotification(keyOrTitle: String): Boolean {
        val notif = notifications.values.find {
            it.key == keyOrTitle ||
            it.title.lowercase().contains(keyOrTitle.lowercase()) ||
            it.appName.lowercase().contains(keyOrTitle.lowercase())
        } ?: return false

        return try {
            val sbn = activeNotifications?.find { it.key == notif.key }
            sbn?.notification?.contentIntent?.send()
            true
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Error opening notification", e)
            false
        }
    }
}
