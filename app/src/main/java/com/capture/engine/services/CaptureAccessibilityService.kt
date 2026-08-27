package com.capture.engine.services

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class CaptureAccessibilityService : AccessibilityService() {

    var currentPackageName: String? = null
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "CaptureAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank() && pkg != packageName) {
                currentPackageName = pkg
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "CaptureAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    /**
     * Extracts the hierarchical tree from the current active window.
     * @param fullRaw if false, only visible nodes containing text or content descriptions are included.
     *                if true, all structural layout nodes are dumped.
     */
    fun extractAppTree(fullRaw: Boolean, targetBounds: Rect? = null): String {
        val root = rootInActiveWindow ?: return ""
        val builder = StringBuilder()
        traverseNode(root, builder, 0, fullRaw, targetBounds)
        return builder.toString()
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        builder: StringBuilder,
        depth: Int,
        fullRaw: Boolean,
        targetBounds: Rect?
    ) {
        val nodeBounds = Rect()
        node.getBoundsInScreen(nodeBounds)

        // If targetBounds is specified (Point selection mode), filter to nodes intersecting the bounds
        if (targetBounds != null && !Rect.intersects(nodeBounds, targetBounds)) {
            return
        }

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val className = node.className?.toString()?.substringAfterLast('.') ?: "View"
        val viewId = node.viewIdResourceName?.substringAfterLast('/')

        val hasContent = !text.isNullOrBlank() || !desc.isNullOrBlank()
        val isVisible = node.isVisibleToUser

        if (fullRaw || (isVisible && hasContent)) {
            val indent = "  ".repeat(depth)
            builder.append(indent).append("• [").append(className).append("]")

            if (!viewId.isNullOrBlank()) {
                builder.append(" #").append(viewId)
            }
            if (!text.isNullOrBlank()) {
                builder.append(" text=\"").append(text.replace("\n", " ")).append("\"")
            }
            if (!desc.isNullOrBlank() && desc != text) {
                builder.append(" desc=\"").append(desc.replace("\n", " ")).append("\"")
            }
            builder.append(" bounds=").append(nodeBounds.toShortString())
            builder.append("\n")
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, builder, depth + 1, fullRaw, targetBounds)
            child.recycle()
        }
    }

    /**
     * Extracts text from the currently focused or selected node.
     */
    fun extractHighlightedText(): String? {
        val root = rootInActiveWindow ?: return null

        // Try to get input focused node
        val inputFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (inputFocus != null) {
            val text = inputFocus.text?.toString()
            inputFocus.recycle()
            if (!text.isNullOrBlank()) return text
        }

        // Try accessibility focus
        val accessibilityFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (accessibilityFocus != null) {
            val text = accessibilityFocus.text?.toString() ?: accessibilityFocus.contentDescription?.toString()
            accessibilityFocus.recycle()
            if (!text.isNullOrBlank()) return text
        }

        return null
    }

    companion object {
        private const val TAG = "CaptureAccessibility"
        var instance: CaptureAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null
    }
}
