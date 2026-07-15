package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class JunoAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val rootNode = rootInActiveWindow ?: return
        val targetTexts = listOf(
            "A single app", "একক অ্যাপ",
            "Entire screen", "সম্পূর্ণ স্ক্রীন",
            "Start now", "Allow", "Start", "শুরু করুন", "অনুমতি দিন"
        )
        for (text in targetTexts) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    var current = node
                    while (current != null) {
                        if (current.isClickable) {
                            current.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                            return
                        }
                        current = current.parent
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        var instance: JunoAccessibilityService? = null

        fun tap(x: Float, y: Float): Boolean {
            val service = instance ?: return false
            val path = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 100L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return service.dispatchGesture(gesture, null, null)
        }

        fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): Boolean {
            val service = instance ?: return false
            val path = Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return service.dispatchGesture(gesture, null, null)
        }
    }
}
