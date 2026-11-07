package com.nusa.dynamicnotch

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Rect
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
import java.util.Random

class NusaOverlayService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var notchContainer: View
    private lateinit var notchCard: CardView
    private lateinit var detachedBubble: CardView
    private lateinit var tvBubbleIcon: TextView
    private lateinit var params: WindowManager.LayoutParams

    private var currentState: NotchState = NotchState.PET
    private var isExpanded: Boolean = false
    private var isMinimalMode: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val random = Random()

    // Calibrated Dimensions for Redmi Note 13 Pro 5G (Punch hole centered at X=610, Cutout=21.3dp)
    private var collapsedWidthDp = 138
    private var compactActivityWidthDp = 168
    private var collapsedHeightDp = 46
    private var yOffsetDp = 4
    private var eyeSpacingDp = 72
    private var eyeYOffsetDp = 0
    private var eyeSizeDp = 10
    private var expandedWidthDp = 356
    private var expandedHeightDp = 195

    // Compact Presentation Views
    private lateinit var compactPet: View
    private lateinit var compactMusic: View
    private lateinit var compactPomo: View
    private lateinit var compactCharging: View
    private lateinit var compactCall: View
    private lateinit var leftEye: View
    private lateinit var rightEye: View
    private var cameraSpace: View? = null
    private var eyesContainer: View? = null
    private lateinit var compactMusicCutout: View
    private lateinit var compactPomoCutout: View
    private lateinit var compactChargeCutout: View
    private lateinit var compactCallCutout: View
    private lateinit var tvCompactPomoTime: TextView
    private lateinit var tvCompactChargePercent: TextView
    private lateinit var tvCompactCallTime: TextView

    // Live Equalizer Waveform Bars (ılılı)
    private lateinit var eqBar1: View
    private lateinit var eqBar2: View
    private lateinit var eqBar3: View
    private lateinit var eqBar4: View

    // Expanded Presentation Views
    private lateinit var layerMusic: View
    private lateinit var layerPomo: View
    private lateinit var layerCharging: View
    private lateinit var layerCall: View

    // Expanded Music Controls
    private lateinit var tvMusicTitle: TextView
    private lateinit var tvMusicArtist: TextView
    private lateinit var tvTimeCurrent: TextView
    private lateinit var tvTimeRemaining: TextView
    private lateinit var musicProgressBar: ProgressBar
    private lateinit var btnPlayPause: TextView
    private lateinit var btnPrev: TextView
    private lateinit var btnNext: TextView
    private lateinit var btnCloseMusic: TextView

    // Expanded Pomodoro Controls
    private lateinit var tvPomoCountdown: TextView
    private lateinit var btnPomoToggle: Button
    private lateinit var btnPomoReset: Button
    private lateinit var btnClosePomo: TextView

    // Expanded Charging Controls
    private lateinit var tvChargePercent: TextView
    private lateinit var btnCloseCharge: TextView

    // Expanded Call Controls
    private lateinit var btnAcceptCall: TextView
    private lateinit var btnDeclineCall: TextView
    private lateinit var btnCloseCall: TextView

    data class Song(val title: String, val artist: String)
    private val playlist = listOf(
        Song("Paper Sky", "Lumen Coast"),
        Song("Cyber Drift", "SynthWave AI"),
        Song("Nusa Mascot Anthem", "Dynamic Beats"),
        Song("Starlight Bloom", "HyperOS Ambient")
    )
    private var playlistIndex = 0
    private var isPlaying = true
    private var progressValue = 35

    // Pomodoro State
    private var pomoSeconds = 24 * 60 + 50
    private var isPomoRunning = true

    // Call State
    private var callSeconds = 84

    // 1. Equalizer Waveform Animation Runnable
    private val eqRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && (currentState == NotchState.MUSIC || currentState == NotchState.MINIMAL) && !isExpanded) {
                listOf(eqBar1, eqBar2, eqBar3, eqBar4).forEach { bar ->
                    val scale = 0.25f + random.nextFloat() * 0.75f
                    bar.animate().scaleY(scale).setDuration(110).start()
                }
            } else {
                listOf(eqBar1, eqBar2, eqBar3, eqBar4).forEach { bar ->
                    bar.animate().scaleY(0.25f).setDuration(120).start()
                }
            }
            mainHandler.postDelayed(this, 130)
        }
    }

    // 2. Music Progress Runnable
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && (currentState == NotchState.MUSIC || currentState == NotchState.MINIMAL)) {
                progressValue = (progressValue + 1) % 100
                musicProgressBar.progress = progressValue
                val elapsedSec = (progressValue * 220) / 100
                val remainSec = 220 - elapsedSec
                tvTimeCurrent.text = String.format("%d:%02d", elapsedSec / 60, elapsedSec % 60)
                tvTimeRemaining.text = String.format("-%d:%02d", remainSec / 60, remainSec % 60)
            }
            mainHandler.postDelayed(this, 1000)
        }
    }

    // 3. Pomodoro Countdown Runnable
    private val pomoRunnable = object : Runnable {
        override fun run() {
            if (isPomoRunning && pomoSeconds > 0) {
                pomoSeconds--
                val min = pomoSeconds / 60
                val sec = pomoSeconds % 60
                val timeStr = String.format("%02d:%02d", min, sec)
                tvPomoCountdown.text = timeStr
                tvCompactPomoTime.text = timeStr
            }
            mainHandler.postDelayed(this, 1000)
        }
    }

    // 4. Call Duration Runnable
    private val callRunnable = object : Runnable {
        override fun run() {
            if (currentState == NotchState.CALL) {
                callSeconds++
                val min = callSeconds / 60
                val sec = callSeconds % 60
                tvCompactCallTime.text = String.format("%02d:%02d", min, sec)
            }
            mainHandler.postDelayed(this, 1000)
        }
    }

    // 5. Mascot Eye Blinking Runnable
    private val blinkRunnable = object : Runnable {
        override fun run() {
            if (currentState == NotchState.PET && !isExpanded) {
                leftEye.animate().scaleY(0.1f).setDuration(110).withEndAction {
                    leftEye.animate().scaleY(1.0f).setDuration(140).start()
                }.start()
                rightEye.animate().scaleY(0.1f).setDuration(110).withEndAction {
                    rightEye.animate().scaleY(1.0f).setDuration(140).start()
                }.start()
            }
            mainHandler.postDelayed(this, 3800)
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
        initMusicPlayerControls()
        initPomoControls()
        initCallControls()
        registerReceivers()

        mainHandler.post(progressRunnable)
        mainHandler.post(pomoRunnable)
        mainHandler.post(callRunnable)
        mainHandler.post(eqRunnable)
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

    private fun initOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.view_nusa_notch, null)

        notchContainer = overlayView.findViewById(R.id.notchContainer)
        notchCard = overlayView.findViewById(R.id.notchCard)
        detachedBubble = overlayView.findViewById(R.id.detachedBubble)
        tvBubbleIcon = overlayView.findViewById(R.id.tvBubbleIcon)

        // Compact Views
        compactPet = overlayView.findViewById(R.id.compactPet)
        compactMusic = overlayView.findViewById(R.id.compactMusic)
        compactPomo = overlayView.findViewById(R.id.compactPomo)
        compactCharging = overlayView.findViewById(R.id.compactCharging)
        compactCall = overlayView.findViewById(R.id.compactCall)
        leftEye = overlayView.findViewById(R.id.leftEye)
        rightEye = overlayView.findViewById(R.id.rightEye)
        cameraSpace = overlayView.findViewById(R.id.cameraSpace)
        eyesContainer = overlayView.findViewById(R.id.eyesContainer)

        compactMusicCutout = overlayView.findViewById(R.id.compactMusicCutout)
        compactPomoCutout = overlayView.findViewById(R.id.compactPomoCutout)
        compactChargeCutout = overlayView.findViewById(R.id.compactChargeCutout)
        compactCallCutout = overlayView.findViewById(R.id.compactCallCutout)

        tvCompactPomoTime = overlayView.findViewById(R.id.tvCompactPomoTime)
        tvCompactChargePercent = overlayView.findViewById(R.id.tvCompactChargePercent)
        tvCompactCallTime = overlayView.findViewById(R.id.tvCompactCallTime)

        // Equalizer Bars
        eqBar1 = overlayView.findViewById(R.id.eqBar1)
        eqBar2 = overlayView.findViewById(R.id.eqBar2)
        eqBar3 = overlayView.findViewById(R.id.eqBar3)
        eqBar4 = overlayView.findViewById(R.id.eqBar4)

        // Expanded Presentation Views
        layerMusic = overlayView.findViewById(R.id.layerMusic)
        layerPomo = overlayView.findViewById(R.id.layerPomo)
        layerCharging = overlayView.findViewById(R.id.layerCharging)
        layerCall = overlayView.findViewById(R.id.layerCall)

        // Preferences
        val prefs = getSharedPreferences("nusa_prefs", Context.MODE_PRIVATE)
        yOffsetDp = prefs.getInt("y_offset", 4)
        collapsedWidthDp = prefs.getInt("notch_width", 138)
        collapsedHeightDp = prefs.getInt("notch_height", 46)
        eyeSpacingDp = prefs.getInt("eye_spacing", 72)
        eyeYOffsetDp = prefs.getInt("eye_y_offset", 0)
        eyeSizeDp = prefs.getInt("eye_size", 10)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Hitbox extends past 122px HyperOS status bar to guarantee 100% touch delivery
        val initialW = dpToPx(maxOf(compactActivityWidthDp + 24, 180))
        val initialH = dpToPx(maxOf(collapsedHeightDp + 26, 72))

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
        showCompact(compactPet)

        // Apple Dynamic Island Gesture Detection
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!isExpanded) {
                    expandCurrentState()
                    return true
                }
                return false
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!isExpanded) {
                    expandCurrentState()
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
                        expandCurrentState()
                        return true
                    } else if (dy < -25 && isExpanded) {
                        collapseToCompact()
                        return true
                    }
                }
                return false
            }
        })

        // Tap outside collapses expanded card; otherwise dispatch through gestureDetector
        overlayView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                if (isExpanded) {
                    collapseToCompact()
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
                expandCurrentState()
            }
        }

        // Secondary detached bubble interaction
        detachedBubble.setOnClickListener {
            if (isMinimalMode) {
                // Switch between primary and secondary activity
                setNotchState(NotchState.POMODORO)
            }
        }

        windowManager.addView(overlayView, params)
    }

    private fun applyEyeAndNotchConfig() {
        params.y = dpToPx(yOffsetDp)

        // 1. Cutout Space across all presentations
        listOf(cameraSpace, compactMusicCutout, compactPomoCutout, compactChargeCutout, compactCallCutout).forEach { space ->
            space?.let {
                val lp = it.layoutParams
                lp.width = dpToPx(eyeSpacingDp)
                it.layoutParams = lp
            }
        }

        // 2. Vertical position of eyes
        eyesContainer?.translationY = dpToPx(eyeYOffsetDp).toFloat()

        // 3. Eye dimensions
        val eyeW = dpToPx(eyeSizeDp)
        val eyeH = dpToPx((eyeSizeDp * 1.5).toInt())
        val leftLp = leftEye.layoutParams
        leftLp.width = eyeW
        leftLp.height = eyeH
        leftEye.layoutParams = leftLp

        val rightLp = rightEye.layoutParams
        rightLp.width = eyeW
        rightLp.height = eyeH
        rightEye.layoutParams = rightLp

        if (!isExpanded) {
            updateCompactDimensions()
        }

        try {
            windowManager.updateViewLayout(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateCompactDimensions() {
        val targetWidth = when (currentState) {
            NotchState.PET -> collapsedWidthDp
            NotchState.MINIMAL -> compactActivityWidthDp
            else -> compactActivityWidthDp
        }

        val lp = notchCard.layoutParams
        lp.width = dpToPx(targetWidth)
        lp.height = dpToPx(collapsedHeightDp)
        notchCard.layoutParams = lp
        notchCard.radius = dpToPx(collapsedHeightDp / 2).toFloat()

        if (isMinimalMode) {
            detachedBubble.visibility = View.VISIBLE
            params.width = dpToPx(300)
        } else {
            detachedBubble.visibility = View.GONE
            params.width = dpToPx(maxOf(targetWidth + 24, 180))
        }
        params.height = dpToPx(maxOf(collapsedHeightDp + 26, 72))
    }

    private fun initMusicPlayerControls() {
        tvMusicTitle = overlayView.findViewById(R.id.tvMusicTitle)
        tvMusicArtist = overlayView.findViewById(R.id.tvMusicArtist)
        tvTimeCurrent = overlayView.findViewById(R.id.tvTimeCurrent)
        tvTimeRemaining = overlayView.findViewById(R.id.tvTimeRemaining)
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

        btnCloseMusic.setOnClickListener {
            collapseToCompact()
        }
    }

    private fun initPomoControls() {
        tvPomoCountdown = overlayView.findViewById(R.id.tvPomoCountdown)
        btnPomoToggle = overlayView.findViewById(R.id.btnPomoToggle)
        btnPomoReset = overlayView.findViewById(R.id.btnPomoReset)
        btnClosePomo = overlayView.findViewById(R.id.btnClosePomo)

        btnPomoToggle.setOnClickListener {
            isPomoRunning = !isPomoRunning
            btnPomoToggle.text = if (isPomoRunning) "Tạm dừng" else "Tiếp tục"
        }

        btnPomoReset.setOnClickListener {
            pomoSeconds = 25 * 60
            isPomoRunning = true
            btnPomoToggle.text = "Tạm dừng"
        }

        btnClosePomo.setOnClickListener {
            collapseToCompact()
        }
    }

    private fun initCallControls() {
        btnAcceptCall = overlayView.findViewById(R.id.btnAcceptCall)
        btnDeclineCall = overlayView.findViewById(R.id.btnDeclineCall)
        btnCloseCall = overlayView.findViewById(R.id.btnCloseCall)

        btnAcceptCall.setOnClickListener {
            setNotchState(NotchState.PET)
        }

        btnDeclineCall.setOnClickListener {
            setNotchState(NotchState.PET)
        }

        btnCloseCall.setOnClickListener {
            collapseToCompact()
        }
    }

    private fun updateMusicDisplay() {
        val song = playlist[playlistIndex]
        tvMusicTitle.text = song.title
        tvMusicArtist.text = song.artist
        btnPlayPause.text = if (isPlaying) "⏸" else "▶"
    }

    fun setNotchState(state: NotchState) {
        currentState = state
        isMinimalMode = (state == NotchState.MINIMAL)

        when (state) {
            NotchState.PET -> {
                collapseToCompact()
                showCompact(compactPet)
            }
            NotchState.MUSIC -> {
                updateMusicDisplay()
                collapseToCompact()
                showCompact(compactMusic)
            }
            NotchState.POMODORO -> {
                collapseToCompact()
                showCompact(compactPomo)
            }
            NotchState.CHARGING -> {
                showCompact(compactCharging)
                // Expand briefly like Apple charging HUD, then collapse
                expandCurrentState()
                mainHandler.postDelayed({
                    if (currentState == NotchState.CHARGING && isExpanded) {
                        collapseToCompact()
                    }
                }, 3500)
            }
            NotchState.CALL -> {
                showCompact(compactCall)
                expandCurrentState()
            }
            NotchState.MINIMAL -> {
                updateMusicDisplay()
                collapseToCompact()
                showCompact(compactMusic)
                detachedBubble.visibility = View.VISIBLE
                tvBubbleIcon.text = "⏱️"
            }
        }
    }

    private fun showCompact(activeCompact: View) {
        listOf(compactPet, compactMusic, compactPomo, compactCharging, compactCall).forEach {
            it.visibility = if (it == activeCompact) View.VISIBLE else View.GONE
        }
        listOf(layerMusic, layerPomo, layerCharging, layerCall).forEach {
            it.visibility = View.GONE
        }
    }

    private fun showExpanded(activeExpanded: View) {
        listOf(layerMusic, layerPomo, layerCharging, layerCall).forEach {
            it.visibility = if (it == activeExpanded) View.VISIBLE else View.GONE
        }
        listOf(compactPet, compactMusic, compactPomo, compactCharging, compactCall).forEach {
            it.visibility = View.GONE
        }
    }

    private fun expandCurrentState() {
        isExpanded = true
        when (currentState) {
            NotchState.PET -> {
                updateMusicDisplay()
                showExpanded(layerMusic)
            }
            NotchState.MUSIC, NotchState.MINIMAL -> {
                updateMusicDisplay()
                showExpanded(layerMusic)
            }
            NotchState.POMODORO -> {
                showExpanded(layerPomo)
            }
            NotchState.CHARGING -> {
                showExpanded(layerCharging)
            }
            NotchState.CALL -> {
                showExpanded(layerCall)
            }
        }

        // Hide detached bubble during expansion
        detachedBubble.visibility = View.GONE

        val targetW = dpToPx(expandedWidthDp)
        val targetH = dpToPx(expandedHeightDp)

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
            dpToPx(collapsedHeightDp / 2), dpToPx(30),
            null
        )
    }

    private fun collapseToCompact() {
        isExpanded = false
        val targetWidthDp = when (currentState) {
            NotchState.PET -> collapsedWidthDp
            NotchState.MINIMAL -> compactActivityWidthDp
            else -> compactActivityWidthDp
        }

        val targetW = dpToPx(targetWidthDp)
        val targetH = dpToPx(collapsedHeightDp)

        animateCardSize(
            notchCard.width, targetW,
            notchCard.height, targetH,
            dpToPx(30), dpToPx(collapsedHeightDp / 2)
        ) {
            updateCompactDimensions()
            try {
                windowManager.updateViewLayout(overlayView, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        when (currentState) {
            NotchState.PET -> showCompact(compactPet)
            NotchState.MUSIC -> showCompact(compactMusic)
            NotchState.POMODORO -> showCompact(compactPomo)
            NotchState.CHARGING -> showCompact(compactCharging)
            NotchState.CALL -> showCompact(compactCall)
            NotchState.MINIMAL -> {
                showCompact(compactMusic)
                detachedBubble.visibility = View.VISIBLE
            }
        }
    }

    private fun animateCardSize(
        fromW: Int, toW: Int,
        fromH: Int, toH: Int,
        fromRadius: Int, toRadius: Int,
        onEnd: (() -> Unit)?
    ) {
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 280
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
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onEnd?.invoke()
            }
        })
        animator.start()
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
