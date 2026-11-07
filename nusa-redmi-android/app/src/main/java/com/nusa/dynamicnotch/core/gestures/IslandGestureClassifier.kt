package com.nusa.dynamicnotch.core.gestures

import kotlin.math.abs

enum class IslandGesture {
    TAP,
    LONG_PRESS,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    SWIPE_DOWN,
    SWIPE_UP,
    NONE
}

object IslandGestureClassifier {

    private const val CLICK_MAX_DISPLACEMENT = 30f
    private const val LONG_PRESS_MIN_DURATION_MS = 500L
    private const val HORIZONTAL_SWIPE_MIN_DX = 35f
    private const val VERTICAL_SWIPE_MIN_DY = 25f

    fun classify(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        isExpanded: Boolean
    ): IslandGesture {
        val dx = endX - startX
        val dy = endY - startY
        val absDx = abs(dx)
        val absDy = abs(dy)

        if (!isExpanded) {
            // Compact Mode Gestures
            if (absDx > HORIZONTAL_SWIPE_MIN_DX && absDx > absDy * 1.2f) {
                return if (dx < 0) IslandGesture.SWIPE_LEFT else IslandGesture.SWIPE_RIGHT
            }
            if (dy > VERTICAL_SWIPE_MIN_DY && dy > absDx) {
                return IslandGesture.SWIPE_DOWN
            }
            if (absDx < CLICK_MAX_DISPLACEMENT && absDy < CLICK_MAX_DISPLACEMENT) {
                return if (durationMs >= LONG_PRESS_MIN_DURATION_MS) {
                    IslandGesture.LONG_PRESS
                } else {
                    IslandGesture.TAP
                }
            }
        } else {
            // Expanded Mode Gestures
            if (dy < -VERTICAL_SWIPE_MIN_DY && absDy > absDx) {
                return IslandGesture.SWIPE_UP
            }
            if (absDx > HORIZONTAL_SWIPE_MIN_DX && absDx > absDy * 1.2f) {
                return if (dx < 0) IslandGesture.SWIPE_LEFT else IslandGesture.SWIPE_RIGHT
            }
            if (absDx < CLICK_MAX_DISPLACEMENT && absDy < CLICK_MAX_DISPLACEMENT && durationMs < LONG_PRESS_MIN_DURATION_MS) {
                return IslandGesture.TAP
            }
        }

        return IslandGesture.NONE
    }
}
