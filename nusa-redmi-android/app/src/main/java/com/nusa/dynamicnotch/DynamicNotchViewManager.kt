package com.nusa.dynamicnotch

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.palette.graphics.Palette
import com.nusa.dynamicnotch.core.fsm.IslandPriorityManager
import com.nusa.dynamicnotch.core.gestures.IslandGesture
import com.nusa.dynamicnotch.core.gestures.IslandGestureClassifier
import com.nusa.dynamicnotch.core.haptics.NusaHapticEngine
import com.nusa.dynamicnotch.models.NotchState
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

    private var hapticEngine: NusaHapticEngine? = null
    private val priorityManager = IslandPriorityManager { _, newState ->
        applyStateTransition(newState)
    }

    val currentState: NotchState
        get() = priorityManager.currentState

    val previousState: NotchState
        get() = priorityManager.baselineState

    private var isExpanded: Boolean = false
    private var isMinimalMode: Boolean = false

    // Dimensions in dp (Calibrated specifically for Redmi Note 13 Pro 5G)
    private var collapsedWidthDp: Int = 140
    private var collapsedHeightDp: Int = 36
    private var compactActivityWidthDp: Int = 156
    private var expandedWidthDp: Int = 376
    private var expandedHeightDp: Int = 195
    private var yOffsetDp: Int = 2
    private var eyeSpacingDp: Int = 68
    private var eyeYOffsetDp: Int = 0
    private var eyeSizeDp: Int = 10

    // Root views
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
    private var compactNotification: View? = null
    private var compactRinger: View? = null

    private var leftEye: View? = null
    private var rightEye: View? = null
    private var cameraSpace: View? = null
    private var eyesContainer: View? = null

    private var compactMusicCutout: View? = null
    private var compactPomoCutout: View? = null
    private var compactChargeCutout: View? = null
    private var compactCallCutout: View? = null
    private var compactNotifCutout: View? = null
    private var compactRingerCutout: View? = null

    private var ivCompactNotifIcon: ImageView? = null
    private var tvCompactNotifBadge: TextView? = null
    private var tvRingerIcon: TextView? = null
    private var tvRingerText: TextView? = null

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
    private var layerNotification: View? = null

    // Music controls
    private var tvOutputRoute: TextView? = null
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
    private var btnPomoToggle: TextView? = null
    private var btnPomoReset: TextView? = null
    private var btnClosePomo: TextView? = null

    // Call controls
    private var btnAcceptCall: TextView? = null
    private var btnDeclineCall: TextView? = null
    private var btnCloseCall: TextView? = null

    // Notification controls
    private var tvNotifAppBadge: TextView? = null
    private var ivNotifLargeIcon: ImageView? = null
    private var tvNotifSender: TextView? = null
    private var tvNotifBody: TextView? = null
    private var btnOpenNotif: Button? = null
    private var btnCloseNotif: TextView? = null
    private var currentNotifPendingIntent: PendingIntent? = null

    // Outline and Artwork views
    private var vIslandStroke: View? = null
    private var vBubbleStroke: View? = null
    private var cardCompactMusicArt: View? = null
    private var ivCompactMusicArt: ImageView? = null
    private var compactMusicIcon: TextView? = null
    private var ivMusicArtLarge: ImageView? = null
    private var tvMusicArt: TextView? = null
    private var btnCloseCharge: TextView? = null

    // Charging controls
    private var tvChargePercentage: TextView? = null

    // Runnables & State
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRealMediaActive = false
    private var activeMediaPackage: String? = null
    private var isPlaying = false
    private var progressValue = 0
    private var totalDuration = 0
    private var pomoSeconds = 25 * 60
    private var isPomoRunning = true
    private var callSeconds = 42

    private var audioManager: AudioManager? = null
    private var audioPlaybackCallback: AudioManager.AudioPlaybackCallback? = null
    private var isSystemAudioActive = false
    private val systemAudioStopRunnable = Runnable {
        if (!isRealMediaActive && currentState == NotchState.MUSIC) {
            if (isExpanded) collapseToCompact()
            setNotchState(NotchState.PET)
        }
    }

    private val pauseTimeoutRunnable = Runnable {
        if (currentState == NotchState.MUSIC && !isPlaying && !isExpanded) {
            setNotchState(NotchState.PET)
        }
    }

    private val dismissChargeRunnable = Runnable {
        if (currentState == NotchState.CHARGING) {
            collapseToCompact()
            showCompact(compactCharging)
            mainHandler.postDelayed({
                if (currentState == NotchState.CHARGING) {
                    priorityManager.dismissTransient(NotchState.CHARGING)
                }
            }, 2500)
        }
    }

    private val songs = listOf(
        Pair("Paper Sky", "Lumen Coast"),
        Pair("Starry Night", "Peggy Gou"),
        Pair("Midnight City", "M83"),
        Pair("Golden Hour", "JVKE")
    )
    private var currentSongIdx = 0

    // Apple Spring Curve (iOS 17 fluid spring physics)
    private val appleSpringInterpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && totalDuration > 0) {
                progressValue++
                if (progressValue > totalDuration) progressValue = totalDuration
                musicProgressBar?.progress = progressValue
                val curMin = progressValue / 60
                val curSec = progressValue % 60
                val remTotal = maxOf(0, totalDuration - progressValue)
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
            if ((compactMusic?.visibility == View.VISIBLE || isMinimalMode) && (isPlaying || isSystemAudioActive)) {
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
                leftEye?.animate()?.cancel()
                rightEye?.animate()?.cancel()
                leftEye?.animate()?.scaleY(0.1f)?.setDuration(110)?.withEndAction {
                    leftEye?.animate()?.scaleY(1f)?.setDuration(120)?.start()
                }?.start()
                rightEye?.animate()?.scaleY(0.1f)?.setDuration(110)?.withEndAction {
                    rightEye?.animate()?.scaleY(1f)?.setDuration(120)?.start()
                }?.start()
            } else {
                leftEye?.scaleY = 1.0f
                rightEye?.scaleY = 1.0f
            }
            val nextDelay = (2500..5000).random().toLong()
            mainHandler.postDelayed(this, nextDelay)
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val pct = if (scale > 0) (level * 100) / scale else level

                    tvCompactChargePercent?.text = "$pct%"
                    tvChargePercentage?.text = "$pct%"
                    // Note: Never trigger setNotchState(NotchState.CHARGING) from ACTION_BATTERY_CHANGED
                    // to prevent endless auto-reopening loops. Real charging starts via ACTION_POWER_CONNECTED.
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    performHaptic(NusaHapticEngine.FeedbackType.MEDIUM_CLICK)
                    setNotchState(NotchState.CHARGING)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    if (currentState == NotchState.CHARGING) {
                        mainHandler.removeCallbacks(dismissChargeRunnable)
                        priorityManager.dismissTransient(NotchState.CHARGING)
                    }
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    val mode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
                    showRingerHUD(mode)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    if (isExpanded) {
                        collapseToCompact()
                    }
                }
                android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE, android.bluetooth.BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
                    }
                    val deviceName = try {
                        device?.name ?: "Tai nghe Bluetooth"
                    } catch (e: SecurityException) {
                        "Tai nghe Bluetooth"
                    }
                    onNotificationReceived(
                        packageName = "android.bluetooth",
                        title = deviceName,
                        text = "Đã kết nối • Âm thanh nổi",
                        largeIcon = null,
                        pendingIntent = null
                    )
                }
                NusaOverlayService.ACTION_SET_STATE -> {
                    val stateStr = intent.getStringExtra("state") ?: "PET"
                    val targetState = try {
                        NotchState.valueOf(stateStr)
                    } catch (e: Exception) {
                        NotchState.PET
                    }
                    performHaptic()
                    setNotchState(targetState)
                }
                NusaOverlayService.ACTION_UPDATE_CONFIG -> {
                    collapsedWidthDp = intent.getIntExtra("notch_width", collapsedWidthDp)
                    collapsedHeightDp = intent.getIntExtra("notch_height", collapsedHeightDp)
                    eyeSpacingDp = intent.getIntExtra("eye_spacing", eyeSpacingDp)
                    eyeYOffsetDp = intent.getIntExtra("eye_y_offset", eyeYOffsetDp)
                    eyeSizeDp = intent.getIntExtra("eye_size", eyeSizeDp)
                    applyEyeAndNotchConfig()
                }
            }
        }
    }

    fun attach(context: Context, asAccessibility: Boolean) {
        if (isAttached) {
            if (isAccessibilityMode && !asAccessibility) {
                return
            }
            detach()
        }

        currentContext = context.applicationContext
        hapticEngine = NusaHapticEngine(context.applicationContext)
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
        compactNotification = overlayView?.findViewById(R.id.compactNotification)
        compactRinger = overlayView?.findViewById(R.id.compactRinger)

        leftEye = overlayView?.findViewById(R.id.leftEye)
        rightEye = overlayView?.findViewById(R.id.rightEye)
        cameraSpace = overlayView?.findViewById(R.id.cameraSpace)
        eyesContainer = overlayView?.findViewById(R.id.eyesContainer)

        compactMusicCutout = overlayView?.findViewById(R.id.compactMusicCutout)
        compactPomoCutout = overlayView?.findViewById(R.id.compactPomoCutout)
        compactChargeCutout = overlayView?.findViewById(R.id.compactChargeCutout)
        compactCallCutout = overlayView?.findViewById(R.id.compactCallCutout)
        compactNotifCutout = overlayView?.findViewById(R.id.compactNotifCutout)
        compactRingerCutout = overlayView?.findViewById(R.id.compactRingerCutout)

        ivCompactNotifIcon = overlayView?.findViewById(R.id.ivCompactNotifIcon)
        tvCompactNotifBadge = overlayView?.findViewById(R.id.tvCompactNotifBadge)
        tvRingerIcon = overlayView?.findViewById(R.id.tvRingerIcon)
        tvRingerText = overlayView?.findViewById(R.id.tvRingerText)

        tvCompactPomoTime = overlayView?.findViewById(R.id.tvCompactPomoTime)
        tvCompactChargePercent = overlayView?.findViewById(R.id.tvCompactChargePercent)
        tvCompactCallTime = overlayView?.findViewById(R.id.tvCompactCallTime)

        // Equalizer Bars
        eqBar1 = overlayView?.findViewById(R.id.eqBar1)
        eqBar2 = overlayView?.findViewById(R.id.eqBar2)
        eqBar3 = overlayView?.findViewById(R.id.eqBar3)
        eqBar4 = overlayView?.findViewById(R.id.eqBar4)

        // Expanded Views
        layerMusic = overlayView?.findViewById(R.id.layerMusic)
        layerPomo = overlayView?.findViewById(R.id.layerPomo)
        layerCharging = overlayView?.findViewById(R.id.layerCharging)
        layerCall = overlayView?.findViewById(R.id.layerCall)
        layerNotification = overlayView?.findViewById(R.id.layerNotification)

        // Apple Specular Outlines and Real Artwork
        vIslandStroke = overlayView?.findViewById(R.id.vIslandStroke)
        vBubbleStroke = overlayView?.findViewById(R.id.vBubbleStroke)
        cardCompactMusicArt = overlayView?.findViewById(R.id.cardCompactMusicArt)
        ivCompactMusicArt = overlayView?.findViewById(R.id.ivCompactMusicArt)
        compactMusicIcon = overlayView?.findViewById(R.id.compactMusicIcon)
        ivMusicArtLarge = overlayView?.findViewById(R.id.ivMusicArtLarge)
        tvMusicArt = overlayView?.findViewById(R.id.tvMusicArt)
        btnCloseCharge = overlayView?.findViewById(R.id.btnCloseCharge)

        btnCloseCharge?.setOnClickListener {
            performHaptic(NusaHapticEngine.FeedbackType.LIGHT_TICK)
            priorityManager.dismissTransient(NotchState.CHARGING)
        }

        // Preferences (Calibrated for Redmi Note 13 Pro 5G)
        val prefs = context.getSharedPreferences("nusa_prefs", Context.MODE_PRIVATE)
        yOffsetDp = prefs.getInt("y_offset", 2)
        collapsedWidthDp = prefs.getInt("notch_width", 140)
        collapsedHeightDp = prefs.getInt("notch_height", 36)
        eyeSpacingDp = prefs.getInt("eye_spacing", 68)
        eyeYOffsetDp = prefs.getInt("eye_y_offset", 0)
        eyeSizeDp = prefs.getInt("eye_size", 10)

        // Window Layout Flags & Type
        val layoutType = if (asAccessibility) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY // Layer 311,000 (Above System StatusBar)
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
        initNotificationControls()

        // 0ms delay direct touch interaction
        setupTouchInteraction()

        try {
            windowManager?.addView(overlayView, params)
            isAttached = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        registerSystemReceivers(context)
        initAudioMonitoring(context)

        mainHandler.post(progressRunnable)
        mainHandler.post(pomoRunnable)
        mainHandler.post(callRunnable)
        mainHandler.post(eqRunnable)
        mainHandler.postDelayed(blinkRunnable, 2000)
    }

    fun detach() {
        if (!isAttached) return
        try {
            unregisterSystemReceivers()
            stopAudioMonitoring()
            mainHandler.removeCallbacksAndMessages(null)
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isAttached = false
            hapticEngine = null
            overlayView = null
            windowManager = null
            currentContext = null
        }
    }

    fun onOrientationChanged(isLandscape: Boolean) {
        mainHandler.post {
            overlayView?.visibility = if (isLandscape) View.GONE else View.VISIBLE
        }
    }

    private fun initAudioMonitoring(context: Context) {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioPlaybackCallback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                    super.onPlaybackConfigChanged(configs)
                    val activeMedia = configs?.any { config ->
                        config.isActive && (
                            config.audioAttributes.usage == AudioAttributes.USAGE_MEDIA ||
                            config.audioAttributes.usage == AudioAttributes.USAGE_GAME
                        )
                    } ?: false

                    onSystemAudioPlaybackChanged(activeMedia)
                }
            }
            try {
                audioPlaybackCallback?.let {
                    audioManager?.registerAudioPlaybackCallback(it, mainHandler)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopAudioMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioPlaybackCallback != null) {
            try {
                audioManager?.unregisterAudioPlaybackCallback(audioPlaybackCallback!!)
            } catch (e: Exception) {
                // ignore
            }
            audioPlaybackCallback = null
        }
        audioManager = null
    }

    private fun onSystemAudioPlaybackChanged(active: Boolean) {
        isSystemAudioActive = active
        if (active) {
            mainHandler.removeCallbacks(systemAudioStopRunnable)
            if (!isRealMediaActive && currentState != NotchState.MUSIC && !isExpanded) {
                tvMusicTitle?.text = "Đang phát âm thanh"
                tvMusicArtist?.text = "Media • Loa ngoài"
                tvOutputRoute?.text = "🔊 Âm thanh hệ thống • Loa ngoài"
                btnPlayPause?.text = "⏸"
                cardCompactMusicArt?.visibility = View.GONE
                compactMusicIcon?.visibility = View.VISIBLE
                ivMusicArtLarge?.visibility = View.GONE
                tvMusicArt?.visibility = View.VISIBLE
                isPlaying = true
                setNotchState(NotchState.MUSIC)
            }
        } else {
            if (!isRealMediaActive && currentState == NotchState.MUSIC) {
                mainHandler.removeCallbacks(systemAudioStopRunnable)
                mainHandler.postDelayed(systemAudioStopRunnable, 2000)
            }
        }
    }

    private fun registerSystemReceivers(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(NusaOverlayService.ACTION_SET_STATE)
            addAction(NusaOverlayService.ACTION_UPDATE_CONFIG)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(systemReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(systemReceiver, filter)
        }
    }

    private fun unregisterSystemReceivers() {
        try {
            currentContext?.unregisterReceiver(systemReceiver)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun winkBothEyes() {
        leftEye?.animate()?.cancel()
        rightEye?.animate()?.cancel()
        notchCard?.animate()?.cancel()
        leftEye?.animate()?.scaleY(0.1f)?.setDuration(100)?.withEndAction {
            leftEye?.animate()?.scaleY(1f)?.setDuration(120)?.start()
        }?.start()
        rightEye?.animate()?.scaleY(0.1f)?.setDuration(100)?.withEndAction {
            rightEye?.animate()?.scaleY(1f)?.setDuration(120)?.start()
        }?.start()
        notchCard?.animate()
            ?.scaleX(1.08f)
            ?.scaleY(0.92f)
            ?.setDuration(100)
            ?.withEndAction {
                notchCard?.animate()
                    ?.scaleX(1f)
                    ?.scaleY(1f)
                    ?.setDuration(140)
                    ?.start()
            }
            ?.start()
    }

    private fun setupTouchInteraction() {
        var startX = 0f
        var startY = 0f
        var startTime = 0L
        var isDownOnCard = false

        // Tap outside to collapse expanded card
        overlayView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                if (isExpanded) {
                    collapseToCompact()
                }
                true
            } else {
                false
            }
        }

        // Secondary detached bubble interaction
        detachedBubble?.setOnClickListener {
            performHaptic()
            if (isMinimalMode) {
                setNotchState(NotchState.POMODORO)
            }
        }

        // Unified Direct Touch & Gesture Listener directly on notchCard
        notchCard?.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startTime = System.currentTimeMillis()
                    isDownOnCard = true
                    notchCard?.animate()?.scaleX(0.96f)?.scaleY(0.96f)?.setDuration(90)?.start()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    notchCard?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(120)?.start()
                    if (isDownOnCard) {
                        isDownOnCard = false
                        val dx = event.rawX - startX
                        val dy = event.rawY - startY
                        val absDx = abs(dx)
                        val absDy = abs(dy)

                        if (!isExpanded) {
                            if (absDx > 30 && absDx > absDy * 1.2f) {
                                performHaptic()
                                if (currentState == NotchState.MUSIC) {
                                    if (dx < 0) {
                                        if (isRealMediaActive) NusaNotificationListener.skipNext()
                                        else skipNextFallback()
                                    } else {
                                        if (isRealMediaActive) NusaNotificationListener.skipPrev()
                                        else skipPrevFallback()
                                    }
                                } else {
                                    winkBothEyes()
                                }
                            } else if (dy > 20 && dy > absDx) {
                                performHaptic()
                                expandCurrentState()
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    notchCard?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(140)?.start()
                    if (!isDownOnCard) return@setOnTouchListener true
                    isDownOnCard = false

                    val duration = System.currentTimeMillis() - startTime
                    val gesture = IslandGestureClassifier.classify(
                        startX = startX,
                        startY = startY,
                        endX = event.rawX,
                        endY = event.rawY,
                        durationMs = duration,
                        isExpanded = isExpanded
                    )

                    when (gesture) {
                        IslandGesture.SWIPE_LEFT -> {
                            performHaptic(NusaHapticEngine.FeedbackType.MEDIUM_CLICK)
                            if (currentState == NotchState.MUSIC) {
                                if (isRealMediaActive) NusaNotificationListener.skipNext()
                                else skipNextFallback()
                            } else {
                                winkBothEyes()
                            }
                            true
                        }
                        IslandGesture.SWIPE_RIGHT -> {
                            performHaptic(NusaHapticEngine.FeedbackType.MEDIUM_CLICK)
                            if (currentState == NotchState.MUSIC) {
                                if (isRealMediaActive) NusaNotificationListener.skipPrev()
                                else skipPrevFallback()
                            } else {
                                winkBothEyes()
                            }
                            true
                        }
                        IslandGesture.SWIPE_DOWN, IslandGesture.LONG_PRESS -> {
                            performHaptic(NusaHapticEngine.FeedbackType.HEAVY_POP)
                            expandCurrentState()
                            true
                        }
                        IslandGesture.SWIPE_UP -> {
                            performHaptic(NusaHapticEngine.FeedbackType.LIGHT_TICK)
                            if (currentState == NotchState.NOTIFICATION) {
                                dismissTransientNotification()
                            } else {
                                collapseToCompact()
                            }
                            true
                        }
                        IslandGesture.TAP -> {
                            performHaptic(NusaHapticEngine.FeedbackType.LIGHT_TICK)
                            if (!isExpanded) {
                                if (currentState == NotchState.NOTIFICATION) {
                                    if (currentNotifPendingIntent != null) {
                                        try {
                                            currentNotifPendingIntent?.send()
                                            dismissTransientNotification()
                                        } catch (e: Exception) {
                                            expandCurrentState()
                                        }
                                    } else {
                                        dismissTransientNotification()
                                    }
                                } else if (currentState == NotchState.PET) {
                                    winkBothEyes()
                                    if (isRealMediaActive || isSystemAudioActive) {
                                        setNotchState(NotchState.MUSIC)
                                    }
                                } else if (currentState == NotchState.MUSIC && !activeMediaPackage.isNullOrEmpty()) {
                                    try {
                                        val intent = currentContext?.packageManager?.getLaunchIntentForPackage(activeMediaPackage!!)
                                        if (intent != null) {
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            currentContext?.startActivity(intent)
                                        } else {
                                            expandCurrentState()
                                        }
                                    } catch (e: Exception) {
                                        expandCurrentState()
                                    }
                                } else {
                                    expandCurrentState()
                                }
                            } else {
                                if (currentState == NotchState.NOTIFICATION && currentNotifPendingIntent != null) {
                                    try {
                                        currentNotifPendingIntent?.send()
                                        dismissTransientNotification()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            true
                        }
                        IslandGesture.NONE -> false
                    }
                }
                else -> false
            }
        }
    }

    private fun applyEyeAndNotchConfig() {
        params?.y = dpToPx(yOffsetDp)

        // Cutouts
        val cutouts = listOf(
            cameraSpace,
            compactMusicCutout,
            compactPomoCutout,
            compactChargeCutout,
            compactCallCutout,
            compactNotifCutout,
            compactRingerCutout
        )
        cutouts.forEach { cutout ->
            cutout?.layoutParams = cutout?.layoutParams?.apply {
                width = dpToPx(eyeSpacingDp)
            }
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

        // Eye offset
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
            NotchState.RINGER -> 160
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
        tvOutputRoute = overlayView?.findViewById(R.id.tvOutputRoute)
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
            performHaptic()
            if (isRealMediaActive) {
                if (isPlaying) NusaNotificationListener.pauseMedia()
                else NusaNotificationListener.playMedia()
            } else {
                isPlaying = !isPlaying
                btnPlayPause?.text = if (isPlaying) "⏸" else "▶"
            }
        }

        btnNext?.setOnClickListener {
            performHaptic()
            if (isRealMediaActive) {
                NusaNotificationListener.skipNext()
            } else {
                skipNextFallback()
            }
        }

        btnPrev?.setOnClickListener {
            performHaptic()
            if (isRealMediaActive) {
                NusaNotificationListener.skipPrev()
            } else {
                skipPrevFallback()
            }
        }

        btnCloseMusic?.setOnClickListener {
            performHaptic()
            collapseToCompact()
        }

        val openMusicAppListener = View.OnClickListener {
            performHaptic()
            val pkg = activeMediaPackage
            if (!pkg.isNullOrEmpty()) {
                try {
                    val intent = currentContext?.packageManager?.getLaunchIntentForPackage(pkg)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        currentContext?.startActivity(intent)
                        collapseToCompact()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        tvMusicTitle?.setOnClickListener(openMusicAppListener)
        tvMusicArtist?.setOnClickListener(openMusicAppListener)
        ivMusicArtLarge?.setOnClickListener(openMusicAppListener)
        cardCompactMusicArt?.setOnClickListener(openMusicAppListener)
        tvOutputRoute?.setOnClickListener(openMusicAppListener)
    }

    private fun skipNextFallback() {
        currentSongIdx = (currentSongIdx + 1) % songs.size
        progressValue = 0
        musicProgressBar?.progress = 0
        updateMusicDisplay()
    }

    private fun skipPrevFallback() {
        currentSongIdx = if (currentSongIdx - 1 < 0) songs.size - 1 else currentSongIdx - 1
        progressValue = 0
        musicProgressBar?.progress = 0
        updateMusicDisplay()
    }

    private fun initPomoControls() {
        tvPomoTime = overlayView?.findViewById(R.id.tvPomoCountdown)
        btnPomoToggle = overlayView?.findViewById(R.id.btnPomoToggle)
        btnPomoReset = overlayView?.findViewById(R.id.btnPomoReset)
        btnClosePomo = overlayView?.findViewById(R.id.btnClosePomo)

        btnPomoToggle?.setOnClickListener {
            performHaptic()
            isPomoRunning = !isPomoRunning
            btnPomoToggle?.text = if (isPomoRunning) "Tạm dừng" else "Tiếp tục"
        }

        btnPomoReset?.setOnClickListener {
            performHaptic()
            pomoSeconds = 25 * 60
            isPomoRunning = true
            btnPomoToggle?.text = "Tạm dừng"
        }

        btnClosePomo?.setOnClickListener {
            performHaptic()
            collapseToCompact()
        }
    }

    private fun initCallControls() {
        btnAcceptCall = overlayView?.findViewById(R.id.btnAcceptCall)
        btnDeclineCall = overlayView?.findViewById(R.id.btnDeclineCall)
        btnCloseCall = overlayView?.findViewById(R.id.btnCloseCall)

        btnAcceptCall?.setOnClickListener {
            performHaptic()
            setNotchState(NotchState.PET)
        }

        btnDeclineCall?.setOnClickListener {
            performHaptic()
            setNotchState(NotchState.PET)
        }

        btnCloseCall?.setOnClickListener {
            performHaptic()
            collapseToCompact()
        }
    }

    private fun initNotificationControls() {
        tvNotifAppBadge = overlayView?.findViewById(R.id.tvNotifAppBadge)
        ivNotifLargeIcon = overlayView?.findViewById(R.id.ivNotifLargeIcon)
        tvNotifSender = overlayView?.findViewById(R.id.tvNotifSender)
        tvNotifBody = overlayView?.findViewById(R.id.tvNotifBody)
        btnOpenNotif = overlayView?.findViewById(R.id.btnOpenNotif)
        btnCloseNotif = overlayView?.findViewById(R.id.btnCloseNotif)

        btnOpenNotif?.setOnClickListener {
            performHaptic()
            try {
                currentNotifPendingIntent?.send()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            dismissTransientNotification()
        }

        btnCloseNotif?.setOnClickListener {
            performHaptic()
            dismissTransientNotification()
        }
    }

    private fun updateMusicDisplay() {
        if (isRealMediaActive) return
        tvMusicTitle?.text = "Chưa phát phương tiện"
        tvMusicArtist?.text = "Mở Spotify, TikTok, YouTube để đồng bộ"
        tvOutputRoute?.text = "🎧 Redmi Note 13 Pro • Sẵn sàng"
    }

    fun onMediaUpdated(
        title: String,
        artist: String,
        albumArt: Bitmap?,
        durationMs: Long,
        positionMs: Long,
        isPlaying: Boolean,
        packageName: String
    ) {
        mainHandler.post {
            this.isRealMediaActive = true
            this.activeMediaPackage = packageName
            this.isPlaying = isPlaying

            tvMusicTitle?.text = title
            tvMusicArtist?.text = if (artist.isNotEmpty()) artist else getAppLabel(packageName)
            btnPlayPause?.text = if (isPlaying) "⏸" else "▶"

            if (albumArt != null) {
                cardCompactMusicArt?.visibility = View.VISIBLE
                ivCompactMusicArt?.setImageBitmap(albumArt)
                compactMusicIcon?.visibility = View.GONE

                ivMusicArtLarge?.visibility = View.VISIBLE
                ivMusicArtLarge?.setImageBitmap(albumArt)
                tvMusicArt?.visibility = View.GONE

                try {
                    Palette.from(albumArt).generate { palette ->
                        val color = palette?.getVibrantColor(0x33FFFFFF)
                            ?: palette?.getDominantColor(0x33FFFFFF)
                            ?: 0x33FFFFFF
                        vIslandStroke?.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
                    }
                } catch (e: Exception) {
                    // ignore
                }
            } else {
                cardCompactMusicArt?.visibility = View.GONE
                compactMusicIcon?.visibility = View.VISIBLE

                ivMusicArtLarge?.visibility = View.GONE
                tvMusicArt?.visibility = View.VISIBLE
                vIslandStroke?.backgroundTintList = android.content.res.ColorStateList.valueOf(0x22FFFFFF)
            }

            val appLabel = getAppLabel(packageName)
            tvOutputRoute?.text = "🎧 $appLabel • Loa ngoài"

            if (durationMs > 0) {
                this.totalDuration = (durationMs / 1000).toInt()
                this.progressValue = (positionMs / 1000).toInt()
                musicProgressBar?.max = totalDuration
                musicProgressBar?.progress = progressValue

                val curMin = progressValue / 60
                val curSec = progressValue % 60
                val remTotal = maxOf(0, totalDuration - progressValue)
                val remMin = remTotal / 60
                val remSec = remTotal % 60
                tvTimeCurrent?.text = String.format("%d:%02d", curMin, curSec)
                tvTimeRemaining?.text = String.format("-%d:%02d", remMin, remSec)
            }

            if (isPlaying) {
                mainHandler.removeCallbacks(pauseTimeoutRunnable)
                if (currentState != NotchState.MUSIC && !isExpanded) {
                    setNotchState(NotchState.MUSIC)
                }
            } else {
                mainHandler.removeCallbacks(pauseTimeoutRunnable)
                mainHandler.postDelayed(pauseTimeoutRunnable, 3500)
            }
        }
    }

    fun onMediaStopped() {
        mainHandler.post {
            isRealMediaActive = false
            isPlaying = false
            activeMediaPackage = null
            mainHandler.removeCallbacks(pauseTimeoutRunnable)
            if (currentState == NotchState.MUSIC) {
                if (isExpanded) {
                    collapseToCompact()
                }
                setNotchState(NotchState.PET)
            }
        }
    }

    fun onNotificationReceived(
        packageName: String,
        title: String,
        text: String,
        largeIcon: Bitmap?,
        pendingIntent: PendingIntent?
    ) {
        mainHandler.post {
            currentNotifPendingIntent = pendingIntent

            val appLabel = getAppLabel(packageName)
            tvCompactNotifBadge?.text = appLabel
            tvNotifAppBadge?.text = "💬 $appLabel"
            tvNotifSender?.text = title
            tvNotifBody?.text = text

            if (largeIcon != null) {
                ivCompactNotifIcon?.setImageBitmap(largeIcon)
                ivNotifLargeIcon?.setImageBitmap(largeIcon)
            } else {
                ivCompactNotifIcon?.setImageResource(android.R.drawable.sym_action_chat)
                ivNotifLargeIcon?.setImageResource(android.R.drawable.sym_action_chat)
            }

            performHaptic()
            setNotchState(NotchState.NOTIFICATION)

            // Auto-collapse / dismiss notification island after 4.5 seconds
            mainHandler.removeCallbacks(dismissNotifRunnable)
            mainHandler.postDelayed(dismissNotifRunnable, 4500)
        }
    }

    fun dismissTransientNotification() {
        mainHandler.removeCallbacks(dismissNotifRunnable)
        priorityManager.dismissTransient(NotchState.NOTIFICATION)
    }

    private val dismissNotifRunnable = Runnable {
        if (currentState == NotchState.NOTIFICATION) {
            dismissTransientNotification()
        }
    }

    private fun showRingerHUD(mode: Int) {
        mainHandler.post {
            when (mode) {
                AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_VIBRATE -> {
                    tvRingerIcon?.text = "🔕"
                    tvRingerText?.text = "Im lặng"
                    tvRingerText?.setTextColor(0xFFFF453A.toInt())
                }
                else -> {
                    tvRingerIcon?.text = "🔔"
                    tvRingerText?.text = "Chuông"
                    tvRingerText?.setTextColor(0xFF34C759.toInt())
                }
            }
            performHaptic(NusaHapticEngine.FeedbackType.MEDIUM_CLICK)
            setNotchState(NotchState.RINGER)

            mainHandler.removeCallbacks(dismissRingerRunnable)
            mainHandler.postDelayed(dismissRingerRunnable, 2500)
        }
    }

    private val dismissRingerRunnable = Runnable {
        if (currentState == NotchState.RINGER) {
            priorityManager.dismissTransient(NotchState.RINGER)
        }
    }

    fun setNotchState(state: NotchState) {
        priorityManager.requestState(state)
    }

    private fun applyStateTransition(state: NotchState) {
        isMinimalMode = (state == NotchState.MINIMAL)

        when (state) {
            NotchState.PET -> {
                collapseToCompact()
                showCompact(compactPet)
                detachedBubble?.visibility = View.GONE
            }
            NotchState.MUSIC -> {
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
                mainHandler.removeCallbacks(dismissChargeRunnable)
                mainHandler.postDelayed(dismissChargeRunnable, 3500)
            }
            NotchState.CALL -> {
                showCompact(compactCall)
                expandCurrentState()
            }
            NotchState.MINIMAL -> {
                collapseToCompact()
                showCompact(compactMusic)
                detachedBubble?.visibility = View.VISIBLE
                tvBubbleIcon?.text = "⏱️"
            }
            NotchState.NOTIFICATION -> {
                showExpanded(layerNotification)
                expandCurrentState()
                detachedBubble?.visibility = View.GONE
                mainHandler.removeCallbacks(dismissNotifRunnable)
                mainHandler.postDelayed(dismissNotifRunnable, 4500)
            }
            NotchState.RINGER -> {
                collapseToCompact()
                showCompact(compactRinger)
                detachedBubble?.visibility = View.GONE
                mainHandler.removeCallbacks(dismissRingerRunnable)
                mainHandler.postDelayed(dismissRingerRunnable, 2500)
            }
        }
    }

    private fun showCompact(activeCompact: View?) {
        if (activeCompact == compactPet) {
            leftEye?.animate()?.cancel()
            rightEye?.animate()?.cancel()
            leftEye?.scaleY = 1.0f
            leftEye?.scaleX = 1.0f
            rightEye?.scaleY = 1.0f
            rightEye?.scaleX = 1.0f
            notchCard?.animate()?.cancel()
            notchCard?.scaleX = 1.0f
            notchCard?.scaleY = 1.0f
        }
        listOf(
            compactPet,
            compactMusic,
            compactPomo,
            compactCharging,
            compactCall,
            compactNotification,
            compactRinger
        ).forEach {
            it?.visibility = if (it == activeCompact) View.VISIBLE else View.GONE
        }
        listOf(layerMusic, layerPomo, layerCharging, layerCall, layerNotification).forEach {
            it?.visibility = View.GONE
        }
    }

    private fun showExpanded(activeExpanded: View?) {
        listOf(layerMusic, layerPomo, layerCharging, layerCall, layerNotification).forEach {
            it?.visibility = if (it == activeExpanded) View.VISIBLE else View.GONE
        }
        listOf(
            compactPet,
            compactMusic,
            compactPomo,
            compactCharging,
            compactCall,
            compactNotification,
            compactRinger
        ).forEach {
            it?.visibility = View.GONE
        }
    }

    fun expandCurrentState() {
        isExpanded = true
        when (currentState) {
            NotchState.PET -> {
                if (isRealMediaActive || isSystemAudioActive) {
                    showExpanded(layerMusic)
                } else if (isPomoRunning) {
                    showExpanded(layerPomo)
                } else {
                    tvMusicTitle?.text = "Redmi Note 13 Pro 5G"
                    tvMusicArtist?.text = "Nusa Dynamic Island • Sẵn sàng"
                    tvOutputRoute?.text = "✨ Trạng thái nghỉ ngơi"
                    showExpanded(layerMusic)
                }
            }
            NotchState.MUSIC, NotchState.MINIMAL -> {
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
            NotchState.NOTIFICATION -> {
                showExpanded(layerNotification)
            }
            NotchState.RINGER -> {
                collapseToCompact()
                return
            }
        }

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

        animateCardMorph(targetW, targetH, 28)
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
            NotchState.NOTIFICATION -> showCompact(compactNotification)
            NotchState.RINGER -> showCompact(compactRinger)
            NotchState.MINIMAL -> {
                showCompact(compactMusic)
                detachedBubble?.visibility = View.VISIBLE
            }
        }

        val targetWidthDp = when (currentState) {
            NotchState.PET -> collapsedWidthDp
            NotchState.RINGER -> 160
            else -> compactActivityWidthDp
        }
        val targetW = dpToPx(targetWidthDp)
        val targetH = dpToPx(collapsedHeightDp)
        val targetCorner = collapsedHeightDp / 2

        animateCardMorph(targetW, targetH, targetCorner) {
            try {
                if (isAttached && overlayView != null && params != null) {
                    windowManager?.updateViewLayout(overlayView, params)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun animateCardMorph(
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
            duration = 340
            interpolator = appleSpringInterpolator
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
                (vIslandStroke?.background?.mutate() as? android.graphics.drawable.GradientDrawable)?.cornerRadius = r
            }
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onEnd?.invoke()
            }
        })
        anim.start()
    }

    private fun performHaptic(type: NusaHapticEngine.FeedbackType = NusaHapticEngine.FeedbackType.LIGHT_TICK) {
        hapticEngine?.perform(type, notchCard)
    }

    private fun getAppLabel(pkg: String): String {
        val ctx = currentContext ?: return "Tin nhắn"
        return try {
            val pm = ctx.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            when {
                pkg.contains("telegram", true) -> "Telegram"
                pkg.contains("zalo", true) -> "Zalo"
                pkg.contains("orca", true) || pkg.contains("messenger", true) -> "Messenger"
                pkg.contains("whatsapp", true) -> "WhatsApp"
                pkg.contains("spotify", true) -> "Spotify"
                pkg.contains("youtube", true) -> "YouTube Music"
                else -> "Thông báo"
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = currentContext?.resources?.displayMetrics?.density ?: 3f
        return (dp * density).toInt()
    }
}
