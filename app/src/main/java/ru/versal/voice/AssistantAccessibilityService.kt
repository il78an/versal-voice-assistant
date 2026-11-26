package ru.versal.voice

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class AssistantAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile private var instance: AssistantAccessibilityService? = null

        fun goHome(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_HOME)
        }

        fun swipeShort(next: Boolean): Boolean {
            val service = instance ?: return false
            return service.swipeShortInternal(next)
        }
    }

    @Volatile private var foregroundPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()
        if (!pkg.isNullOrBlank()) foregroundPackage = pkg
    }

    private fun swipeShortInternal(next: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        if (foregroundPackage != "com.google.android.youtube") return false

        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.5f
        val lowerY = metrics.heightPixels * 0.76f
        val upperY = metrics.heightPixels * 0.28f
        val path = Path().apply {
            moveTo(x, if (next) lowerY else upperY)
            lineTo(x, if (next) upperY else lowerY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 280))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        foregroundPackage = null
        super.onDestroy()
    }
}
