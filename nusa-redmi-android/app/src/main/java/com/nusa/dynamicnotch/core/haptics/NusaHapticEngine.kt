package com.nusa.dynamicnotch.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Enterprise-grade Haptic Engine emulating Apple Taptic Engine tactile physics
 * with sub-millisecond latency and dynamic vibration composition across Android APIs.
 */
class NusaHapticEngine(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    enum class FeedbackType {
        LIGHT_TICK,    // Compact notch touch, seek progress
        MEDIUM_CLICK,  // State switch, track change, button tap
        HEAVY_POP,     // Expand dynamic island, action trigger
        SUCCESS_PULSE, // Charge connected, call accepted
        ERROR_REJECT   // Action denied, bounds limit
    }

    fun perform(type: FeedbackType, fallbackView: View? = null) {
        if (vibrator == null || !vibrator!!.hasVibrator()) {
            performViewFeedback(type, fallbackView)
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator?.areAllPrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    VibrationEffect.Composition.PRIMITIVE_TICK
                ) == true
            ) {
                // Android 11+ High-Definition Primitive Vibration Composition
                val composition = VibrationEffect.startComposition()
                when (type) {
                    FeedbackType.LIGHT_TICK -> {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.45f)
                    }
                    FeedbackType.MEDIUM_CLICK -> {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
                    }
                    FeedbackType.HEAVY_POP -> {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                    }
                    FeedbackType.SUCCESS_PULSE -> {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f)
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f, 50)
                    }
                    FeedbackType.ERROR_REJECT -> {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f)
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 70)
                    }
                }
                vibrator?.vibrate(composition.compose())
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ Predefined Effects
                val effectId = when (type) {
                    FeedbackType.LIGHT_TICK -> VibrationEffect.EFFECT_TICK
                    FeedbackType.MEDIUM_CLICK -> VibrationEffect.EFFECT_CLICK
                    FeedbackType.HEAVY_POP -> VibrationEffect.EFFECT_HEAVY_CLICK
                    FeedbackType.SUCCESS_PULSE, FeedbackType.ERROR_REJECT -> VibrationEffect.EFFECT_DOUBLE_CLICK
                }
                vibrator?.vibrate(VibrationEffect.createPredefined(effectId))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0+ One-Shot Amplitude Waveform
                val duration = when (type) {
                    FeedbackType.LIGHT_TICK -> 12L
                    FeedbackType.MEDIUM_CLICK -> 24L
                    FeedbackType.HEAVY_POP -> 40L
                    FeedbackType.SUCCESS_PULSE -> 55L
                    FeedbackType.ERROR_REJECT -> 70L
                }
                val amplitude = when (type) {
                    FeedbackType.LIGHT_TICK -> 80
                    FeedbackType.MEDIUM_CLICK -> 160
                    FeedbackType.HEAVY_POP -> 255
                    FeedbackType.SUCCESS_PULSE -> 180
                    FeedbackType.ERROR_REJECT -> 220
                }
                vibrator?.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                performViewFeedback(type, fallbackView)
            }
        } catch (e: Exception) {
            performViewFeedback(type, fallbackView)
        }
    }

    private fun performViewFeedback(type: FeedbackType, view: View?) {
        view ?: return
        val feedbackConstant = when (type) {
            FeedbackType.LIGHT_TICK -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    HapticFeedbackConstants.TEXT_HANDLE_MOVE
                } else {
                    HapticFeedbackConstants.CLOCK_TICK
                }
            }
            FeedbackType.MEDIUM_CLICK -> HapticFeedbackConstants.KEYBOARD_TAP
            FeedbackType.HEAVY_POP -> HapticFeedbackConstants.LONG_PRESS
            FeedbackType.SUCCESS_PULSE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                }
            }
            FeedbackType.ERROR_REJECT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.REJECT
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                }
            }
        }
        view.performHapticFeedback(feedbackConstant)
    }
}
