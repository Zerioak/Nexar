package com.nexar.assistant.notifications

data class NotificationInfo(
    val key: String,
    val appName: String,
    val title: String,
    val text: String,
    val packageName: String,
    val timestamp: Long
)

class NexarNotificationRepository {

    fun getNotifications(appFilter: String? = null): List<NotificationInfo> {
        val service = NexarNotificationListenerService.getInstance() ?: return emptyList()
        val all = service.getActiveNotifications()
        return if (appFilter != null) {
            all.filter { it.appName.lowercase().contains(appFilter.lowercase()) }
        } else {
            all
        }
    }

    fun openNotification(keyOrTitle: String): Boolean {
        val service = NexarNotificationListenerService.getInstance() ?: return false
        return service.openNotification(keyOrTitle)
    }
}
