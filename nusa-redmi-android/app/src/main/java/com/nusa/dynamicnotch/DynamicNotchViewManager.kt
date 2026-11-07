package com.nusa.dynamicnotch

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import kotlin.math.abs

@SuppressLint("StaticFieldLeak")
object DynamicNotchViewManager {

    private var currentContext: Context? = null
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null

    var isAttached = false
        private set
    var isAccessibilityMode = false
        private set

    private var currentState: NotchState = NotchState.PET
    private var isExpanded: Boolean = false
    private var isMinimalMode: Boolean = false

    // Dimensions in dp
    private var collapsedWidthDp: Int = 138
    private var collapsedHeightDp: Int = 46
    private var compactActivityWidthDp: Int = 168
    private var expandedWidthDp: Int = 380
    private var expandedHeightDp: Int = 195
    private var yOffsetDp: Int = 4
    private var eyeSpacingDp: Int = 72
    private var eyeYOffsetDp: Int = 0
    private var eyeSizeDp: Int = 10

    // Views
    private var notchContainer: View? = null
    private var notchCard: CardView? = null
    private var detachedBubble: CardView? = null
    private var tvBubbleIcon: TextView? = null

    // Compact views
    private var compactPet: View? = null
    private var compactMusic: View? = null
    private var compactPomo: View? = null
    private var compactCharging: View? = null
    private var compactCall: View? = null
    private var leftEye: View? = null
    private var rightEye: View? = null
    private var cameraSpace: View? = null
    private var eyesContainer: View? = null

    private var compactMusicCutout: View? = null
    private var compactPomoCutout: View? = null
    private var compactChargeCutout: View? = null
    private var compactCallCutout: View? = null

    private var tvCompactPomoTime: TextView? = null
    private var tvCompactChargePercent: TextView? = null
    private var tvCompactCallTime: TextView? = null

    // Equalizer bars
    private var eqBar1: View? = null
    private var eqBar2: View? = null
    private var eqBar3: View? = null
    private var eqBar4: View? = null

    // Expanded views
    private var layerMusic: View? = null
    private var layerPomo: View? = null
    private var layerCharging: View? = null
    private var layerCall: View? = null

    // Music controls
    private var tvMusicTitle: TextView? = null
    private var tvMusicArtist: TextView? = null
    private var tvTimeCurrent: TextView? = null
    private var tvTimeRemaining: TextView? = null
    private var musicProgressBar: ProgressBar? = null
    private var btnPlayPause: TextView? = null
    private var btnPrev: TextView? = null
    private var btnNext: TextView? = null
    private var btnCloseMusic: TextView? = null

    // Pomodoro controls
    private var tvPomoTime: TextView? = null
    private var tvPomoRound: TextView? = null
    private var pomoProgressBar: ProgressBar? = null
    private var btnPomoToggle: TextView? = null
    private var btnPomoReset: TextView? = null
    private var btnClosePomo: TextView? = null

    // Call controls
    private var btnAcceptCall: TextView? = null
    private var btnDeclineCall: TextView? = null
    private var btnCloseCall: TextView? = null

    // Runnables & State
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isPlaying = true
    private var progressValue = 125
    private val totalDuration = 220
    private var pomoSeconds = 25 * 60
    private var isPomoRunning = true
    private var callSeconds = 42

