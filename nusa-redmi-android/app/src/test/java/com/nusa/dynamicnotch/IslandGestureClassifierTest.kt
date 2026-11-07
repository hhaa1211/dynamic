package com.nusa.dynamicnotch

import com.nusa.dynamicnotch.core.gestures.IslandGesture
import com.nusa.dynamicnotch.core.gestures.IslandGestureClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class IslandGestureClassifierTest {

    @Test
    fun testTapDetection() {
        val gesture = IslandGestureClassifier.classify(
            startX = 100f, startY = 50f,
            endX = 105f, endY = 52f,
            durationMs = 120L,
            isExpanded = false
        )
        assertEquals(IslandGesture.TAP, gesture)
    }

    @Test
    fun testLongPressDetection() {
        val gesture = IslandGestureClassifier.classify(
            startX = 100f, startY = 50f,
            endX = 102f, endY = 51f,
            durationMs = 650L,
            isExpanded = false
        )
        assertEquals(IslandGesture.LONG_PRESS, gesture)
    }

    @Test
    fun testHorizontalSwipeLeft() {
        val gesture = IslandGestureClassifier.classify(
            startX = 200f, startY = 50f,
            endX = 120f, endY = 55f,
            durationMs = 150L,
            isExpanded = false
        )
        assertEquals(IslandGesture.SWIPE_LEFT, gesture)
    }

    @Test
    fun testHorizontalSwipeRight() {
        val gesture = IslandGestureClassifier.classify(
            startX = 120f, startY = 50f,
            endX = 210f, endY = 48f,
            durationMs = 160L,
            isExpanded = false
        )
        assertEquals(IslandGesture.SWIPE_RIGHT, gesture)
    }

    @Test
    fun testVerticalSwipeDownInCompactExpands() {
        val gesture = IslandGestureClassifier.classify(
            startX = 150f, startY = 50f,
            endX = 155f, endY = 180f,
            durationMs = 180L,
            isExpanded = false
        )
        assertEquals(IslandGesture.SWIPE_DOWN, gesture)
    }

    @Test
    fun testVerticalSwipeUpInExpandedCollapses() {
        val gesture = IslandGestureClassifier.classify(
            startX = 150f, startY = 200f,
            endX = 152f, endY = 80f,
            durationMs = 150L,
            isExpanded = true
        )
        assertEquals(IslandGesture.SWIPE_UP, gesture)
    }
}
