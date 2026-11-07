package com.nusa.dynamicnotch

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NusaNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        val extras = sbn.notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Filter out our own notifications
        if (sbn.packageName == packageName) return

        // Check if this is a media notification
        val isMedia = sbn.notification.category == Notification.CATEGORY_TRANSPORT ||
                sbn.notification.actions != null

        val intent = Intent(NusaOverlayService.ACTION_UPDATE_EVENT).apply {
            putExtra("is_media", isMedia)
            putExtra("title", title)
            putExtra("text", text)
            putExtra("package", sbn.packageName)
        }
        sendBroadcast(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
