package ru.versal.voice

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class AssistantAccessibilityService : AccessibilityService() {
    companion object {
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        @Volatile private var instance: AssistantAccessibilityService? = null
        @Volatile private var foregroundPackage: String? = null

        fun goHome(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_HOME)
        }

        fun isYouTubeForeground(): Boolean = foregroundPackage == YOUTUBE_PACKAGE

        // Shorts: свайп вверх — следующий ролик, вниз — предыдущий.
        fun swipeShort(next: Boolean): Boolean {
            val service = instance ?: return false
            if (!isYouTubeForeground()) return false
            return service.swipeShortInternal(next)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            foregroundPackage = event.packageName?.toString()
        }
    }

    private fun swipeShortInternal(next: Boolean): Boolean {
        val width = resources.displayMetrics.widthPixels.toFloat()
        val height = resources.displayMetrics.heightPixels.toFloat()
        val x = width * 0.5f
        val fromY = if (next) height * 0.78f else height * 0.22f
        val toY = if (next) height * 0.22f else height * 0.78f
        val path = Path().apply {
            moveTo(x, fromY)
            lineTo(x, toY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 320))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) {
            instance = null
            foregroundPackage = null
        }
        super.onDestroy()
    }
}
