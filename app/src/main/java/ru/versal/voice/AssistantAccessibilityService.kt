package ru.versal.voice

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AssistantAccessibilityService : AccessibilityService() {
    companion object {
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        @Volatile private var instance: AssistantAccessibilityService? = null
        @Volatile private var foregroundPackage: String? = null

        fun goHome(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_HOME)
        }

        fun isAvailable(): Boolean = instance != null
        fun isYouTubeForeground(): Boolean = foregroundPackage == YOUTUBE_PACKAGE

        fun swipeShort(next: Boolean): Boolean {
            val service = instance ?: return false
            if (!isYouTubeForeground()) return false
            return service.swipeShortInternal(next)
        }

        fun pauseYouTube(): Boolean {
            val service = instance ?: return false
            if (!isYouTubeForeground()) return false
            return service.clickPlaybackControl(listOf("пауза", "pause"))
        }

        fun playYouTube(): Boolean {
            val service = instance ?: return false
            if (!isYouTubeForeground()) return false
            return service.clickPlaybackControl(listOf("воспроизвести", "продолжить", "play", "resume"))
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        refreshForegroundPackage()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventPackage = event?.packageName?.toString()
        if (!eventPackage.isNullOrBlank()) foregroundPackage = eventPackage
        else refreshForegroundPackage()
    }

    private fun refreshForegroundPackage() {
        try {
            foregroundPackage = rootInActiveWindow?.packageName?.toString()
        } catch (_: Exception) {
            // Keep the last package observed from an accessibility event.
        }
    }

    private fun clickPlaybackControl(labels: List<String>): Boolean {
        val root = try { rootInActiveWindow } catch (_: Exception) { null } ?: return false
        return findAndClick(root, labels)
    }

    private fun findAndClick(node: AccessibilityNodeInfo, labels: List<String>): Boolean {
        val description = node.contentDescription?.toString()?.lowercase().orEmpty()
        val text = node.text?.toString()?.lowercase().orEmpty()
        val matches = labels.any { description.contains(it) || text.contains(it) }
        if (matches) {
            var target: AccessibilityNodeInfo? = node
            while (target != null) {
                if (target.isClickable && target.isEnabled) {
                    return try { target.performAction(AccessibilityNodeInfo.ACTION_CLICK) } catch (_: Exception) { false }
                }
                target = target.parent
            }
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (findAndClick(child, labels)) return true
        }
        return false
    }

    private fun swipeShortInternal(next: Boolean): Boolean {
        val width = resources.displayMetrics.widthPixels.toFloat()
        val height = resources.displayMetrics.heightPixels.toFloat()
        val x = width * 0.5f
        val fromY = if (next) height * 0.78f else height * 0.22f
        val toY = if (next) height * 0.22f else height * 0.78f
        val path = Path().apply { moveTo(x, fromY); lineTo(x, toY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 420))
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