    private val songs = listOf(
        Pair("Paper Sky", "Lumen Coast"),
        Pair("Starry Night", "Peggy Gou"),
        Pair("Midnight City", "M83"),
        Pair("Golden Hour", "JVKE")
    )
    private var currentSongIdx = 0

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (isPlaying) {
                progressValue++
                if (progressValue > totalDuration) progressValue = 0
                musicProgressBar?.progress = progressValue
                val curMin = progressValue / 60
                val curSec = progressValue % 60
                val remTotal = totalDuration - progressValue
                val remMin = remTotal / 60
                val remSec = remTotal % 60
                tvTimeCurrent?.text = String.format("%d:%02d", curMin, curSec)
                tvTimeRemaining?.text = String.format("-%d:%02d", remMin, remSec)
            }
            mainHandler.postDelayed(this, 1000)
        }
    }

    private val pomoRunnable = object : Runnable {
        override fun run() {
            if (isPomoRunning && pomoSeconds > 0) {
                pomoSeconds--
                val min = pomoSeconds / 60
                val sec = pomoSeconds % 60
                val timeStr = String.format("%02d:%02d", min, sec)
                tvPomoTime?.text = timeStr
                tvCompactPomoTime?.text = timeStr
                pomoProgressBar?.progress = ((25 * 60 - pomoSeconds) * 100) / (25 * 60)
            }
            mainHandler.postDelayed(this, 1000)
        }
    }

    private val callRunnable = object : Runnable {
        override fun run() {
            callSeconds++
            val min = callSeconds / 60
            val sec = callSeconds % 60
            tvCompactCallTime?.text = String.format("%02d:%02d", min, sec)
            mainHandler.postDelayed(this, 1000)
        }
    }

    private val eqRunnable = object : Runnable {
        private val heights = listOf(
            intArrayOf(16, 26, 12, 22),
            intArrayOf(24, 14, 28, 16),
            intArrayOf(12, 28, 18, 24),
            intArrayOf(28, 18, 14, 26)
        )
        private var frame = 0

        override fun run() {
            if (compactMusic?.visibility == View.VISIBLE || isMinimalMode) {
                val currentFrame = heights[frame % heights.size]
                val bars = listOf(eqBar1, eqBar2, eqBar3, eqBar4)
                bars.forEachIndexed { i, bar ->
                    val lp = bar?.layoutParams
                    if (lp != null) {
                        lp.height = dpToPx(currentFrame[i] / 2)
                        bar.layoutParams = lp
                    }
                }
                frame++
            }
            mainHandler.postDelayed(this, 130)
        }
    }

    private val blinkRunnable = object : Runnable {
        override fun run() {
            if (currentState == NotchState.PET && !isExpanded) {
                leftEye?.animate()?.scaleY(0.1f)?.setDuration(120)?.withEndAction {
                    leftEye?.animate()?.scaleY(1f)?.setDuration(120)?.start()
                }?.start()
                rightEye?.animate()?.scaleY(0.1f)?.setDuration(120)?.withEndAction {
                    rightEye?.animate()?.scaleY(1f)?.setDuration(120)?.start()
                }?.start()
            }
            val nextDelay = (2500..5000).random().toLong()
            mainHandler.postDelayed(this, nextDelay)
        }
    }

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                NusaOverlayService.ACTION_SET_STATE -> {
                    val stateStr = intent.getStringExtra("state") ?: "PET"
                    val targetState = try {
                        NotchState.valueOf(stateStr)
                    } catch (e: Exception) {
                        NotchState.PET
                    }
                    setNotchState(targetState)
                }
                NusaOverlayService.ACTION_UPDATE_EVENT -> {
                    val type = intent.getStringExtra("event_type") ?: ""
                    when (type) {
                        "MUSIC_PLAY" -> {
                            val title = intent.getStringExtra("title") ?: "Now Playing"
                            val artist = intent.getStringExtra("artist") ?: "Artist"
                            tvMusicTitle?.text = title
                            tvMusicArtist?.text = artist
                            setNotchState(NotchState.MUSIC)
                        }
                        "POMO_START" -> setNotchState(NotchState.POMODORO)
                        "CHARGING" -> setNotchState(NotchState.CHARGING)
                        "CALL_INCOMING" -> setNotchState(NotchState.CALL)
                        "PET" -> setNotchState(NotchState.PET)
                    }
                }
                NusaOverlayService.ACTION_UPDATE_CONFIG -> {
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

    fun attach(context: Context, asAccessibility: Boolean) {
        if (isAttached) {
            if (isAccessibilityMode && !asAccessibility) {
                // Already running with higher-priority accessibility overlay
                return
            }
            detach()
        }

        currentContext = context
        isAccessibilityMode = asAccessibility
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val inflater = LayoutInflater.from(context)
        overlayView = inflater.inflate(R.layout.view_nusa_notch, null)

        notchContainer = overlayView?.findViewById(R.id.notchContainer)
        notchCard = overlayView?.findViewById(R.id.notchCard)
        detachedBubble = overlayView?.findViewById(R.id.detachedBubble)
        tvBubbleIcon = overlayView?.findViewById(R.id.tvBubbleIcon)

        // Compact views
        compactPet = overlayView?.findViewById(R.id.compactPet)
        compactMusic = overlayView?.findViewById(R.id.compactMusic)
        compactPomo = overlayView?.findViewById(R.id.compactPomo)
        compactCharging = overlayView?.findViewById(R.id.compactCharging)
        compactCall = overlayView?.findViewById(R.id.compactCall)
        leftEye = overlayView?.findViewById(R.id.leftEye)
        rightEye = overlayView?.findViewById(R.id.rightEye)
        cameraSpace = overlayView?.findViewById(R.id.cameraSpace)
        eyesContainer = overlayView?.findViewById(R.id.eyesContainer)

        compactMusicCutout = overlayView?.findViewById(R.id.compactMusicCutout)
        compactPomoCutout = overlayView?.findViewById(R.id.compactPomoCutout)
        compactChargeCutout = overlayView?.findViewById(R.id.compactChargeCutout)
        compactCallCutout = overlayView?.findViewById(R.id.compactCallCutout)

        tvCompactPomoTime = overlayView?.findViewById(R.id.tvCompactPomoTime)
        tvCompactChargePercent = overlayView?.findViewById(R.id.tvCompactChargePercent)
        tvCompactCallTime = overlayView?.findViewById(R.id.tvCompactCallTime)

        // Equalizer Bars
        eqBar1 = overlayView?.findViewById(R.id.eqBar1)
        eqBar2 = overlayView?.findViewById(R.id.eqBar2)
        eqBar3 = overlayView?.findViewById(R.id.eqBar3)
        eqBar4 = overlayView?.findViewById(R.id.eqBar4)

        // Expanded Presentation Views
        layerMusic = overlayView?.findViewById(R.id.layerMusic)
        layerPomo = overlayView?.findViewById(R.id.layerPomo)
        layerCharging = overlayView?.findViewById(R.id.layerCharging)
        layerCall = overlayView?.findViewById(R.id.layerCall)

        // Preferences
        val prefs = context.getSharedPreferences("nusa_prefs", Context.MODE_PRIVATE)
        yOffsetDp = prefs.getInt("y_offset", 4)
        collapsedWidthDp = prefs.getInt("notch_width", 138)
        collapsedHeightDp = prefs.getInt("notch_height", 46)
        eyeSpacingDp = prefs.getInt("eye_spacing", 72)
        eyeYOffsetDp = prefs.getInt("eye_y_offset", 0)
        eyeSizeDp = prefs.getInt("eye_size", 10)

        // Window Layout Flags & Type
        val layoutType = if (asAccessibility) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY // Layer 161,000 (ABOVE StatusBar 151,000!)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val initialW = dpToPx(maxOf(compactActivityWidthDp + 24, 180))
        val initialH = dpToPx(maxOf(collapsedHeightDp + 26, 72))

        params = WindowManager.LayoutParams(
            initialW,
            initialH,
            layoutType,
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

        initMusicPlayerControls()
        initPomoControls()
        initCallControls()

        // ULTRA-RESPONSIVE DIRECT TOUCH DETECTION (Zero lag, no cancel on micro-scrolls)
        setupTouchInteraction()

        try {
            windowManager?.addView(overlayView, params)
            isAttached = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        registerReceivers(context)

        mainHandler.post(progressRunnable)
        mainHandler.post(pomoRunnable)
        mainHandler.post(callRunnable)
        mainHandler.post(eqRunnable)
        mainHandler.postDelayed(blinkRunnable, 2000)
    }

    fun detach() {
        if (!isAttached) return
        try {
            unregisterReceivers()
            mainHandler.removeCallbacksAndMessages(null)
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isAttached = false
            overlayView = null
            windowManager = null
            currentContext = null
        }
    }

    private fun registerReceivers(context: Context) {
        val filter = IntentFilter().apply {
            addAction(NusaOverlayService.ACTION_SET_STATE)
            addAction(NusaOverlayService.ACTION_UPDATE_EVENT)
            addAction(NusaOverlayService.ACTION_UPDATE_CONFIG)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(eventReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(eventReceiver, filter)
        }
    }

    private fun unregisterReceivers() {
        try {
            currentContext?.unregisterReceiver(eventReceiver)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun setupTouchInteraction() {
        var startX = 0f
        var startY = 0f
        var startTime = 0L

        overlayView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_OUTSIDE -> {
                    if (isExpanded) {
                        collapseToCompact()
                    }
                    true
                }
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = abs(event.rawX - startX)
                    val dy = event.rawY - startY
                    val duration = System.currentTimeMillis() - startTime

                    if (dy > 30 && !isExpanded) {
                        // Swipe down -> Expand
                        expandCurrentState()
                        true
                    } else if (dy < -30 && isExpanded) {
                        // Swipe up -> Collapse
                        collapseToCompact()
                        true
                    } else if (dx < 60 && abs(dy) < 60 && duration < 600) {
                        // Direct Tap on Notch
                        if (!isExpanded) {
                            expandCurrentState()
                        }
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }

        // Secondary detached bubble interaction
        detachedBubble?.setOnClickListener {
            if (isMinimalMode) {
                setNotchState(NotchState.POMODORO)
            }
        }

        // Click directly on notch card
        notchCard?.setOnClickListener {
            if (!isExpanded) {
                expandCurrentState()
            }
        }
    }

    private fun applyEyeAndNotchConfig() {
        params?.y = dpToPx(yOffsetDp)

        // Eye spacing around Redmi punch-hole
        cameraSpace?.layoutParams = cameraSpace?.layoutParams?.apply {
            width = dpToPx(eyeSpacingDp)
        }
        compactMusicCutout?.layoutParams = compactMusicCutout?.layoutParams?.apply {
            width = dpToPx(eyeSpacingDp)
        }
        compactPomoCutout?.layoutParams = compactPomoCutout?.layoutParams?.apply {
            width = dpToPx(eyeSpacingDp)
        }
        compactChargeCutout?.layoutParams = compactChargeCutout?.layoutParams?.apply {
            width = dpToPx(eyeSpacingDp)
        }
        compactCallCutout?.layoutParams = compactCallCutout?.layoutParams?.apply {
            width = dpToPx(eyeSpacingDp)
        }

        // Eye size
        val sizePx = dpToPx(eyeSizeDp)
        leftEye?.layoutParams = leftEye?.layoutParams?.apply {
            width = sizePx
            height = sizePx
        }
        rightEye?.layoutParams = rightEye?.layoutParams?.apply {
            width = sizePx
            height = sizePx
        }

        // Eye vertical offset
        eyesContainer?.translationY = dpToPx(eyeYOffsetDp).toFloat()

        if (!isExpanded) {
            updateCompactDimensions()
        }

        try {
            if (isAttached && overlayView != null && params != null) {
                windowManager?.updateViewLayout(overlayView, params)
            }
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

        val lp = notchCard?.layoutParams
        if (lp != null) {
            lp.width = dpToPx(targetWidth)
            lp.height = dpToPx(collapsedHeightDp)
            notchCard?.layoutParams = lp
        }
        notchCard?.radius = dpToPx(collapsedHeightDp / 2).toFloat()

        if (isMinimalMode) {
            detachedBubble?.visibility = View.VISIBLE
            params?.width = dpToPx(300)
        } else {
            detachedBubble?.visibility = View.GONE
            params?.width = dpToPx(maxOf(targetWidth + 24, 180))
        }
        params?.height = dpToPx(maxOf(collapsedHeightDp + 26, 72))
    }

    private fun initMusicPlayerControls() {
        tvMusicTitle = overlayView?.findViewById(R.id.tvMusicTitle)
        tvMusicArtist = overlayView?.findViewById(R.id.tvMusicArtist)
        tvTimeCurrent = overlayView?.findViewById(R.id.tvTimeCurrent)
        tvTimeRemaining = overlayView?.findViewById(R.id.tvTimeRemaining)
        musicProgressBar = overlayView?.findViewById(R.id.musicProgressBar)
        btnPlayPause = overlayView?.findViewById(R.id.btnPlayPause)
        btnPrev = overlayView?.findViewById(R.id.btnPrev)
        btnNext = overlayView?.findViewById(R.id.btnNext)
        btnCloseMusic = overlayView?.findViewById(R.id.btnCloseMusic)

        updateMusicDisplay()

        btnPlayPause?.setOnClickListener {
            isPlaying = !isPlaying
            btnPlayPause?.text = if (isPlaying) "⏸" else "▶"
        }

        btnNext?.setOnClickListener {
            currentSongIdx = (currentSongIdx + 1) % songs.size
            progressValue = 0
            musicProgressBar?.progress = 0
            updateMusicDisplay()
        }

        btnPrev?.setOnClickListener {
            currentSongIdx = if (currentSongIdx - 1 < 0) songs.size - 1 else currentSongIdx - 1
            progressValue = 0
            musicProgressBar?.progress = 0
            updateMusicDisplay()
        }

        btnCloseMusic?.setOnClickListener {
            collapseToCompact()
        }
    }

    private fun initPomoControls() {
        tvPomoTime = overlayView?.findViewById(R.id.tvPomoTime)
        tvPomoRound = overlayView?.findViewById(R.id.tvPomoRound)
        pomoProgressBar = overlayView?.findViewById(R.id.pomoProgressBar)
        btnPomoToggle = overlayView?.findViewById(R.id.btnPomoToggle)
        btnPomoReset = overlayView?.findViewById(R.id.btnPomoReset)
        btnClosePomo = overlayView?.findViewById(R.id.btnClosePomo)

        btnPomoToggle?.setOnClickListener {
            isPomoRunning = !isPomoRunning
            btnPomoToggle?.text = if (isPomoRunning) "Tạm dừng" else "Tiếp tục"
        }

        btnPomoReset?.setOnClickListener {
            pomoSeconds = 25 * 60
            isPomoRunning = true
            btnPomoToggle?.text = "Tạm dừng"
        }

        btnClosePomo?.setOnClickListener {
            collapseToCompact()
        }
    }

    private fun initCallControls() {
        btnAcceptCall = overlayView?.findViewById(R.id.btnAcceptCall)
        btnDeclineCall = overlayView?.findViewById(R.id.btnDeclineCall)
        btnCloseCall = overlayView?.findViewById(R.id.btnCloseCall)

        btnAcceptCall?.setOnClickListener {
            setNotchState(NotchState.PET)
        }

        btnDeclineCall?.setOnClickListener {
            setNotchState(NotchState.PET)
        }

        btnCloseCall?.setOnClickListener {
            collapseToCompact()
        }
    }

    private fun updateMusicDisplay() {
        val (title, artist) = songs[currentSongIdx]
        tvMusicTitle?.text = title
        tvMusicArtist?.text = artist
    }

    fun setNotchState(state: NotchState) {
        currentState = state
        isMinimalMode = (state == NotchState.MINIMAL)

        when (state) {
            NotchState.PET -> {
                collapseToCompact()
                showCompact(compactPet)
                detachedBubble?.visibility = View.GONE
            }
            NotchState.MUSIC -> {
                updateMusicDisplay()
                collapseToCompact()
                showCompact(compactMusic)
                detachedBubble?.visibility = View.GONE
            }
            NotchState.POMODORO -> {
                collapseToCompact()
                showCompact(compactPomo)
                detachedBubble?.visibility = View.GONE
            }
            NotchState.CHARGING -> {
                showExpanded(layerCharging)
                expandCurrentState()
                mainHandler.postDelayed({
                    if (currentState == NotchState.CHARGING) {
                        collapseToCompact()
                        showCompact(compactCharging)
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
                detachedBubble?.visibility = View.VISIBLE
                tvBubbleIcon?.text = "⏱️"
            }
        }
    }

    private fun showCompact(activeCompact: View?) {
        listOf(compactPet, compactMusic, compactPomo, compactCharging, compactCall).forEach {
            it?.visibility = if (it == activeCompact) View.VISIBLE else View.GONE
        }
        listOf(layerMusic, layerPomo, layerCharging, layerCall).forEach {
            it?.visibility = View.GONE
        }
    }

    private fun showExpanded(activeExpanded: View?) {
        listOf(layerMusic, layerPomo, layerCharging, layerCall).forEach {
            it?.visibility = if (it == activeExpanded) View.VISIBLE else View.GONE
        }
        listOf(compactPet, compactMusic, compactPomo, compactCharging, compactCall).forEach {
            it?.visibility = View.GONE
        }
    }

    fun expandCurrentState() {
        isExpanded = true
        when (currentState) {
            NotchState.PET -> {
                // Expanding Pet reveals quick music playback & controls
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
        detachedBubble?.visibility = View.GONE

        val targetW = dpToPx(expandedWidthDp)
        val targetH = dpToPx(expandedHeightDp)

        params?.width = targetW
        params?.height = targetH + dpToPx(30)

        try {
            if (isAttached && overlayView != null && params != null) {
                windowManager?.updateViewLayout(overlayView, params)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        animateCardSize(targetW, targetH, 28)
    }

    fun collapseToCompact() {
        isExpanded = false
        updateCompactDimensions()

        when (currentState) {
            NotchState.PET -> showCompact(compactPet)
            NotchState.MUSIC -> showCompact(compactMusic)
            NotchState.POMODORO -> showCompact(compactPomo)
            NotchState.CHARGING -> showCompact(compactCharging)
            NotchState.CALL -> showCompact(compactCall)
            NotchState.MINIMAL -> {
                showCompact(compactMusic)
                detachedBubble?.visibility = View.VISIBLE
            }
        }

        val targetW = dpToPx(if (currentState == NotchState.PET) collapsedWidthDp else compactActivityWidthDp)
        val targetH = dpToPx(collapsedHeightDp)
        val targetCorner = collapsedHeightDp / 2

        animateCardSize(targetW, targetH, targetCorner) {
            try {
                if (isAttached && overlayView != null && params != null) {
                    windowManager?.updateViewLayout(overlayView, params)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun animateCardSize(
        targetWidthPx: Int,
        targetHeightPx: Int,
        targetCornerRadiusDp: Int,
        onEnd: (() -> Unit)? = null
    ) {
        val currentWidth = notchCard?.width ?: targetWidthPx
        val currentHeight = notchCard?.height ?: targetHeightPx
        val currentRadius = notchCard?.radius ?: dpToPx(targetCornerRadiusDp).toFloat()
        val targetRadius = dpToPx(targetCornerRadiusDp).toFloat()

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320
            interpolator = OvershootInterpolator(0.85f)
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                val w = (currentWidth + (targetWidthPx - currentWidth) * fraction).toInt()
                val h = (currentHeight + (targetHeightPx - currentHeight) * fraction).toInt()
                val r = currentRadius + (targetRadius - currentRadius) * fraction

                val lp = notchCard?.layoutParams
                if (lp != null) {
                    lp.width = w
                    lp.height = h
                    notchCard?.layoutParams = lp
                }
                notchCard?.radius = r
            }
        }
        anim.start()
        anim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onEnd?.invoke()
            }
        })
    }

    private fun dpToPx(dp: Int): Int {
        val density = currentContext?.resources?.displayMetrics?.density ?: 3f
        return (dp * density).toInt()
    }
}
