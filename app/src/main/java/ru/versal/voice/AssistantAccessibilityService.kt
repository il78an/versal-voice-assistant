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
        event?.packageName?.toString()?.takeIf { it.isNotBlank() }?.let {
            foregroundPackage = it
        }
    }

    private fun swipeShortInternal(next: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        if (foregroundPackage != "com.google.android.youtube") return false

        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.5f
        val startY = metrics.heightPixels * if (next) 0.78f else 0.28f
        val endY = metrics.heightPixels * if (next) 0.28f else 0.78f
        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
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
