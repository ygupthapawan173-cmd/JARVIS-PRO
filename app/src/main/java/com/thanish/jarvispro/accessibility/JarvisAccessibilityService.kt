package com.thanish.jarvispro.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Safe starter only. Actions will be added explicitly and must be initiated by the user.
 */
class JarvisAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Observe only; no automatic taps or data collection in this starter.
    }
    override fun onInterrupt() = Unit
}
