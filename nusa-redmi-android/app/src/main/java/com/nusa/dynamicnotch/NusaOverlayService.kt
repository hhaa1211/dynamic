package com.nusa.dynamicnotch

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.*
import android.view.animation.OvershootInterpolator
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

    // Redmi Note 13 Pro 5G Dimensions (calibrated)
    private var collapsedWidthDp = 136
    private var collapsedHeightDp = 42
    private var yOffsetDp = 4
    private var eyeSpacingDp = 72
    private var eyeYOffsetDp = 0
    private var eyeSizeDp = 10
    private var expandedWidthDp = 350
    private var expandedHeightDp = 175

    // Views
    private lateinit var layerPet: View
    private lateinit var layerIdle: View
    private lateinit var layerMusic: View
    private lateinit var layerPomo: View
    private lateinit var layerCharging: View
    private lateinit var leftEye: View
    private lateinit var rightEye: View
    private var cameraSpace: View? = null
    private var eyesContainer: View? = null

    // Music Player Views & State
    private lateinit var tvMusicTitle: TextView
    private lateinit var tvMusicArtist: TextView
    private lateinit var musicProgressBar: ProgressBar
    private lateinit var btnPlayPause: TextView
    private lateinit var btnPrev: TextView
    private lateinit var btnNext: TextView
    private var btnCloseMusic: TextView? = null

    data class Song(val title: String, val artist: String)
    private val playlist = listOf(
        Song("Paper Sky", "Lumen Coast"),
        Song("Cyber Drift", "SynthWave AI"),
        Song("Nusa Mascot Anthem", "Dynamic Beats"),
        Song("Starlight Bloom", "HyperOS Ambient")
    )
    private var playlistIndex = 0
    private var isPlaying = true
    private var progressValue = 45

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && isExpanded && currentState == NotchState.MUSIC) {
                progressValue = (progressValue + 1) % 100
                musicProgressBar.progress = progressValue
            }
            mainHandler.postDelayed(this, 1000)
        }
    }

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
                        tvMusicTitle.text = title
                        tvMusicArtist.text = text
                        setNotchState(NotchState.MUSIC)
                    }
                }
                ACTION_UPDATE_CONFIG -> {
                    yOffsetDp = intent.getIntExtra("y_offset", yOffsetDp)
                    collapsedWidthDp = intent.getIntExtra("notch_width", collapsedWidthDp)
                    collapsedHeightDp = intent.getIntExtra("notch_height", collapsedHeightDp)
                    eyeSpacingDp = intent.getIntExtra("eye_spacing", eyeSpacingDp)
                    eyeYOffsetDp = intent.getIntExtra("eye_y_offset", eyeYOffsetDp)
                    eyeSizeDp = intent.getIntExtra("eye_size", eyeSizeDp)

                    applyEyeAndNotchConfig()
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
        initMusicPlayerControls()
        registerReceivers()
        mainHandler.post(progressRunnable)
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
            .setContentText("Dynamic Notch né camera Redmi Note 13 Pro 5G")
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
        cameraSpace = overlayView.findViewById(R.id.cameraSpace)
        eyesContainer = overlayView.findViewById(R.id.eyesContainer)

        // Read user preferences
        val prefs = getSharedPreferences("nusa_prefs", Context.MODE_PRIVATE)
        yOffsetDp = prefs.getInt("y_offset", 4)
        collapsedWidthDp = prefs.getInt("notch_width", 136)
        collapsedHeightDp = prefs.getInt("notch_height", 42)
        eyeSpacingDp = prefs.getInt("eye_spacing", 72)
        eyeYOffsetDp = prefs.getInt("eye_y_offset", 0)
        eyeSizeDp = prefs.getInt("eye_size", 10)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Window height extends past the 122px HyperOS status bar boundary to guarantee touch delivery
        val initialW = dpToPx(maxOf(collapsedWidthDp + 24, 160))
        val initialH = dpToPx(maxOf(collapsedHeightDp + 24, 66))

        params = WindowManager.LayoutParams(
            initialW,
            initialH,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dpToPx(yOffsetDp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        applyEyeAndNotchConfig()
        showLayer(layerPet)

        // Gesture detector for smooth tap and swipe interactions
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!isExpanded) {
                    setNotchState(NotchState.MUSIC)
                    return true
                }
                return false
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!isExpanded) {
                    setNotchState(NotchState.MUSIC)
                    return true
                }
                return false
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 != null) {
                    val dy = e2.y - e1.y
                    if (dy > 25 && !isExpanded) {
                        setNotchState(NotchState.MUSIC)
                        return true
                    } else if (dy < -25 && isExpanded) {
                        setNotchState(NotchState.PET)
                        return true
                    }
                }
                return false
            }
        })

        // Tap outside collapses when expanded; otherwise route through gesture detector
        overlayView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                if (isExpanded) {
                    setNotchState(NotchState.PET)
                }
                return@setOnTouchListener true
            }

            if (!isExpanded) {
                return@setOnTouchListener gestureDetector.onTouchEvent(event)
            } else {
                gestureDetector.onTouchEvent(event)
                return@setOnTouchListener false
            }
        }

        notchCard.setOnClickListener {
            if (!isExpanded) {
                setNotchState(NotchState.MUSIC)
            }
        }

        windowManager.addView(overlayView, params)
    }

    private fun applyEyeAndNotchConfig() {
        params.y = dpToPx(yOffsetDp)

        // 1. Spacing between eyes (camera punch hole clearance)
        cameraSpace?.let {
            val lp = it.layoutParams
            lp.width = dpToPx(eyeSpacingDp)
            it.layoutParams = lp
        }

        // 2. Vertical position of eyes
        eyesContainer?.translationY = dpToPx(eyeYOffsetDp).toFloat()

        // 3. Eye dimensions
        val eyeW = dpToPx(eyeSizeDp)
        val eyeH = dpToPx((eyeSizeDp * 1.45).toInt())
        val leftLp = leftEye.layoutParams
        leftLp.width = eyeW
        leftLp.height = eyeH
        leftEye.layoutParams = leftLp

        val rightLp = rightEye.layoutParams
        rightLp.width = eyeW
        rightLp.height = eyeH
        rightEye.layoutParams = rightLp

        if (!isExpanded) {
            val lp = notchCard.layoutParams
            lp.width = dpToPx(collapsedWidthDp)
            lp.height = dpToPx(collapsedHeightDp)
            notchCard.layoutParams = lp
            notchCard.radius = dpToPx(collapsedHeightDp / 2).toFloat()

            // Extended hit box guarantees touch events bypass HyperOS status bar gesture interceptor
            params.width = dpToPx(maxOf(collapsedWidthDp + 24, 160))
            params.height = dpToPx(maxOf(collapsedHeightDp + 24, 66))
        }

        try {
            windowManager.updateViewLayout(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initMusicPlayerControls() {
        tvMusicTitle = overlayView.findViewById(R.id.tvMusicTitle)
        tvMusicArtist = overlayView.findViewById(R.id.tvMusicArtist)
        musicProgressBar = overlayView.findViewById(R.id.musicProgressBar)
        btnPlayPause = overlayView.findViewById(R.id.btnPlayPause)
        btnPrev = overlayView.findViewById(R.id.btnPrev)
        btnNext = overlayView.findViewById(R.id.btnNext)
        btnCloseMusic = overlayView.findViewById(R.id.btnCloseMusic)

        updateMusicDisplay()

        btnPlayPause.setOnClickListener {
            isPlaying = !isPlaying
            btnPlayPause.text = if (isPlaying) "⏸" else "▶"
        }

        btnNext.setOnClickListener {
            playlistIndex = (playlistIndex + 1) % playlist.size
            progressValue = 0
            musicProgressBar.progress = 0
            updateMusicDisplay()
        }

        btnPrev.setOnClickListener {
            playlistIndex = if (playlistIndex - 1 < 0) playlist.size - 1 else playlistIndex - 1
            progressValue = 0
            musicProgressBar.progress = 0
            updateMusicDisplay()
        }

        btnCloseMusic?.setOnClickListener {
            setNotchState(NotchState.PET)
        }
    }

    private fun updateMusicDisplay() {
        val song = playlist[playlistIndex]
        tvMusicTitle.text = song.title
        tvMusicArtist.text = song.artist
        btnPlayPause.text = if (isPlaying) "⏸" else "▶"
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
                updateMusicDisplay()
                showLayer(layerMusic)
                expandNotch(expandedWidthDp, expandedHeightDp)
            }
            NotchState.POMODORO -> {
                showLayer(layerPomo)
                expandNotch(expandedWidthDp, expandedHeightDp)
            }
            NotchState.CHARGING -> {
                showLayer(layerCharging)
                expandNotch(320, 56)
            }
            else -> {}
        }
    }

    private fun showLayer(activeLayer: View) {
        listOf(layerPet, layerIdle, layerMusic, layerPomo, layerCharging).forEach {
            it.visibility = if (it == activeLayer) View.VISIBLE else View.GONE
        }
    }

    private fun expandNotch(targetWidthDp: Int, targetHeightDp: Int) {
        isExpanded = true

        val targetW = dpToPx(targetWidthDp)
        val targetH = dpToPx(targetHeightDp)

        // Expand WindowManager immediately so child view is never clipped
        params.width = targetW
        params.height = targetH
        try {
            windowManager.updateViewLayout(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val startW = if (notchCard.width > 0) notchCard.width else dpToPx(collapsedWidthDp)
        val startH = if (notchCard.height > 0) notchCard.height else dpToPx(collapsedHeightDp)

        animateCardSize(
            startW, targetW,
            startH, targetH,
            dpToPx(collapsedHeightDp / 2), dpToPx(28),
            null
        )
    }

    private fun collapseNotch() {
        isExpanded = false
        val targetW = dpToPx(collapsedWidthDp)
        val targetH = dpToPx(collapsedHeightDp)

        animateCardSize(
            notchCard.width, targetW,
            notchCard.height, targetH,
            dpToPx(28), dpToPx(collapsedHeightDp / 2)
        ) {
            // Shrink window back to hit box bounds on animation end
            params.width = dpToPx(maxOf(collapsedWidthDp + 24, 160))
            params.height = dpToPx(maxOf(collapsedHeightDp + 24, 66))
            try {
                windowManager.updateViewLayout(overlayView, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        showLayer(layerPet)
    }

    private fun animateCardSize(
        fromW: Int, toW: Int,
        fromH: Int, toH: Int,
        fromRadius: Int, toRadius: Int,
        onEnd: (() -> Unit)?
    ) {
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 280
        animator.interpolator = OvershootInterpolator(1.1f)
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
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onEnd?.invoke()
            }
        })
        animator.start()
    }

    private fun initBlinkingAnimation() {
        val blinkRunnable = object : Runnable {
            override fun run() {
                if (currentState == NotchState.PET && !isExpanded) {
                    leftEye.animate().scaleY(0.1f).setDuration(110).withEndAction {
                        leftEye.animate().scaleY(1.0f).setDuration(140).start()
                    }.start()

                    rightEye.animate().scaleY(0.1f).setDuration(110).withEndAction {
                        rightEye.animate().scaleY(1.0f).setDuration(140).start()
                    }.start()
                }
                mainHandler.postDelayed(this, 4000)
            }
        }
        mainHandler.postDelayed(blinkRunnable, 2000)
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(ACTION_SET_STATE)
            addAction(ACTION_UPDATE_EVENT)
            addAction(ACTION_UPDATE_CONFIG)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(eventReceiver, filter)
        }
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
        mainHandler.removeCallbacksAndMessages(null)
        unregisterReceiver(eventReceiver)
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }

    companion object {
        const val ACTION_SET_STATE = "com.nusa.dynamicnotch.ACTION_SET_STATE"
        const val ACTION_UPDATE_EVENT = "com.nusa.dynamicnotch.ACTION_UPDATE_EVENT"
        const val ACTION_UPDATE_CONFIG = "com.nusa.dynamicnotch.ACTION_UPDATE_CONFIG"
    }
}
