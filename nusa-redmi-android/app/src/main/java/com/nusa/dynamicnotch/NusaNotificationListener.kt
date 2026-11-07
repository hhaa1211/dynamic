package com.nusa.dynamicnotch

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NusaNotificationListener : NotificationListenerService() {

    companion object {
        var activeMediaController: MediaController? = null
            private set

        fun playMedia() {
            try {
                activeMediaController?.transportControls?.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun pauseMedia() {
            try {
                activeMediaController?.transportControls?.pause()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun skipNext() {
            try {
                activeMediaController?.transportControls?.skipToNext()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun skipPrev() {
            try {
                activeMediaController?.transportControls?.skipToPrevious()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun seekTo(posMs: Long) {
            try {
                activeMediaController?.transportControls?.seekTo(posMs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var mediaSessionManager: MediaSessionManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            syncMediaState(activeMediaController)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            syncMediaState(activeMediaController)
        }
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        val active = controllers?.firstOrNull { c ->
            val s = c.playbackState?.state
            s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING
        } ?: controllers?.firstOrNull()

        bindMediaController(active)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val component = ComponentName(this, NusaNotificationListener::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionListener, component)
            val controllers = mediaSessionManager?.getActiveSessions(component)
            bindMediaController(controllers?.firstOrNull())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        unbindMediaController()
        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
    }

    private fun bindMediaController(controller: MediaController?) {
        if (activeMediaController == controller && controller != null) return
        unbindMediaController()
        activeMediaController = controller
        controller?.registerCallback(mediaCallback, mainHandler)
        syncMediaState(controller)
    }

    private fun unbindMediaController() {
        try {
            activeMediaController?.unregisterCallback(mediaCallback)
        } catch (e: Exception) {
            // ignore
        }
        activeMediaController = null
    }

    private fun syncMediaState(controller: MediaController?) {
        if (controller == null) return
        val metadata = controller.metadata
        val pbState = controller.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = pbState?.position ?: 0L
        val isPlaying = pbState?.state == PlaybackState.STATE_PLAYING

        val artBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        if (title.isNotEmpty()) {
            DynamicNotchViewManager.onMediaUpdated(
                title = title,
                artist = artist,
                albumArt = artBitmap,
                durationMs = duration,
                positionMs = position,
                isPlaying = isPlaying,
                packageName = controller.packageName
            )
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return
        if (sbn.packageName == packageName) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Check if media session notification
        if (notification.category == Notification.CATEGORY_TRANSPORT ||
            extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
            // Handled via MediaSessionManager
            return
        }

        val title = extras.getString(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: return

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""

        // Filter out empty or progress-only notifications
        if (title.isEmpty() || text.isEmpty()) return

        var iconBitmap: Bitmap? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val largeIcon = notification.getLargeIcon()
            if (largeIcon != null) {
                try {
                    val drawable = largeIcon.loadDrawable(this)
                    if (drawable is BitmapDrawable) {
                        iconBitmap = drawable.bitmap
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        val pendingIntent = notification.contentIntent

        DynamicNotchViewManager.onNotificationReceived(
            packageName = sbn.packageName,
            title = title,
            text = text,
            largeIcon = iconBitmap,
            pendingIntent = pendingIntent
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
