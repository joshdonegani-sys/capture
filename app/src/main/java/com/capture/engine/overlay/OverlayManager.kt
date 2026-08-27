package com.capture.engine.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.capture.engine.CaptureApplication
import com.capture.engine.data.PayloadBuilder
import com.capture.engine.data.SettingsRepository
import com.capture.engine.model.ActiveContextSources
import com.capture.engine.model.CaptureEntity
import com.capture.engine.model.DeliveryStatus
import com.capture.engine.network.WebhookClient
import com.capture.engine.services.CaptureAccessibilityService
import com.capture.engine.services.MediaMetadataListener
import com.capture.engine.services.ScreenCaptureService
import com.capture.engine.ui.theme.CaptureEngineTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

class OverlayManager private constructor(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val settings: SettingsRepository = CaptureApplication.instance.settingsRepository

    private var toolbarView: ComposeView? = null
    private var toolbarParams: WindowManager.LayoutParams? = null
    private var toolbarLifecycle: OverlayLifecycleOwner? = null

    private var bottomSheetView: ComposeView? = null
    private var bottomSheetLifecycle: OverlayLifecycleOwner? = null

    private var pointOverlayView: ComposeView? = null
    private var pointLifecycle: OverlayLifecycleOwner? = null

    // State
    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded

    private val _isPointModeActive = MutableStateFlow(false)
    val isPointModeActive: StateFlow<Boolean> = _isPointModeActive

    private val _activeSelectionBounds = MutableStateFlow<Rect?>(null)
    val activeSelectionBounds: StateFlow<Rect?> = _activeSelectionBounds

    fun canDrawOverlays(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun showToolbar() {
        if (!canDrawOverlays() || toolbarView != null) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val dm = context.resources.displayMetrics
        toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = (dm.heightPixels * 0.45f).toInt()
        }

        toolbarLifecycle = OverlayLifecycleOwner()
        toolbarView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(toolbarLifecycle)
            setViewTreeViewModelStoreOwner(toolbarLifecycle)
            setViewTreeSavedStateRegistryOwner(toolbarLifecycle)

            setContent {
                CaptureEngineTheme {
                    FloatingToolbar(
                        isExpanded = _isExpanded,
                        isPointActive = _isPointModeActive,
                        hasActiveSelection = _activeSelectionBounds,
                        onExpandToggle = { _isExpanded.value = !_isExpanded.value },
                        onCollapse = { _isExpanded.value = false },
                        onDrag = { dx, dy ->
                            toolbarParams?.let { params ->
                                val w = toolbarView?.width ?: 0
                                val h = toolbarView?.height ?: 0
                                val maxX = (dm.widthPixels - w).coerceAtLeast(0)
                                val maxY = (dm.heightPixels - h).coerceAtLeast(0)
                                params.x = (params.x + dx.toInt()).coerceIn(0, maxX)
                                params.y = (params.y + dy.toInt()).coerceIn(0, maxY)
                                windowManager.updateViewLayout(this@apply, params)
                            }
                        },
                        onInputSelected = { inputType ->
                            openInputBottomSheet(inputType = inputType)
                        },
                        onSelectAllContext = {
                            settings.isScreenGrabActive = true
                            settings.isAppTreeActive = true
                            settings.isHighlightedTextActive = true
                            settings.isMediaActive = true
                        },
                        onSelectNoneContext = {
                            settings.isScreenGrabActive = false
                            settings.isAppTreeActive = false
                            settings.isHighlightedTextActive = false
                            settings.isMediaActive = false
                        },
                        onTogglePointMode = {
                            if (_activeSelectionBounds.value != null) {
                                // Clear existing selection per spec
                                _activeSelectionBounds.value = null
                                _isPointModeActive.value = false
                            } else {
                                launchPointSelectionOverlay()
                            }
                        }
                    )
                }
            }
        }

        windowManager.addView(toolbarView, toolbarParams)
    }

    fun launchPointSelectionOverlay() {
        if (!canDrawOverlays() || pointOverlayView != null) return

        _isPointModeActive.value = true

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        pointLifecycle = OverlayLifecycleOwner()
        pointOverlayView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(pointLifecycle)
            setViewTreeViewModelStoreOwner(pointLifecycle)
            setViewTreeSavedStateRegistryOwner(pointLifecycle)

            setContent {
                CaptureEngineTheme {
                    PointSelectionOverlay(
                        onSelectionComplete = { rect ->
                            _activeSelectionBounds.value = rect
                            _isPointModeActive.value = false
                            dismissPointSelectionOverlay()
                        },
                        onCancel = {
                            _isPointModeActive.value = false
                            dismissPointSelectionOverlay()
                        }
                    )
                }
            }
        }

        windowManager.addView(pointOverlayView, params)
    }

    fun dismissPointSelectionOverlay() {
        pointOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing point overlay", e)
            }
            pointLifecycle?.destroy()
            pointOverlayView = null
            pointLifecycle = null
        }
    }

    fun openInputBottomSheet(
        inputType: String = "text",
        prefilledText: String? = null,
        attachmentBase64: String? = null,
        attachmentMime: String? = null
    ) {
        if (!canDrawOverlays() || bottomSheetView != null) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        bottomSheetLifecycle = OverlayLifecycleOwner()
        bottomSheetView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(bottomSheetLifecycle)
            setViewTreeViewModelStoreOwner(bottomSheetLifecycle)
            setViewTreeSavedStateRegistryOwner(bottomSheetLifecycle)

            setContent {
                CaptureEngineTheme {
                    InputBottomSheet(
                        initialTab = inputType,
                        prefilledText = prefilledText,
                        attachmentBase64 = attachmentBase64,
                        attachmentMime = attachmentMime,
                        onDismiss = { dismissBottomSheet() },
                        onSend = { type, textContent, audioBase64, attBase64, attMime ->
                            scope.launch {
                                dismissBottomSheet()
                                triggerCapture(
                                    inputType = type,
                                    inputContent = textContent,
                                    audioBase64 = audioBase64,
                                    attachmentBase64 = attBase64,
                                    attachmentMime = attMime,
                                    source = "floating_overlay"
                                )
                            }
                        }
                    )
                }
            }
        }

        windowManager.addView(bottomSheetView, params)
    }

    fun dismissBottomSheet() {
        bottomSheetView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing bottom sheet view", e)
            }
            bottomSheetLifecycle?.destroy()
            bottomSheetView = null
            bottomSheetLifecycle = null
        }
    }

    /**
     * Executes context capture:
     * 1. Instantly hides all HUD overlays so they are NOT in the screenshot.
     * 2. Takes screenshot & extracts app tree, focused text, media metadata.
     * 3. Immediately restores HUD overlay visibility.
     * 4. Assembles JSON payload, saves to database, and posts to Webhook.
     */
    suspend fun triggerCapture(
        inputType: String,
        inputContent: String? = null,
        audioBase64: String? = null,
        attachmentBase64: String? = null,
        attachmentMime: String? = null,
        source: String = "floating_overlay",
        prefilledHighlightedText: String? = null
    ) = withContext(Dispatchers.Main) {
        val app = CaptureApplication.instance
        val dao = app.database.captureDao()
        val client = WebhookClient()

        // 1. Hide all overlays before taking screenshot (per UX decision)
        val toolbarWasVisible = toolbarView?.visibility == View.VISIBLE
        toolbarView?.visibility = View.GONE
        bottomSheetView?.visibility = View.GONE
        pointOverlayView?.visibility = View.GONE

        // Give window manager a frame to clear the overlay
        delay(60)

        val cropRect = _activeSelectionBounds.value
        val screenGrabEnabled = settings.isScreenGrabActive
        val appTreeEnabled = settings.isAppTreeActive
        val highlightedTextEnabled = settings.isHighlightedTextActive
        val mediaEnabled = settings.isMediaActive

        // 2. Perform Capture
        val screenshotBase64 = if (screenGrabEnabled && ScreenCaptureService.isReady) {
            ScreenCaptureService.instance?.captureScreenBase64(cropRect)
        } else null

        // 3. Restore overlay visibility immediately
        if (toolbarWasVisible) {
            toolbarView?.visibility = View.VISIBLE
        }

        // Reset point selection if it was one-shot per spec
        val selectionString = cropRect?.let { "${it.left},${it.top},${it.right},${it.bottom}" }
        _activeSelectionBounds.value = null

        // Background parsing for app tree and media to avoid blocking UI
        withContext(Dispatchers.IO) {
            val accessibility = CaptureAccessibilityService.instance
            val packageName = accessibility?.currentPackageName

            val appTree = if (appTreeEnabled && accessibility != null) {
                accessibility.extractAppTree(
                    fullRaw = settings.isFullAppTreeEnabled,
                    targetBounds = cropRect
                )
            } else null

            val highlightedText = prefilledHighlightedText
                ?: if (highlightedTextEnabled && accessibility != null) {
                    accessibility.extractHighlightedText()
                } else null

            val mediaContext = if (mediaEnabled) {
                MediaMetadataListener.instance?.getCurrentMediaContext()
            } else null

            val activeSources = ActiveContextSources(
                screenGrab = screenGrabEnabled,
                appTree = appTreeEnabled,
                highlightedText = highlightedTextEnabled,
                media = mediaEnabled
            )

            // 4. Build JSON Payload
            val payload = PayloadBuilder.build(
                type = inputType,
                source = source,
                packageName = packageName,
                inputContent = inputContent,
                activeContextSources = activeSources,
                mediaContext = mediaContext,
                highlightedText = highlightedText,
                targetedSelection = selectionString,
                appTreeText = appTree,
                screenshotBase64 = screenshotBase64,
                audioBase64 = audioBase64,
                attachmentBase64 = attachmentBase64,
                attachmentMime = attachmentMime
            )

            val rawJson = PayloadBuilder.toJson(payload)

            // 5. Save to Room database (Status: SENDING)
            val captureEntity = CaptureEntity(
                type = inputType,
                source = source,
                packageName = packageName,
                inputPreview = inputContent ?: (if (audioBase64 != null) "[Audio Recording]" else "[Context Grab]"),
                hasScreenGrab = screenGrabEnabled && screenshotBase64 != null,
                hasAppTree = appTreeEnabled && !appTree.isNullOrBlank(),
                hasHighlightedText = highlightedTextEnabled && !highlightedText.isNullOrBlank(),
                hasMedia = mediaEnabled && mediaContext != null,
                rawJson = rawJson,
                status = DeliveryStatus.SENDING
            )

            val insertId = dao.insert(captureEntity)

            // 6. Send to Webhook
            val result = client.sendPayload(
                url = settings.webhookUrl,
                jsonPayload = rawJson,
                apiKey = settings.apiKey.ifBlank { null }
            )

            // 7. Update database with delivery result
            val updated = captureEntity.copy(
                id = insertId,
                status = if (result.isSuccess) DeliveryStatus.SUCCESS else DeliveryStatus.ERROR,
                responseCode = result.statusCode,
                responseTimeMs = result.responseTimeMs,
                errorMessage = result.errorMessage
            )
            dao.update(updated)
        }
    }

    fun hideAll() {
        try {
            toolbarView?.let { windowManager.removeView(it) }
            toolbarLifecycle?.destroy()
            toolbarView = null
            toolbarLifecycle = null

            dismissBottomSheet()
            dismissPointSelectionOverlay()
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding all overlays", e)
        }
    }

    companion object {
        private const val TAG = "OverlayManager"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: OverlayManager? = null

        fun getInstance(context: Context): OverlayManager {
            return INSTANCE ?: synchronized(this) {
                val instance = OverlayManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
