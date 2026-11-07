package com.nusa.dynamicnotch

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.nusa.dynamicnotch.models.NotchState
import com.nusa.dynamicnotch.models.SongInfo

class NusaOverlayService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var notchCard: CardView
    private lateinit var params: WindowManager.LayoutParams

    private var currentState: NotchState = NotchState.PET
    private var isExpanded: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // Redmi Note 13 Pro 5G Dimensions (dp)
    private var collapsedWidthDp = 92
    private var collapsedHeightDp = 30
    private var yOffsetDp = 18
    private var expandedWidthDp = 350
    private var expandedHeightDp = 160

    // Views
    private lateinit var layerPet: View
    private lateinit var layerIdle: View
    private lateinit var layerMusic: View
    private lateinit var layerPomo: View
    private lateinit var layerCharging: View
    private lateinit var leftEye: View
    private lateinit var rightEye: View

    // Media & Battery
    private var currentSong = SongInfo()

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_SET_STATE -> {
                    val stateName = intent.getStringExtra("state") ?: return
                    try {
                        val targetState = NotchState.valueOf(stateName)
                        setNotchState(targetState)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                ACTION_UPDATE_EVENT -> {
                    val isMedia = intent.getBooleanExtra("is_media", false)
                    val title = intent.getStringExtra("title") ?: ""
                    val text = intent.getStringExtra("text") ?: ""
                    if (isMedia && title.isNotEmpty()) {
                        currentSong = SongInfo(title = title, artist = text)
                        updateMusicView()
                        setNotchState(NotchState.MUSIC)
                    }
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    setNotchState(NotchState.CHARGING)
                    mainHandler.postDelayed({
                        setNotchState(NotchState.PET)
                    }, 4000)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        initOverlay()
        initBlinkingAnimation()
        registerReceivers()
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
            .setContentTitle("Nusa Notch đang hoạt động")
            .setContentText("Đang hiển thị quanh camera Redmi Note 13 Pro 5G")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        startForeground(101, notification)
    }

    private fun initOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.view_nusa_notch, null)

        notchCard = overlayView.findViewById(R.id.notchCard)
        layerPet = overlayView.findViewById(R.id.layerPet)
        layerIdle = overlayView.findViewById(R.id.layerIdle)
        layerMusic = overlayView.findViewById(R.id.layerMusic)
        layerPomo = overlayView.findViewById(R.id.layerPomo)
        layerCharging = overlayView.findViewById(R.id.layerCharging)
        leftEye = overlayView.findViewById(R.id.leftEye)
        rightEye = overlayView.findViewById(R.id.rightEye)

        // Read user preferences
        val prefs = getSharedPreferences("nusa_prefs", Context.MODE_PRIVATE)
        yOffsetDp = prefs.getInt("y_offset", 18)
        collapsedWidthDp = prefs.getInt("notch_width", 92)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dpToPx(yOffsetDp)
        }

        applyCollapsedDimensions()

        // Click on Notch toggles expand / collapse
        notchCard.setOnClickListener {
            if (isExpanded) {
                collapseNotch()
            } else {
                expandNotch(NotchState.MUSIC)
            }
        }

        windowManager.addView(overlayView, params)
    }

    private fun setNotchState(state: NotchState) {
        currentState = state
        when (state) {
            NotchState.PET -> {
                collapseNotch()
                showLayer(layerPet)
            }
            NotchState.IDLE -> {
                collapseNotch()
                showLayer(layerIdle)
            }
            NotchState.MUSIC -> {
                updateMusicView()
                expandNotch(state)
                showLayer(layerMusic)
            }
            NotchState.POMODORO -> {
                expandNotch(state)
                showLayer(layerPomo)
            }
            NotchState.CHARGING -> {
                expandHud(320, 56)
                showLayer(layerCharging)
            }
            else -> {}
        }
    }

    private fun showLayer(activeLayer: View) {
        listOf(layerPet, layerIdle, layerMusic, layerPomo, layerCharging).forEach {
            it.visibility = if (it == activeLayer) View.VISIBLE else View.GONE
        }
    }

    private fun updateMusicView() {
        overlayView.findViewById<TextView>(R.id.tvMusicTitle)?.text = currentSong.title
        overlayView.findViewById<TextView>(R.id.tvMusicArtist)?.text = currentSong.artist
    }

    private fun expandNotch(targetState: NotchState) {
        isExpanded = true
        animateCardSize(
            notchCard.width, dpToPx(expandedWidthDp),
            notchCard.height, dpToPx(expandedHeightDp),
            dpToPx(16), dpToPx(28)
        )
    }

    private fun expandHud(widthDp: Int, heightDp: Int) {
        isExpanded = true
        animateCardSize(
            notchCard.width, dpToPx(widthDp),
            notchCard.height, dpToPx(heightDp),
            dpToPx(16), dpToPx(20)
        )
    }

    private fun collapseNotch() {
        isExpanded = false
        animateCardSize(
            notchCard.width, dpToPx(collapsedWidthDp),
            notchCard.height, dpToPx(collapsedHeightDp),
            dpToPx(28), dpToPx(16)
        )
        showLayer(layerPet)
    }

    private fun applyCollapsedDimensions() {
        val layoutParams = notchCard.layoutParams
        layoutParams.width = dpToPx(collapsedWidthDp)
        layoutParams.height = dpToPx(collapsedHeightDp)
        notchCard.layoutParams = layoutParams
        notchCard.radius = dpToPx(16).toFloat()
    }

    private fun animateCardSize(
        fromW: Int, toW: Int,
        fromH: Int, toH: Int,
        fromRadius: Int, toRadius: Int
    ) {
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 320
        animator.interpolator = OvershootInterpolator(1.15f)
        animator.addUpdateListener { valAnim ->
            val fraction = valAnim.animatedValue as Float
            val currentW = (fromW + (toW - fromW) * fraction).toInt()
            val currentH = (fromH + (toH - fromH) * fraction).toInt()
            val currentR = fromRadius + (toRadius - fromRadius) * fraction

            val lp = notchCard.layoutParams
            lp.width = currentW
            lp.height = currentH
            notchCard.layoutParams = lp
            notchCard.radius = currentR

            try {
                windowManager.updateViewLayout(overlayView, params)
            } catch (e: Exception) {
                // ignore transient detached window
            }
        }
        animator.start()
    }

    private fun initBlinkingAnimation() {
        val blinkRunnable = object : Runnable {
            override fun run() {
                if (currentState == NotchState.PET && !isExpanded) {
                    leftEye.animate().scaleY(0.1f).setDuration(120).withEndAction {
                        leftEye.animate().scaleY(1.0f).setDuration(150).start()
                    }.start()

                    rightEye.animate().scaleY(0.1f).setDuration(120).withEndAction {
                        rightEye.animate().scaleY(1.0f).setDuration(150).start()
                    }.start()
                }
                mainHandler.postDelayed(this, 4500)
            }
        }
        mainHandler.postDelayed(blinkRunnable, 2000)
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(ACTION_SET_STATE)
            addAction(ACTION_UPDATE_EVENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        registerReceiver(eventReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(eventReceiver)
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }

    companion object {
        const val ACTION_SET_STATE = "com.nusa.dynamicnotch.ACTION_SET_STATE"
        const val ACTION_UPDATE_EVENT = "com.nusa.dynamicnotch.ACTION_UPDATE_EVENT"
    }
}
