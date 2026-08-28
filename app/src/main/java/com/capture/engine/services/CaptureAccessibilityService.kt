package com.capture.engine.services

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Base64
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

class CaptureAccessibilityService : AccessibilityService() {

    var currentPackageName: String? = null
        private set

    @Volatile
    private var lastSelectedText: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "CaptureAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventPkg = event.packageName?.toString()
        if (!eventPkg.isNullOrBlank() && eventPkg != packageName) {
            currentPackageName = eventPkg
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (!eventPkg.isNullOrBlank() && eventPkg != packageName) {
                    currentPackageName = eventPkg
                }
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                try {
                    val fromIndex = event.fromIndex
                    val toIndex = event.toIndex
                    val textList = event.text
                    if (fromIndex >= 0 && toIndex > fromIndex && textList.isNotEmpty()) {
                        val fullText = textList.joinToString(" ")
                        if (toIndex <= fullText.length) {
                            val selected = fullText.substring(fromIndex, toIndex).trim()
                            if (selected.isNotBlank()) {
                                lastSelectedText = selected
                            }
                        }
                    } else if (textList.isNotEmpty()) {
                        val fullText = textList.joinToString(" ").trim()
                        if (fullText.isNotBlank()) {
                            lastSelectedText = fullText
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error capturing text selection event", e)
                }
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
     * Resolves the target application root node.
     * If rootInActiveWindow is null or belongs to Capture Engine, inspects windows list for foreground app.
     */
    fun getTargetRootNode(): AccessibilityNodeInfo? {
        val activeRoot = rootInActiveWindow
        if (activeRoot != null) {
            val pkg = activeRoot.packageName?.toString()
            if (!pkg.isNullOrBlank() && pkg != packageName) {
                currentPackageName = pkg
                return activeRoot
            }
        }

        // Search windows list for the foreground application window
        try {
            val winList = windows
            if (!winList.isNullOrEmpty()) {
                val appWindow = winList.firstOrNull { win ->
                    win.type == AccessibilityWindowInfo.TYPE_APPLICATION && (win.isActive || win.isFocused)
                } ?: winList.firstOrNull { win ->
                    win.type == AccessibilityWindowInfo.TYPE_APPLICATION
                } ?: winList.firstOrNull { win ->
                    val r = win.root
                    val winPkg = r?.packageName?.toString()
                    winPkg != null && winPkg != packageName
                }

                val winRoot = appWindow?.root
                if (winRoot != null) {
                    val pkg = winRoot.packageName?.toString()
                    if (!pkg.isNullOrBlank() && pkg != packageName) {
                        currentPackageName = pkg
                    }
                    return winRoot
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting accessibility windows", e)
        }

        return activeRoot
    }

    /**
     * Extracts the hierarchical tree from the current active window or foreground application.
     * @param fullRaw if false, only nodes containing text or descriptions or visible structure are included.
     *                if true, all structural layout nodes are dumped.
     */
    fun extractAppTree(fullRaw: Boolean, targetBounds: Rect? = null): String {
        val root = getTargetRootNode() ?: return ""
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

        if (fullRaw || hasContent || isVisible) {
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
     * Extracts text from the currently focused node, active text selection, or recent selection event.
     */
    fun extractHighlightedText(): String? {
        val root = getTargetRootNode()

        // 1. Search tree for active text selection ranges
        if (root != null) {
            val selectionFromTree = findTextSelectionInNode(root)
            if (!selectionFromTree.isNullOrBlank()) {
                return selectionFromTree
            }

            // 2. Try input focused node
            val inputFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (inputFocus != null) {
                val selStart = inputFocus.textSelectionStart
                val selEnd = inputFocus.textSelectionEnd
                val fullText = inputFocus.text?.toString()
                inputFocus.recycle()

                if (!fullText.isNullOrBlank()) {
                    if (selStart >= 0 && selEnd > selStart && selEnd <= fullText.length) {
                        val sub = fullText.substring(selStart, selEnd).trim()
                        if (sub.isNotBlank()) return sub
                    }
                    return fullText
                }
            }

            // 3. Try accessibility focus
            val accessibilityFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            if (accessibilityFocus != null) {
                val selStart = accessibilityFocus.textSelectionStart
                val selEnd = accessibilityFocus.textSelectionEnd
                val text = accessibilityFocus.text?.toString() ?: accessibilityFocus.contentDescription?.toString()
                accessibilityFocus.recycle()

                if (!text.isNullOrBlank()) {
                    if (selStart >= 0 && selEnd > selStart && selEnd <= text.length) {
                        val sub = text.substring(selStart, selEnd).trim()
                        if (sub.isNotBlank()) return sub
                    }
                    return text
                }
            }
        }

        // 4. Fallback to latest event-cached text selection
        if (!lastSelectedText.isNullOrBlank()) {
            return lastSelectedText
        }

        return null
    }

    private fun findTextSelectionInNode(node: AccessibilityNodeInfo): String? {
        val selStart = node.textSelectionStart
        val selEnd = node.textSelectionEnd
        val nodeText = node.text?.toString()

        if (selStart >= 0 && selEnd > selStart && !nodeText.isNullOrBlank() && selEnd <= nodeText.length) {
            val selected = nodeText.substring(selStart, selEnd).trim()
            if (selected.isNotBlank()) return selected
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            val childSel = findTextSelectionInNode(child)
            child.recycle()
            if (!childSel.isNullOrBlank()) return childSel
        }
        return null
    }

    /**
     * Captures a screenshot using Android 11+ AccessibilityService.takeScreenshot API.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun takeScreenshotBitmap(): Bitmap? = suspendCancellableCoroutine { continuation ->
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                            hardwareBuffer.close()
                            if (continuation.isActive) {
                                continuation.resume(bitmap)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error wrapping hardware buffer into bitmap", e)
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "AccessibilityService.takeScreenshot failed: $errorCode")
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling takeScreenshot", e)
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
    }

    /**
     * Captures the screen via AccessibilityService, crops if needed, compresses to 80% JPEG,
     * and returns the Base64-encoded string.
     */
    suspend fun captureScreenBase64(cropRect: Rect? = null): String? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return@withContext null
        }

        val fullBitmap = takeScreenshotBitmap() ?: return@withContext null

        try {
            val targetBitmap = if (cropRect != null && !cropRect.isEmpty) {
                val safeLeft = cropRect.left.coerceIn(0, fullBitmap.width - 1)
                val safeTop = cropRect.top.coerceIn(0, fullBitmap.height - 1)
                val safeWidth = cropRect.width().coerceIn(1, fullBitmap.width - safeLeft)
                val safeHeight = cropRect.height().coerceIn(1, fullBitmap.height - safeTop)

                val cropped = Bitmap.createBitmap(fullBitmap, safeLeft, safeTop, safeWidth, safeHeight)
                if (cropped != fullBitmap) {
                    fullBitmap.recycle()
                }
                cropped
            } else {
                fullBitmap
            }

            val outputStream = ByteArrayOutputStream()
            targetBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytes = outputStream.toByteArray()
            targetBitmap.recycle()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding accessibility screenshot", e)
            null
        }
    }

    companion object {
        private const val TAG = "CaptureAccessibility"
        var instance: CaptureAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null
    }
}
