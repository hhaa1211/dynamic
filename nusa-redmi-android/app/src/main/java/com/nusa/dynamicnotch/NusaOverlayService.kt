package com.nusa.dynamicnotch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService

class NusaOverlayService : LifecycleService() {

    companion object {
        const val ACTION_SET_STATE = "com.nusa.dynamicnotch.ACTION_SET_STATE"
        const val ACTION_UPDATE_EVENT = "com.nusa.dynamicnotch.ACTION_UPDATE_EVENT"
        const val ACTION_UPDATE_CONFIG = "com.nusa.dynamicnotch.ACTION_UPDATE_CONFIG"
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()

        // Attach using TYPE_APPLICATION_OVERLAY if not already attached via Accessibility
        if (!DynamicNotchViewManager.isAttached) {
            DynamicNotchViewManager.attach(this, asAccessibility = false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!DynamicNotchViewManager.isAttached) {
            DynamicNotchViewManager.attach(this, asAccessibility = false)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!DynamicNotchViewManager.isAccessibilityMode) {
            DynamicNotchViewManager.detach()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val isLandscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        DynamicNotchViewManager.onOrientationChanged(isLandscape)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun startForegroundNotification() {
        val channelId = "nusa_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Nusa Dynamic Notch Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Dynamic Island đang hoạt động")
            .setContentText("Apple Dynamic Island tối ưu Redmi Note 13 Pro 5G")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        startForeground(101, notification)
    }
}
