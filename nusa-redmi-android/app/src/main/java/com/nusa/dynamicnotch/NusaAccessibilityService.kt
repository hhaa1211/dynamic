package com.nusa.dynamicnotch

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class NusaAccessibilityService : AccessibilityService() {

    companion object {
        var isRunning: Boolean = false
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        // Attach overlay directly at Layer 161,000 (TYPE_ACCESSIBILITY_OVERLAY - above StatusBar 151,000)
        DynamicNotchViewManager.attach(this, asAccessibility = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        DynamicNotchViewManager.detach()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used for overlay interactions
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val isLandscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        DynamicNotchViewManager.onOrientationChanged(isLandscape)
    }

    override fun onInterrupt() {}
}
