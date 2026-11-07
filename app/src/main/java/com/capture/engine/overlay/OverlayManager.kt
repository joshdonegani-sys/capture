package com.capture.engine.overlay

import android.annotation.SuppressLint
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import com.capture.engine.ui.CameraAnnotateActivity
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.capture.engine.model.ShareContext
import com.capture.engine.network.WebhookClient
import com.capture.engine.services.CaptureAccessibilityService
import com.capture.engine.services.MediaMetadataListener
import com.capture.engine.services.ScreenCaptureService
import com.capture.engine.ui.theme.CaptureEngineTheme
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val onBackPressedDispatcher = OnBackPressedDispatcher()

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

    private var subMenuView: ComposeView? = null
    private var subMenuParams: WindowManager.LayoutParams? = null
    private var subMenuLifecycle: OverlayLifecycleOwner? = null
    private val _expandedTarget = MutableStateFlow(ExpandedTarget.NONE)

    private var bottomSheetView: ComposeView? = null
    private var bottomSheetLifecycle: OverlayLifecycleOwner? = null

    private var pointOverlayView: ComposeView? = null
    private var pointLifecycle: OverlayLifecycleOwner? = null

    private var dormantPlacementView: ComposeView? = null
    private var dormantPlacementLifecycle: OverlayLifecycleOwner? = null

    // State
    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded

    private val _isPointModeActive = MutableStateFlow(false)
    val isPointModeActive: StateFlow<Boolean> = _isPointModeActive

    private val _activeSelectionBounds = MutableStateFlow<Rect?>(null)
    val activeSelectionBounds: StateFlow<Rect?> = _activeSelectionBounds

    private var baseAnchorX: Int = 16
    private var baseAnchorY: Int = 100
    private var awakeAnchorX: Int = settings.toolbarAwakePosX
    private var awakeAnchorY: Int = settings.toolbarAwakePosY
    private val windowXFlow = MutableStateFlow(16)
    private val windowYFlow = MutableStateFlow(100)
    private var toolbarDockState: DockState? = null

    private var lastScreenWidth: Int = 0
    private var lastScreenHeight: Int = 0
    private val screenWidthFlow = MutableStateFlow(context.resources.displayMetrics.widthPixels)
    private val screenHeightFlow = MutableStateFlow(context.resources.displayMetrics.heightPixels)

    fun getScreenDimensions(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    private val componentCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            handleConfigurationChanged()
        }
        override fun onLowMemory() {}
    }

    private var sleepJob: Job? = null

    init {
        val (initW, initH) = getScreenDimensions()
        lastScreenWidth = initW
        lastScreenHeight = initH
        screenWidthFlow.value = initW
        screenHeightFlow.value = initH

        try {
            context.registerComponentCallbacks(componentCallbacks)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering ComponentCallbacks", e)
        }

        scope.launch {
            var prevForcePreview = settings.toolbarForceOpenForPreview
            kotlinx.coroutines.flow.merge(
                settings.toolbarForceOpenForPreviewFlow,
                settings.toolbarSleepAfterMinutesFlow,
                settings.toolbarBaseLayoutFlow,
                settings.toolbarSubLayoutFlow,
                settings.toolbarSubDirectionFlow,
                settings.toolbarSubAlignFlow,
                settings.toolbarSubAlignModifierFlow,
                settings.toolbarStayOnScreenFlow,
                settings.toolbarIconSizeFlow,
                settings.toolbarAlphaFlow,
                settings.toolbarColorRoleFlow,
                settings.toolbarCustomSeedColorFlow,
                settings.toolbarTonalElevationFlow,
                settings.toolbarShadowElevationFlow,
                settings.toolbarAwakeStyleFlow,
                settings.toolbarAwakePositionFlow,
                settings.toolbarAwakeLockedFlow,
                settings.toolbarAwakePosXFlow,
                settings.toolbarAwakePosYFlow,
                settings.toolbarDormantStyleFlow,
                settings.toolbarDormantPositionModeFlow,
                settings.toolbarDormantPosXFlow,
                settings.toolbarDormantPosYFlow,
                settings.toolbarCustomIconsFlow,
                settings.lastUsedInputTypeFlow
            ).collect {
                val currentForcePreview = settings.toolbarForceOpenForPreview
                if (currentForcePreview) {
                    sleepJob?.cancel()
                    sleepJob = null
                    if (toolbarView == null && canDrawOverlays()) {
                        showToolbar()
                    }
                    _isExpanded.value = true
                    _expandedTarget.value = ExpandedTarget.JOINED_BOTH
                    if (settings.toolbarDormantPositionMode == "defined") {
                        animateToolbarPosition(awakeAnchorX, awakeAnchorY)
                    }
                    toolbarView?.post {
                        updateSubMenuPlacement()
                    } ?: updateSubMenuPlacement()
                } else {
                    if (prevForcePreview) {
                        dismissSubMenu()
                        if (_isExpanded.value) {
                            resetSleepTimer()
                        }
                    } else if (subMenuView != null && _expandedTarget.value != ExpandedTarget.NONE) {
                        toolbarView?.post {
                            updateSubMenuPlacement()
                        } ?: updateSubMenuPlacement()
                    } else if (_isExpanded.value) {
                        resetSleepTimer()
                    }
                }
                if (settings.toolbarAwakePosition == "always docked" && toolbarView != null) {
                    val (curW, curH) = getScreenDimensions()
                    val w = toolbarView?.width ?: 0
                    val h = toolbarView?.height ?: 0
                    if (w > 0 && h > 0) {
                        val maxX = (curW - w).coerceAtLeast(0)
                        val maxY = (curH - h - 70).coerceAtLeast(0)
                        val (snappedX, snappedY) = ToolbarDockingEngine.snapToNearestEdge(baseAnchorX, baseAnchorY, maxX, maxY)
                        if (snappedX != baseAnchorX || snappedY != baseAnchorY) {
                            baseAnchorX = snappedX
                            baseAnchorY = snappedY
                            toolbarParams?.let { p ->
                                p.x = snappedX
                                p.y = snappedY
                                try {
                                    windowManager.updateViewLayout(toolbarView, p)
                                } catch (e: Exception) {}
                            }
                            if (_isExpanded.value) {
                                awakeAnchorX = snappedX
                                awakeAnchorY = snappedY
                                settings.toolbarAwakePosX = snappedX
                                settings.toolbarAwakePosY = snappedY
                            }
                            windowXFlow.value = snappedX
                            windowYFlow.value = snappedY
                        }
                    }
                }
                prevForcePreview = currentForcePreview
            }
        }
    }

    private fun handleConfigurationChanged() {
        scope.launch {
            var dims = getScreenDimensions()
            var newW = dims.first
            var newH = dims.second

            if (newW == lastScreenWidth && newH == lastScreenHeight) {
                delay(60)
                dims = getScreenDimensions()
                newW = dims.first
                newH = dims.second
            }

            if (newW != lastScreenWidth || newH != lastScreenHeight) {
                onScreenRotated(newW, newH)
            }
        }
    }

    fun onScreenRotated(newScreenWidth: Int, newScreenHeight: Int) {
        val oldW = lastScreenWidth
        val oldH = lastScreenHeight
        lastScreenWidth = newScreenWidth
        lastScreenHeight = newScreenHeight
        screenWidthFlow.value = newScreenWidth
        screenHeightFlow.value = newScreenHeight

        Log.d(TAG, "onScreenRotated: (${oldW}x${oldH}) -> (${newScreenWidth}x${newScreenHeight})")

        if (oldW <= 0 || oldH <= 0) return

        val toolbar = toolbarView ?: return
        val params = toolbarParams ?: return

        val viewW = if (toolbar.width > 0) toolbar.width else (50 * context.resources.displayMetrics.density).toInt()
        val viewH = if (toolbar.height > 0) toolbar.height else (50 * context.resources.displayMetrics.density).toInt()

        val oldMaxX = (oldW - viewW).coerceAtLeast(0)
        val oldMaxY = (oldH - viewH - 70).coerceAtLeast(0)
        val newMaxX = (newScreenWidth - viewW).coerceAtLeast(0)
        val newMaxY = (newScreenHeight - viewH - 70).coerceAtLeast(0)

        val positionBehavior = if (_isExpanded.value || settings.toolbarDormantPositionMode == "off") {
            settings.toolbarAwakePosition
        } else {
            "free"
        }

        val newDockState = ToolbarDockingEngine.onScreenRotation(
            currentVisualX = baseAnchorX,
            currentVisualY = baseAnchorY,
            oldMaxX = oldMaxX,
            oldMaxY = oldMaxY,
            newMaxX = newMaxX,
            newMaxY = newMaxY,
            positionBehavior = positionBehavior,
            currentEdge = toolbarDockState?.currentDockEdge,
            edgeTolerancePx = (20 * context.resources.displayMetrics.density)
        )

        toolbarDockState = newDockState
        baseAnchorX = newDockState.visualX
        baseAnchorY = newDockState.visualY
        params.x = baseAnchorX
        params.y = baseAnchorY
        windowXFlow.value = baseAnchorX
        windowYFlow.value = baseAnchorY

        try {
            windowManager.updateViewLayout(toolbar, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating toolbar layout on rotation", e)
        }

        if (_isExpanded.value) {
            awakeAnchorX = baseAnchorX
            awakeAnchorY = baseAnchorY
            settings.toolbarAwakePosX = baseAnchorX
            settings.toolbarAwakePosY = baseAnchorY

            if (settings.toolbarDormantPositionMode == "defined") {
                val dormantRotated = ToolbarDockingEngine.onScreenRotation(
                    currentVisualX = settings.toolbarDormantPosX,
                    currentVisualY = settings.toolbarDormantPosY,
                    oldMaxX = oldMaxX,
                    oldMaxY = oldMaxY,
                    newMaxX = newMaxX,
                    newMaxY = newMaxY,
                    positionBehavior = "free"
                )
                settings.toolbarDormantPosX = dormantRotated.visualX
                settings.toolbarDormantPosY = dormantRotated.visualY
            }
        } else {
            if (settings.toolbarDormantPositionMode == "defined") {
                settings.toolbarDormantPosX = baseAnchorX
                settings.toolbarDormantPosY = baseAnchorY
            }
            val awakeRotated = ToolbarDockingEngine.onScreenRotation(
                currentVisualX = awakeAnchorX,
                currentVisualY = awakeAnchorY,
                oldMaxX = oldMaxX,
                oldMaxY = oldMaxY,
                newMaxX = newMaxX,
                newMaxY = newMaxY,
                positionBehavior = settings.toolbarAwakePosition
            )
            awakeAnchorX = awakeRotated.visualX
            awakeAnchorY = awakeRotated.visualY
            settings.toolbarAwakePosX = awakeRotated.visualX
            settings.toolbarAwakePosY = awakeRotated.visualY
        }

        if (_expandedTarget.value != ExpandedTarget.NONE) {
            updateSubMenuPlacement()
        }
    }

    fun resetSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null

        if (!_isExpanded.value || settings.toolbarForceOpenForPreview || bottomSheetView != null) {
            return
        }

        val minutes = settings.toolbarSleepAfterMinutes
        if (minutes <= 0) return

        val durationMs = minutes * 60 * 1000L
        sleepJob = scope.launch {
            delay(durationMs)
            if (_isExpanded.value && !settings.toolbarForceOpenForPreview && bottomSheetView == null) {
                collapseToolbarToDormant()
            }
        }
    }

    private var positionAnimJob: Job? = null

    fun animateToolbarPosition(targetX: Int, targetY: Int, durationMs: Long = 260L) {
        positionAnimJob?.cancel()
        val currentX = toolbarParams?.x ?: targetX
        val currentY = toolbarParams?.y ?: targetY
        Log.d(TAG, "animateToolbarPosition from ($currentX, $currentY) to ($targetX, $targetY), isExpanded=${_isExpanded.value}")
        if (currentX == targetX && currentY == targetY) {
            baseAnchorX = targetX
            baseAnchorY = targetY
            windowXFlow.value = targetX
            windowYFlow.value = targetY
            if (_expandedTarget.value != ExpandedTarget.NONE) {
                updateSubMenuPlacement()
            }
            return
        }

        positionAnimJob = scope.launch {
            val startTime = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val fraction = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                val ease = fraction * fraction * (3f - 2f * fraction)
                val newX = (currentX + (targetX - currentX) * ease).toInt()
                val newY = (currentY + (targetY - currentY) * ease).toInt()

                baseAnchorX = newX
                baseAnchorY = newY
                toolbarParams?.let { params ->
                    params.x = newX
                    params.y = newY
                    windowXFlow.value = newX
                    windowYFlow.value = newY
                    toolbarView?.let { v ->
                        try {
                            windowManager.updateViewLayout(v, params)
                        } catch (e: Exception) {}
                    }
                }
                if (_expandedTarget.value != ExpandedTarget.NONE) {
                    updateSubMenuPlacement()
                }
                if (fraction >= 1f) break
                delay(16)
            }
        }
    }

    fun collapseToolbarToDormant() {
        sleepJob?.cancel()
        sleepJob = null
        _isExpanded.value = false
        dismissSubMenu()
        Log.d(TAG, "collapseToolbarToDormant mode=${settings.toolbarDormantPositionMode} target=(${settings.toolbarDormantPosX}, ${settings.toolbarDormantPosY})")
        if (settings.toolbarDormantPositionMode == "defined") {
            animateToolbarPosition(settings.toolbarDormantPosX, settings.toolbarDormantPosY)
        }
    }

    fun updateDormantPosition(x: Int, y: Int) {
        if (!_isExpanded.value && settings.toolbarDormantPositionMode == "defined") {
            animateToolbarPosition(x, y)
        }
    }

    fun setToolbarVisible(visible: Boolean) {
        toolbarView?.visibility = if (visible) View.VISIBLE else View.GONE
        subMenuView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun wakeToolbar() {
        if (toolbarView == null && canDrawOverlays() && settings.isOverlayEnabled) {
            showToolbar()
        }
        _isExpanded.value = true
        dismissSubMenu()
        resetSleepTimer()
        Log.d(TAG, "wakeToolbar mode=${settings.toolbarDormantPositionMode} target=($awakeAnchorX, $awakeAnchorY)")
        if (settings.toolbarDormantPositionMode == "defined") {
            animateToolbarPosition(awakeAnchorX, awakeAnchorY)
        }
    }

    fun toggleSleepWake() {
        if (_isExpanded.value) {
            collapseToolbarToDormant()
        } else {
            wakeToolbar()
        }
    }

    fun canDrawOverlays(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun dismissSubMenu() {
        _expandedTarget.value = ExpandedTarget.NONE
        subMenuView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {}
            subMenuLifecycle?.destroy()
            subMenuView = null
            subMenuLifecycle = null
            subMenuParams = null
        }
    }

    fun updateSubMenuPlacement() {
        val target = _expandedTarget.value
        if (target == ExpandedTarget.NONE) {
            dismissSubMenu()
            return
        }
        if (!canDrawOverlays()) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val (screenWidth, screenHeight) = getScreenDimensions()

        val density = context.resources.displayMetrics.density
        val isBaseHoriz = settings.toolbarBaseLayout == "horiz"
        val isAllIcons = settings.toolbarAwakeStyle == "all icons"
        val baseButtonSizeDp = (settings.toolbarIconSize + 10f).coerceIn(28f, 36f)
        val subButtonSizeDp = (settings.toolbarIconSize + 8f).coerceIn(28f, 36f)

        val shadowMarginPx = (12 * density).toInt()

        // Visible Base pill dimensions
        val customIcons = settings.toolbarCustomIcons
        val customCount = customIcons.size.coerceAtLeast(1)

        val baseWDp = if (isAllIcons) {
            val maxRowW = 6 * (baseButtonSizeDp + 2f) + 8f
            val stackedH = baseButtonSizeDp * 2 + 15f
            if (isBaseHoriz) maxRowW else stackedH
        } else {
            if (isBaseHoriz) (customCount * (baseButtonSizeDp + 3f) + 8f) else (baseButtonSizeDp + 8f)
        }

        val baseHDp = if (isAllIcons) {
            val maxRowW = 6 * (baseButtonSizeDp + 2f) + 8f
            val stackedH = baseButtonSizeDp * 2 + 15f
            if (isBaseHoriz) stackedH else maxRowW
        } else {
            if (isBaseHoriz) (baseButtonSizeDp + 8f) else (customCount * (baseButtonSizeDp + 3f) + 8f)
        }

        val visibleBaseWPx = toolbarView?.width?.takeIf { it > shadowMarginPx * 2 }?.minus(shadowMarginPx * 2) ?: (baseWDp * density).toInt()
        val visibleBaseHPx = toolbarView?.height?.takeIf { it > shadowMarginPx * 2 }?.minus(shadowMarginPx * 2) ?: (baseHDp * density).toInt()

        // Visible Sub pill dimensions (6x2 grid or 2x6 grid)
        val isFlow = settings.toolbarSubLayout == "flow axis"
        val isSubHoriz = if (isFlow) isBaseHoriz else !isBaseHoriz
        val count = 6

        val gridLongEdgeDp = count * subButtonSizeDp + (count - 1) * 2f + 8f
        val gridShortEdgeDp = subButtonSizeDp * 2 + 3f + 1f + 3f + 8f // 2 buttons + divider + spacing + padding = buttonSize * 2 + 15f

        val visibleSubWDp = if (isSubHoriz) gridLongEdgeDp else gridShortEdgeDp
        val visibleSubHDp = if (isSubHoriz) gridShortEdgeDp else gridLongEdgeDp

        val visibleSubWPx = subMenuView?.width?.takeIf { it > shadowMarginPx * 2 }?.minus(shadowMarginPx * 2) ?: (visibleSubWDp * density).toInt()
        val visibleSubHPx = subMenuView?.height?.takeIf { it > shadowMarginPx * 2 }?.minus(shadowMarginPx * 2) ?: (visibleSubHDp * density).toInt()

        val currentToolbarX = toolbarParams?.x ?: this.baseAnchorX
        val currentToolbarY = toolbarParams?.y ?: this.baseAnchorY
        val basePillX = currentToolbarX + shadowMarginPx
        val basePillY = currentToolbarY + shadowMarginPx

        val placement = calculateSubPlacement(
            baseAnchorX = basePillX,
            baseAnchorY = basePillY,
            baseW = visibleBaseWPx,
            baseH = visibleBaseHPx,
            subW = visibleSubWPx,
            subH = visibleSubHPx,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            baseLayout = settings.toolbarBaseLayout,
            subLayout = settings.toolbarSubLayout,
            subDirection = settings.toolbarSubDirection,
            subAlign = settings.toolbarSubAlign,
            subAlignModifier = settings.toolbarSubAlignModifier,
            expandedTarget = target,
            stayOnScreen = settings.toolbarStayOnScreen,
            gapPx = (16 * density).toInt(),
            screenMargin = (16 * density).toInt()
        )

        val targetWindowX = placement.x - shadowMarginPx
        val targetWindowY = placement.y - shadowMarginPx

        if (subMenuView == null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = targetWindowX
                y = targetWindowY
            }
            subMenuParams = params

            val lifecycle = OverlayLifecycleOwner()
            subMenuLifecycle = lifecycle
            subMenuView = ComposeView(context).apply {
                clipChildren = false
                clipToPadding = false
                setViewTreeLifecycleOwner(lifecycle)
                setViewTreeViewModelStoreOwner(lifecycle)
                setViewTreeSavedStateRegistryOwner(lifecycle)

                addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    val w = right - left
                    val h = bottom - top
                    val oldW = oldRight - oldLeft
                    val oldH = oldBottom - oldTop
                    if ((w != oldW || h != oldH) && w > 0 && h > 0 && _expandedTarget.value != ExpandedTarget.NONE) {
                        updateSubMenuPlacement()
                    }
                }

                setOnTouchListener { _, event ->
                    resetSleepTimer()
                    if (event.action == MotionEvent.ACTION_OUTSIDE) {
                        if (!settings.toolbarForceOpenForPreview) {
                            dismissSubMenu()
                        }
                        true
                    } else {
                        false
                    }
                }

                setContent {
                    CaptureEngineTheme {
                        FloatingSubMenu(
                            expandedTarget = _expandedTarget,
                            isPointActive = _isPointModeActive,
                            hasActiveSelection = _activeSelectionBounds,
                            onDismiss = {
                                resetSleepTimer()
                                if (!settings.toolbarForceOpenForPreview) {
                                    dismissSubMenu()
                                }
                            },
                            onInputSelected = { inputType ->
                                resetSleepTimer()
                                if (!settings.toolbarForceOpenForPreview) {
                                    dismissSubMenu()
                                }
                                settings.lastUsedInputType = inputType
                                if (inputType == "point_mode") {
                                    if (_activeSelectionBounds.value != null || pointOverlayView != null) {
                                        _activeSelectionBounds.value = null
                                        _isPointModeActive.value = false
                                        dismissPointSelectionOverlay()
                                        dismissBottomSheet()
                                    } else {
                                        launchPointSelectionOverlay()
                                    }
                                } else if (inputType == "camera") {
                                    launchCameraAnnotate()
                                } else {
                                    openInputBottomSheet(inputType = inputType, showInputTypeTabs = false)
                                }
                            },
                            onTogglePointMode = {
                                resetSleepTimer()
                                if (!settings.toolbarForceOpenForPreview) {
                                    dismissSubMenu()
                                }
                                if (_activeSelectionBounds.value != null || pointOverlayView != null) {
                                    _activeSelectionBounds.value = null
                                    _isPointModeActive.value = false
                                    dismissPointSelectionOverlay()
                                    dismissBottomSheet()
                                } else {
                                    launchPointSelectionOverlay()
                                }
                            },
                            onSelectAllContext = {
                                resetSleepTimer()
                                settings.isScreenGrabActive = true
                                settings.isAppTreeActive = true
                                settings.isHighlightedTextActive = true
                                settings.isMediaActive = true
                            },
                            onSelectNoneContext = {
                                resetSleepTimer()
                                settings.isScreenGrabActive = false
                                settings.isAppTreeActive = false
                                settings.isHighlightedTextActive = false
                                settings.isMediaActive = false
                            }
                        )
                    }
                }
            }

            try {
                windowManager.addView(subMenuView, params)
            } catch (e: Exception) {
                Log.e(TAG, "Error adding sub menu view", e)
            }
        } else {
            subMenuParams?.let { params ->
                params.x = targetWindowX
                params.y = targetWindowY
                try {
                    windowManager.updateViewLayout(subMenuView, params)
                } catch (e: Exception) {}
            }
        }
    }

    fun toggleSubMenu(target: ExpandedTarget) {
        resetSleepTimer()
        val effectiveTarget = target

        if (_expandedTarget.value == effectiveTarget) {
            if (!settings.toolbarForceOpenForPreview) {
                dismissSubMenu()
            }
        } else {
            _expandedTarget.value = effectiveTarget
            updateSubMenuPlacement()
        }
    }

    fun showToolbar() {
        if (!canDrawOverlays() || toolbarView != null) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val (screenWidth, screenHeight) = getScreenDimensions()
        lastScreenWidth = screenWidth
        lastScreenHeight = screenHeight
        screenWidthFlow.value = screenWidth
        screenHeightFlow.value = screenHeight

        val defaultAwakeY = (screenHeight * 0.45f).toInt()
        awakeAnchorX = settings.toolbarAwakePosX
        awakeAnchorY = if (settings.toolbarAwakePosY == 500 && settings.toolbarAwakePosX == 16) {
            defaultAwakeY
        } else {
            settings.toolbarAwakePosY
        }

        val initialX = if (settings.toolbarDormantPositionMode == "defined" && !_isExpanded.value) {
            settings.toolbarDormantPosX
        } else {
            awakeAnchorX
        }
        val initialY = if (settings.toolbarDormantPositionMode == "defined" && !_isExpanded.value) {
            settings.toolbarDormantPosY
        } else {
            awakeAnchorY
        }

        baseAnchorX = initialX
        baseAnchorY = initialY
        windowXFlow.value = initialX
        windowYFlow.value = initialY

        toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        toolbarLifecycle = OverlayLifecycleOwner()
        toolbarView = ComposeView(context).apply {
            clipChildren = false
            clipToPadding = false
            setViewTreeLifecycleOwner(toolbarLifecycle)
            setViewTreeViewModelStoreOwner(toolbarLifecycle)
            setViewTreeSavedStateRegistryOwner(toolbarLifecycle)

            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val w = right - left
                val h = bottom - top
                val oldW = oldRight - oldLeft
                val oldH = oldBottom - oldTop
                if ((w != oldW || h != oldH) && w > 0 && h > 0 && _expandedTarget.value != ExpandedTarget.NONE) {
                    updateSubMenuPlacement()
                }
            }

            setContent {
                val currentX by windowXFlow.collectAsState()
                val currentY by windowYFlow.collectAsState()
                val curScreenWidth by screenWidthFlow.collectAsState()
                val curScreenHeight by screenHeightFlow.collectAsState()

                CaptureEngineTheme {
                    FloatingToolbar(
                        isExpanded = _isExpanded,
                        isPointActive = _isPointModeActive,
                        hasActiveSelection = _activeSelectionBounds,
                        expandedTarget = _expandedTarget,
                        windowPosX = currentX,
                        windowPosY = currentY,
                        screenWidth = curScreenWidth,
                        screenHeight = curScreenHeight,
                        onExpandToggle = {
                            val newExpanded = !_isExpanded.value
                            _isExpanded.value = newExpanded
                            dismissSubMenu()
                            Log.d(TAG, "onExpandToggle newExpanded=$newExpanded mode=${settings.toolbarDormantPositionMode} awakeAnchor=($awakeAnchorX, $awakeAnchorY) dormant=(${settings.toolbarDormantPosX}, ${settings.toolbarDormantPosY})")
                            if (newExpanded) {
                                resetSleepTimer()
                                if (settings.toolbarDormantPositionMode == "defined") {
                                    animateToolbarPosition(awakeAnchorX, awakeAnchorY)
                                }
                            } else {
                                collapseToolbarToDormant()
                            }
                        },
                        onCollapse = {
                            collapseToolbarToDormant()
                        },
                        onFlickSleep = {
                            collapseToolbarToDormant()
                        },
                        onInputClick = {
                            resetSleepTimer()
                            if (!settings.toolbarForceOpenForPreview) {
                                dismissSubMenu()
                            }
                            val inputType = settings.lastUsedInputType
                            if (inputType == "point_mode") {
                                if (_activeSelectionBounds.value != null || pointOverlayView != null) {
                                    _activeSelectionBounds.value = null
                                    _isPointModeActive.value = false
                                    dismissPointSelectionOverlay()
                                    dismissBottomSheet()
                                } else {
                                    launchPointSelectionOverlay()
                                }
                            } else if (inputType == "camera") {
                                launchCameraAnnotate()
                            } else {
                                openInputBottomSheet(inputType = inputType, showInputTypeTabs = false)
                            }
                        },
                        onInputLongClick = {
                            resetSleepTimer()
                            toggleSubMenu(ExpandedTarget.JOINED_BOTH)
                        },
                        onContextClick = {
                            resetSleepTimer()
                            toggleSubMenu(ExpandedTarget.JOINED_BOTH)
                        },
                        onContextLongClick = {
                            resetSleepTimer()
                            toggleSubMenu(ExpandedTarget.JOINED_BOTH)
                        },
                        onInputSubSelected = { inputType ->
                            resetSleepTimer()
                            if (!settings.toolbarForceOpenForPreview) {
                                dismissSubMenu()
                            }
                            settings.lastUsedInputType = inputType
                            if (inputType == "point_mode") {
                                if (_activeSelectionBounds.value != null || pointOverlayView != null) {
                                    _activeSelectionBounds.value = null
                                    _isPointModeActive.value = false
                                    dismissPointSelectionOverlay()
                                    dismissBottomSheet()
                                } else {
                                    launchPointSelectionOverlay()
                                }
                            } else if (inputType == "camera") {
                                launchCameraAnnotate()
                            } else {
                                openInputBottomSheet(inputType = inputType, showInputTypeTabs = false)
                            }
                        },
                        onSelectAllContext = {
                            resetSleepTimer()
                            settings.isScreenGrabActive = true
                            settings.isAppTreeActive = true
                            settings.isHighlightedTextActive = true
                            settings.isMediaActive = true
                        },
                        onSelectNoneContext = {
                            resetSleepTimer()
                            settings.isScreenGrabActive = false
                            settings.isAppTreeActive = false
                            settings.isHighlightedTextActive = false
                            settings.isMediaActive = false
                        },
                        onTogglePointMode = {
                            resetSleepTimer()
                            dismissSubMenu()
                            if (_activeSelectionBounds.value != null || pointOverlayView != null) {
                                _activeSelectionBounds.value = null
                                _isPointModeActive.value = false
                                dismissPointSelectionOverlay()
                                dismissBottomSheet()
                            } else {
                                launchPointSelectionOverlay()
                            }
                        },
                        onDragStart = {
                            toolbarParams?.let { params ->
                                val (curScreenWidth, curScreenHeight) = getScreenDimensions()
                                val w = toolbarView?.width ?: 0
                                val h = toolbarView?.height ?: 0
                                val maxX = (curScreenWidth - w).coerceAtLeast(0)
                                val maxY = (curScreenHeight - h - 70).coerceAtLeast(0)
                                val density = context.resources.displayMetrics.density
                                val dockDistancePx = (70 * density)
                                val edgeTolerancePx = (20 * density)

                                val positionBehavior = if (_isExpanded.value || settings.toolbarDormantPositionMode == "off") {
                                    settings.toolbarAwakePosition
                                } else {
                                    "free"
                                }

                                toolbarDockState = ToolbarDockingEngine.onDragStart(
                                    initialVisualX = baseAnchorX,
                                    initialVisualY = baseAnchorY,
                                    maxX = maxX,
                                    maxY = maxY,
                                    positionBehavior = positionBehavior,
                                    dockDistancePx = dockDistancePx,
                                    edgeTolerancePx = edgeTolerancePx
                                )

                                toolbarDockState?.let { state ->
                                    baseAnchorX = state.visualX
                                    baseAnchorY = state.visualY
                                    params.x = baseAnchorX
                                    params.y = baseAnchorY
                                    try {
                                        windowManager.updateViewLayout(this@apply, params)
                                    } catch (e: Exception) {}
                                }
                            }
                        },
                        onDragEnd = {
                            toolbarParams?.let { params ->
                                val (curScreenWidth, curScreenHeight) = getScreenDimensions()
                                val w = toolbarView?.width ?: 0
                                val h = toolbarView?.height ?: 0
                                val maxX = (curScreenWidth - w).coerceAtLeast(0)
                                val maxY = (curScreenHeight - h - 70).coerceAtLeast(0)
                                val density = context.resources.displayMetrics.density
                                val dockDistancePx = (70 * density)

                                val positionBehavior = if (_isExpanded.value || settings.toolbarDormantPositionMode == "off") {
                                    settings.toolbarAwakePosition
                                } else {
                                    "free"
                                }

                                val currentState = toolbarDockState ?: ToolbarDockingEngine.onDragStart(
                                    initialVisualX = baseAnchorX,
                                    initialVisualY = baseAnchorY,
                                    maxX = maxX,
                                    maxY = maxY,
                                    positionBehavior = positionBehavior,
                                    dockDistancePx = dockDistancePx
                                )

                                val finalState = ToolbarDockingEngine.onDragEnd(
                                    currentState = currentState,
                                    maxX = maxX,
                                    maxY = maxY,
                                    positionBehavior = positionBehavior
                                )
                                toolbarDockState = null

                                baseAnchorX = finalState.visualX
                                baseAnchorY = finalState.visualY
                                params.x = baseAnchorX
                                params.y = baseAnchorY

                                if (_isExpanded.value) {
                                    awakeAnchorX = baseAnchorX
                                    awakeAnchorY = baseAnchorY
                                    settings.toolbarAwakePosX = baseAnchorX
                                    settings.toolbarAwakePosY = baseAnchorY
                                    Log.d(TAG, "onDragEnd(awake) -> updated awakeAnchor to ($baseAnchorX, $baseAnchorY)")
                                } else {
                                    if (settings.toolbarDormantPositionMode == "defined") {
                                        settings.toolbarDormantPosX = baseAnchorX
                                        settings.toolbarDormantPosY = baseAnchorY
                                        Log.d(TAG, "onDragEnd(dormant-defined) -> updated dormantPos to ($baseAnchorX, $baseAnchorY)")
                                    } else {
                                        awakeAnchorX = baseAnchorX
                                        awakeAnchorY = baseAnchorY
                                        settings.toolbarAwakePosX = baseAnchorX
                                        settings.toolbarAwakePosY = baseAnchorY
                                        Log.d(TAG, "onDragEnd(dormant-inherited) -> updated both to ($baseAnchorX, $baseAnchorY)")
                                    }
                                }

                                try {
                                    windowManager.updateViewLayout(this@apply, params)
                                } catch (e: Exception) {}

                                if (_expandedTarget.value != ExpandedTarget.NONE) {
                                    updateSubMenuPlacement()
                                }
                            }
                        },
                        onDrag = { dx, dy ->
                            if (_isExpanded.value) {
                                resetSleepTimer()
                            }
                            toolbarParams?.let { params ->
                                val (curScreenWidth, curScreenHeight) = getScreenDimensions()
                                val w = toolbarView?.width ?: 0
                                val h = toolbarView?.height ?: 0
                                val maxX = (curScreenWidth - w).coerceAtLeast(0)
                                val maxY = (curScreenHeight - h - 70).coerceAtLeast(0)
                                val density = context.resources.displayMetrics.density
                                val dockDistancePx = (70 * density)

                                val positionBehavior = if (_isExpanded.value || settings.toolbarDormantPositionMode == "off") {
                                    settings.toolbarAwakePosition
                                } else {
                                    "free"
                                }

                                val currentState = toolbarDockState ?: ToolbarDockingEngine.onDragStart(
                                    initialVisualX = baseAnchorX,
                                    initialVisualY = baseAnchorY,
                                    maxX = maxX,
                                    maxY = maxY,
                                    positionBehavior = positionBehavior,
                                    dockDistancePx = dockDistancePx
                                )

                                val nextState = ToolbarDockingEngine.onDrag(
                                    currentState = currentState,
                                    dx = dx,
                                    dy = dy,
                                    maxX = maxX,
                                    maxY = maxY,
                                    positionBehavior = positionBehavior,
                                    dockDistancePx = dockDistancePx
                                )
                                toolbarDockState = nextState

                                baseAnchorX = nextState.visualX
                                baseAnchorY = nextState.visualY

                                if (_isExpanded.value && settings.toolbarSleepGesture == "drag to edge" && positionBehavior == "free") {
                                    if (baseAnchorX <= 10 || baseAnchorX >= maxX - 10 || baseAnchorY <= 10 || baseAnchorY >= maxY - 10) {
                                        collapseToolbarToDormant()
                                    }
                                }
                                if (_isExpanded.value && settings.toolbarDormantPositionMode == "defined" && settings.toolbarSleepGesture == "drag to dormant zone") {
                                    val targetX = settings.toolbarDormantPosX
                                    val targetY = settings.toolbarDormantPosY
                                    val distSq = (baseAnchorX - targetX) * (baseAnchorX - targetX) + (baseAnchorY - targetY) * (baseAnchorY - targetY)
                                    if (distSq < 10000) {
                                        collapseToolbarToDormant()
                                    }
                                }

                                params.x = baseAnchorX
                                params.y = baseAnchorY

                                if (_isExpanded.value) {
                                    awakeAnchorX = baseAnchorX
                                    awakeAnchorY = baseAnchorY
                                    settings.toolbarAwakePosX = baseAnchorX
                                    settings.toolbarAwakePosY = baseAnchorY
                                    Log.d(TAG, "onDrag(awake) -> updated awakeAnchor to ($baseAnchorX, $baseAnchorY)")
                                } else {
                                    if (settings.toolbarDormantPositionMode == "defined") {
                                        settings.toolbarDormantPosX = baseAnchorX
                                        settings.toolbarDormantPosY = baseAnchorY
                                        Log.d(TAG, "onDrag(dormant-defined) -> updated dormantPos to ($baseAnchorX, $baseAnchorY)")
                                    } else {
                                        awakeAnchorX = baseAnchorX
                                        awakeAnchorY = baseAnchorY
                                        settings.toolbarAwakePosX = baseAnchorX
                                        settings.toolbarAwakePosY = baseAnchorY
                                        Log.d(TAG, "onDrag(dormant-inherited) -> updated both to ($baseAnchorX, $baseAnchorY)")
                                    }
                                }

                                try {
                                    windowManager.updateViewLayout(this@apply, params)
                                } catch (e: Exception) {}

                                if (_expandedTarget.value != ExpandedTarget.NONE) {
                                    updateSubMenuPlacement()
                                }

                                val isRightHalf = baseAnchorX >= (curScreenWidth / 2)
                                val isBottomHalf = baseAnchorY >= (curScreenHeight / 2)
                                val prevRightHalf = windowXFlow.value >= (curScreenWidth / 2)
                                val prevBottomHalf = windowYFlow.value >= (curScreenHeight / 2)
                                if (isRightHalf != prevRightHalf || isBottomHalf != prevBottomHalf) {
                                    windowXFlow.value = baseAnchorX
                                    windowYFlow.value = baseAnchorY
                                }
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
        _activeSelectionBounds.value = null

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
                    val activeRect by _activeSelectionBounds.collectAsState()
                    PointSelectionOverlay(
                        activeRect = activeRect,
                        onSelectionComplete = { rect ->
                            _activeSelectionBounds.value = rect
                            _isPointModeActive.value = false

                            // Make point overlay view non-touchable so touches go straight to the bottom sheet
                            val currentParams = (pointOverlayView?.layoutParams as? WindowManager.LayoutParams)?.apply {
                                flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            }
                            if (currentParams != null && pointOverlayView != null) {
                                try {
                                    windowManager.updateViewLayout(pointOverlayView, currentParams)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error updating point overlay params", e)
                                }
                            }

                            // Immediately load the input modal bottom drawer with tabs visible and last-used input active
                            openInputBottomSheet(
                                inputType = settings.lastUsedModalInputType,
                                showInputTypeTabs = true,
                                hasActiveSelection = true
                            )
                        },
                        onCancel = {
                            _isPointModeActive.value = false
                            _activeSelectionBounds.value = null
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

    fun launchDormantPlacementOverlay(
        onComplete: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ) {
        if (!canDrawOverlays()) return
        dismissDormantPlacementOverlay()

        setToolbarVisible(false)

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

        dormantPlacementLifecycle = OverlayLifecycleOwner()
        dormantPlacementView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(dormantPlacementLifecycle)
            setViewTreeViewModelStoreOwner(dormantPlacementLifecycle)
            setViewTreeSavedStateRegistryOwner(dormantPlacementLifecycle)

            setContent {
                CaptureEngineTheme {
                    DormantPlacementContent(
                        initialX = settings.toolbarDormantPosX,
                        initialY = settings.toolbarDormantPosY,
                        onConfirm = { x, y ->
                            settings.toolbarDormantPosX = x
                            settings.toolbarDormantPosY = y
                            settings.toolbarDormantPositionMode = "defined"
                            dismissDormantPlacementOverlay()
                            updateDormantPosition(x, y)
                            onComplete?.invoke()
                        },
                        onCancel = {
                            dismissDormantPlacementOverlay()
                            onCancel?.invoke()
                        }
                    )
                }
            }
        }

        try {
            windowManager.addView(dormantPlacementView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding dormant placement overlay", e)
            setToolbarVisible(true)
        }
    }

    fun dismissDormantPlacementOverlay() {
        dormantPlacementView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing dormant placement overlay", e)
            }
            dormantPlacementLifecycle?.destroy()
            dormantPlacementView = null
            dormantPlacementLifecycle = null
            setToolbarVisible(true)
        }
    }

    fun launchCameraAnnotate() {
        dismissSubMenu()
        val intent = Intent(context, CameraAnnotateActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    fun openInputBottomSheet(
        inputType: String = "text",
        showInputTypeTabs: Boolean = true,
        hasActiveSelection: Boolean = false,
        prefilledText: String? = null,
        sharedText: String? = null,
        sharedTitle: String? = null,
        attachmentBase64: String? = null,
        attachmentMime: String? = null
    ) {
        if (!canDrawOverlays() || bottomSheetView != null) return

        sleepJob?.cancel()
        sleepJob = null

        val normalizedInputType = if (inputType == "text") "text_snippet" else inputType
        settings.lastUsedInputType = normalizedInputType

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

        val lifecycle = OverlayLifecycleOwner()
        bottomSheetLifecycle = lifecycle
        bottomSheetView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeViewModelStoreOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            setViewTreeOnBackPressedDispatcherOwner(lifecycle)

            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    bottomSheetLifecycle?.onBackPressedDispatcher?.onBackPressed()
                    true
                } else {
                    false
                }
            }

            setContent {
                CaptureEngineTheme {
                    InputBottomSheet(
                        initialTab = normalizedInputType,
                        showInputTypeTabs = showInputTypeTabs,
                        hasActiveSelection = hasActiveSelection || _activeSelectionBounds.value != null,
                        prefilledText = prefilledText,
                        sharedText = sharedText,
                        sharedTitle = sharedTitle,
                        attachmentBase64 = attachmentBase64,
                        attachmentMime = attachmentMime,
                        onDismiss = {
                            dismissBottomSheet()
                            dismissPointSelectionOverlay()
                            _activeSelectionBounds.value = null
                            _isPointModeActive.value = false
                        },
                        onSend = { type, textContent, audioBase64, attBase64, attMime, sText, sTitle ->
                            settings.lastUsedInputType = type
                            scope.launch {
                                dismissBottomSheet()
                                dismissPointSelectionOverlay()
                                val shareContext = if (!sText.isNullOrBlank() || !sTitle.isNullOrBlank()) {
                                    ShareContext(
                                        text = sText,
                                        title = sTitle,
                                        mimeType = attMime
                                    )
                                } else null

                                triggerCapture(
                                    inputType = type,
                                    inputContent = textContent,
                                    shareContext = shareContext,
                                    audioBase64 = audioBase64,
                                    attachmentBase64 = attBase64,
                                    attachmentMime = attMime,
                                    source = if (shareContext != null) "system_share_sheet" else "floating_overlay"
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
        bottomSheetView?.let { v ->
            try {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(v.windowToken, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    v.windowInsetsController?.hide(WindowInsets.Type.ime())
                }
                v.clearFocus()
                windowManager.removeView(v)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing bottom sheet view", e)
            }
            bottomSheetLifecycle?.destroy()
            bottomSheetView = null
            bottomSheetLifecycle = null
        }
        if (_isExpanded.value) {
            resetSleepTimer()
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
        shareContext: ShareContext? = null,
        audioBase64: String? = null,
        attachmentBase64: String? = null,
        attachmentMime: String? = null,
        source: String = "floating_overlay",
        prefilledHighlightedText: String? = null
    ) = withContext(Dispatchers.Main) {
        val app = CaptureApplication.instance
        val dao = app.database.captureDao()
        val client = WebhookClient()

        // 1. Hide all overlays before taking screenshot and reading accessibility (per UX decision)
        val toolbarWasVisible = toolbarView?.visibility == View.VISIBLE
        val subMenuWasVisible = subMenuView?.visibility == View.VISIBLE
        toolbarView?.visibility = View.GONE
        subMenuView?.visibility = View.GONE
        bottomSheetView?.visibility = View.GONE
        pointOverlayView?.visibility = View.GONE

        // Force hide keyboard from all window tokens
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        toolbarView?.windowToken?.let { imm?.hideSoftInputFromWindow(it, 0) }
        subMenuView?.windowToken?.let { imm?.hideSoftInputFromWindow(it, 0) }
        bottomSheetView?.windowToken?.let { imm?.hideSoftInputFromWindow(it, 0) }

        // Give window manager and IME exit animation time to completely clear
        delay(250)

        val cropRect = _activeSelectionBounds.value
        val screenGrabEnabled = settings.isScreenGrabActive
        val appTreeEnabled = settings.isAppTreeActive
        val highlightedTextEnabled = settings.isHighlightedTextActive
        val mediaEnabled = settings.isMediaActive

        // 2. Perform Capture while overlays are hidden
        val accessibility = CaptureAccessibilityService.instance

        val screenshotBase64 = if (screenGrabEnabled) {
            val accessibilityScreenshot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && accessibility != null) {
                accessibility.captureScreenBase64(cropRect)
            } else null

            accessibilityScreenshot ?: if (ScreenCaptureService.isReady) {
                ScreenCaptureService.instance?.captureScreenBase64(cropRect)
            } else null
        } else null

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

        val resolvedPackageName = accessibility?.currentPackageName
            ?: accessibility?.getTargetRootNode()?.packageName?.toString()

        val resolvedAppName = resolvedPackageName?.let { pkg ->
            try {
                val pm = app.packageManager
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, 0)
                }
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                null
            }
        }

        // 3. Restore overlay visibility immediately
        if (toolbarWasVisible) {
            toolbarView?.visibility = View.VISIBLE
        }
        if (subMenuWasVisible && _expandedTarget.value != ExpandedTarget.NONE) {
            subMenuView?.visibility = View.VISIBLE
        }

        // Reset point selection if it was one-shot per spec
        val selectionString = cropRect?.let { "${it.left},${it.top},${it.right},${it.bottom}" }
        _activeSelectionBounds.value = null

        // Background parsing for media and networking to avoid blocking UI
        withContext(Dispatchers.IO) {
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
                packageName = resolvedPackageName,
                appName = resolvedAppName,
                inputContent = inputContent,
                activeContextSources = activeSources,
                mediaContext = mediaContext,
                shareContext = shareContext,
                highlightedText = highlightedText,
                targetedSelection = selectionString,
                appTreeText = appTree,
                screenshotBase64 = screenshotBase64,
                audioBase64 = audioBase64,
                attachmentBase64 = attachmentBase64,
                attachmentMime = attachmentMime
            )

            val rawJson = PayloadBuilder.toJson(payload)

            // Save raw JSON payload to disk to avoid SQLite CursorWindow limits
            val capturesDir = File(app.filesDir, "captures").apply { mkdirs() }
            val payloadFile = File(capturesDir, "capture_${System.currentTimeMillis()}_${System.nanoTime()}.json")
            try {
                payloadFile.writeText(rawJson, Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving payload to file", e)
            }

            // 5. Save to Room database (Status: SENDING)
            val captureEntity = CaptureEntity(
                type = inputType,
                source = source,
                packageName = resolvedPackageName,
                appName = resolvedAppName,
                inputPreview = when {
                    !inputContent.isNullOrBlank() -> inputContent
                    payload.shareContext != null -> "[Shared: ${payload.shareContext.title ?: payload.shareContext.text?.take(40) ?: "Content"}]"
                    !audioBase64.isNullOrBlank() -> "[Audio Recording]"
                    !attachmentBase64.isNullOrBlank() -> "[Attachment: ${attachmentMime ?: "File"}]"
                    else -> "[Context Grab]"
                },
                hasScreenGrab = screenGrabEnabled && screenshotBase64 != null,
                hasAppTree = appTreeEnabled && !appTree.isNullOrBlank(),
                hasHighlightedText = highlightedTextEnabled && !highlightedText.isNullOrBlank(),
                hasMedia = mediaEnabled && mediaContext != null,
                hasShareContext = payload.shareContext != null,
                hasAttachment = !attachmentBase64.isNullOrBlank(),
                payloadFilePath = payloadFile.absolutePath,
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
            sleepJob?.cancel()
            sleepJob = null
            dismissSubMenu()
            toolbarView?.let { windowManager.removeView(it) }
            toolbarLifecycle?.destroy()
            toolbarView = null
            toolbarLifecycle = null

            dismissBottomSheet()
            dismissPointSelectionOverlay()
            dismissDormantPlacementOverlay()
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
