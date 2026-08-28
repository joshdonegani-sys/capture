package com.capture.engine.overlay

import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MobileFriendly
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.capture.engine.CaptureApplication
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

enum class ExpandedTarget {
    NONE,
    INPUT_ONLY,
    CONTEXT_ONLY,
    JOINED_BOTH
}

data class SubPlacement(
    val x: Int,
    val y: Int,
    val effectiveDirection: String
)

/**
 * Smart Avoidance & Layout Placement Calculation Engine.
 * Calculates exact screen coordinates for the sub-menu relative to the stationary base bar.
 */
fun calculateSubPlacement(
    baseAnchorX: Int,
    baseAnchorY: Int,
    baseW: Int,
    baseH: Int,
    subW: Int,
    subH: Int,
    screenWidth: Int,
    screenHeight: Int,
    baseLayout: String,
    subLayout: String,
    subDirection: String,
    subConnection: String,
    subAlign: String,
    subAlignModifier: String,
    expandedTarget: ExpandedTarget,
    stayOnScreen: Boolean,
    gapPx: Int = 16,
    screenMargin: Int = 16
): SubPlacement {
    val effectiveGap = if (subConnection == "joined") 2 else gapPx
    var effectiveDir = subDirection

    // Auto-flip for clearance if stayOnScreen is enabled
    if (stayOnScreen) {
        when (subDirection) {
            "up" -> {
                val idealY = baseAnchorY - subH - effectiveGap
                if (idealY < screenMargin && (baseAnchorY + baseH + effectiveGap + subH <= screenHeight - screenMargin)) {
                    effectiveDir = "down"
                }
            }
            "down" -> {
                val idealY = baseAnchorY + baseH + effectiveGap
                if (idealY + subH > screenHeight - screenMargin && (baseAnchorY - subH - effectiveGap >= screenMargin)) {
                    effectiveDir = "up"
                }
            }
            "left" -> {
                val idealX = baseAnchorX - subW - effectiveGap
                if (idealX < screenMargin && (baseAnchorX + baseW + effectiveGap + subW <= screenWidth - screenMargin)) {
                    effectiveDir = "right"
                }
            }
            "right" -> {
                val idealX = baseAnchorX + baseW + effectiveGap
                if (idealX + subW > screenWidth - screenMargin && (baseAnchorX - subW - effectiveGap >= screenMargin)) {
                    effectiveDir = "left"
                }
            }
        }
    }

    var targetX = 0
    var targetY = 0

    when (effectiveDir) {
        "down" -> {
            targetY = baseAnchorY + baseH + effectiveGap
        }
        "up" -> {
            targetY = baseAnchorY - subH - effectiveGap
        }
        "right" -> {
            targetX = baseAnchorX + baseW + effectiveGap
        }
        "left" -> {
            targetX = baseAnchorX - subW - effectiveGap
        }
    }

    // Cross-axis alignment calculation
    if (effectiveDir == "down" || effectiveDir == "up") {
        // Cross axis is horizontal (X)
        if (subAlign == "base icon" && baseLayout == "horiz") {
            val (anchorStart, anchorCenter, anchorEnd) = when (expandedTarget) {
                ExpandedTarget.INPUT_ONLY -> Triple(baseAnchorX, baseAnchorX + (baseW * 0.25f).toInt(), baseAnchorX + (baseW * 0.5f).toInt())
                ExpandedTarget.CONTEXT_ONLY -> Triple(baseAnchorX + (baseW * 0.5f).toInt(), baseAnchorX + (baseW * 0.75f).toInt(), baseAnchorX + baseW)
                else -> Triple(baseAnchorX, baseAnchorX + (baseW / 2), baseAnchorX + baseW)
            }
            targetX = when (subAlignModifier) {
                "start", "top", "left" -> anchorStart
                "end", "bottom", "right" -> anchorEnd - subW
                else -> anchorCenter - (subW / 2)
            }
        } else {
            // Full Base (or vertical base bar)
            targetX = when (subAlignModifier) {
                "start", "top", "left" -> baseAnchorX
                "end", "bottom", "right" -> baseAnchorX + baseW - subW
                else -> baseAnchorX + (baseW - subW) / 2
            }
        }
    } else {
        // Cross axis is vertical (Y)
        if (subAlign == "base icon" && baseLayout == "vert") {
            val (anchorStart, anchorCenter, anchorEnd) = when (expandedTarget) {
                ExpandedTarget.INPUT_ONLY -> Triple(baseAnchorY, baseAnchorY + (baseH * 0.25f).toInt(), baseAnchorY + (baseH * 0.5f).toInt())
                ExpandedTarget.CONTEXT_ONLY -> Triple(baseAnchorY + (baseH * 0.5f).toInt(), baseAnchorY + (baseH * 0.75f).toInt(), baseAnchorY + baseH)
                else -> Triple(baseAnchorY, baseAnchorY + (baseH / 2), baseAnchorY + baseH)
            }
            targetY = when (subAlignModifier) {
                "start", "top", "up" -> anchorStart
                "end", "bottom", "down" -> anchorEnd - subH
                else -> anchorCenter - (subH / 2)
            }
        } else {
            // Full Base (or horizontal base bar)
            targetY = when (subAlignModifier) {
                "start", "top", "up" -> baseAnchorY
                "end", "bottom", "down" -> baseAnchorY + baseH - subH
                else -> baseAnchorY + (baseH - subH) / 2
            }
        }
    }

    if (stayOnScreen) {
        val minX = screenMargin
        val maxX = (screenWidth - subW - screenMargin).coerceAtLeast(minX)
        val minY = screenMargin
        val maxY = (screenHeight - subH - screenMargin - 70).coerceAtLeast(minY)

        targetX = targetX.coerceIn(minX, maxX)
        targetY = targetY.coerceIn(minY, maxY)
    }

    return SubPlacement(targetX, targetY, effectiveDir)
}

/**
 * Stationary Base Bar Overlay Composable.
 * Renders dormant pill, all-icons cluster, or fixed 2-button base bar.
 * Never resizes or shifts during sub-menu expansion.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingToolbar(
    isExpanded: StateFlow<Boolean>,
    isPointActive: StateFlow<Boolean>,
    hasActiveSelection: StateFlow<Rect?>,
    expandedTarget: StateFlow<ExpandedTarget>,
    windowPosX: Int,
    windowPosY: Int,
    screenWidth: Int,
    screenHeight: Int,
    onExpandToggle: () -> Unit,
    onCollapse: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onFlickSleep: () -> Unit,
    onInputClick: () -> Unit,
    onInputLongClick: () -> Unit,
    onContextClick: () -> Unit,
    onContextLongClick: () -> Unit,
    onInputSubSelected: (String) -> Unit = {},
    onSelectAllContext: () -> Unit = {},
    onSelectNoneContext: () -> Unit = {},
    onTogglePointMode: () -> Unit = {}
) {
    val expanded by isExpanded.collectAsState()
    val pointActive by isPointActive.collectAsState()
    val activeSelection by hasActiveSelection.collectAsState()
    val target by expandedTarget.collectAsState()
    val settings = remember { CaptureApplication.instance.settingsRepository }

    val iconSize by settings.toolbarIconSizeFlow.collectAsState()
    val alpha by settings.toolbarAlphaFlow.collectAsState()
    val colorRole by settings.toolbarColorRoleFlow.collectAsState()
    val customSeedColor by settings.toolbarCustomSeedColorFlow.collectAsState()
    val visibleInputIcons by settings.visibleInputIconsFlow.collectAsState()
    val lastUsedInput by settings.lastUsedInputTypeFlow.collectAsState()

    val baseLayout by settings.toolbarBaseLayoutFlow.collectAsState()
    val dormantStyle by settings.toolbarDormantStyleFlow.collectAsState()
    val dormantLocked by settings.toolbarDormantLockedFlow.collectAsState()
    val awakeStyle by settings.toolbarAwakeStyleFlow.collectAsState()
    val awakeLocked by settings.toolbarAwakeLockedFlow.collectAsState()
    val allIconsOrder by settings.toolbarAllIconsOrderFlow.collectAsState()
    val wakeGesture by settings.toolbarWakeGestureFlow.collectAsState()
    val sleepGesture by settings.toolbarSleepGestureFlow.collectAsState()
    val tonalElevation by settings.toolbarTonalElevationFlow.collectAsState()
    val shadowElevation by settings.toolbarShadowElevationFlow.collectAsState()

    val screenGrab by settings.isScreenGrabActiveFlow.collectAsState()
    val appTree by settings.isAppTreeActiveFlow.collectAsState()
    val highlightedText by settings.isHighlightedTextActiveFlow.collectAsState()
    val media by settings.isMediaActiveFlow.collectAsState()

    val iconSizeDp = iconSize.dp
    val buttonSize = (iconSize + 10f).dp.coerceIn(28.dp, 36.dp)
    val menuButtonSize = (iconSize + 4f).dp.coerceIn(24.dp, 32.dp)
    val menuIconSizeDp = (iconSize - 4f).dp.coerceIn(15.dp, 19.dp)
    val dividerSize = (iconSize + 4f).dp

    val isDark = isSystemInDarkTheme()
    val colors = resolveToolbarColors(
        colorRole = colorRole,
        customSeedColor = customSeedColor,
        alpha = alpha,
        isDark = isDark
    )

    val hasActiveContext = screenGrab || appTree || highlightedText || media || pointActive || activeSelection != null

    androidx.compose.animation.Crossfade(
        targetState = expanded,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "ToolbarDormantAwakeTransition"
    ) { isAwake ->
        if (!isAwake) {
            // --- DORMANT STATE ---
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .pointerInput(dormantLocked, wakeGesture) {
                        if (!dormantLocked) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.x, dragAmount.y)
                                }
                            )
                        }
                    }
                    .pointerInput(wakeGesture) {
                        detectTapGestures(
                            onTap = {
                                if (wakeGesture == "single tap" || wakeGesture.isBlank()) {
                                    onExpandToggle()
                                }
                            },
                            onDoubleTap = {
                                if (wakeGesture == "double tap") {
                                    onExpandToggle()
                                }
                            },
                            onLongPress = {
                                if (wakeGesture == "long tap") {
                                    onExpandToggle()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.containerColor,
                    tonalElevation = tonalElevation.dp,
                    shadowElevation = shadowElevation.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    when (dormantStyle) {
                        "small icon" -> {
                            Box(
                                modifier = Modifier
                                    .size((iconSize + 12f).dp.coerceAtLeast(36.dp))
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (lastUsedInput) {
                                        "mic_audio" -> Icons.Default.Mic
                                        "clipboard_paste" -> Icons.Default.ContentPaste
                                        "text_snippet" -> Icons.Default.Keyboard
                                        else -> Icons.Default.RecordVoiceOver
                                    },
                                    contentDescription = "Dormant Toolbar Icon",
                                    tint = colors.contentColor,
                                    modifier = Modifier.size(iconSizeDp * 0.8f)
                                )
                            }
                        }
                        "vertical bar" -> {
                            Box(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height((iconSize * 2.5f).dp.coerceAtLeast(60.dp))
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height((iconSize * 1.8f).dp.coerceAtLeast(40.dp)),
                                    shape = CircleShape,
                                    color = colors.contentColor.copy(alpha = 0.6f)
                                ) {}
                            }
                        }
                        else -> {
                            // "horizontal bar" (Default)
                            Box(
                                modifier = Modifier
                                    .width((iconSize * 2.5f).dp.coerceAtLeast(60.dp))
                                    .height(20.dp)
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .width((iconSize * 1.8f).dp.coerceAtLeast(40.dp))
                                        .height(4.dp),
                                    shape = CircleShape,
                                    color = colors.contentColor.copy(alpha = 0.6f)
                                ) {}
                            }
                        }
                    }
                }
            }
        } else {
            // --- AWAKE STATE ---
            var dragVelocityTrackerX by remember { mutableStateOf(0f) }
            var dragVelocityTrackerY by remember { mutableStateOf(0f) }

            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .pointerInput(awakeLocked, sleepGesture) {
                        if (!awakeLocked) {
                            detectDragGestures(
                                onDragEnd = {
                                    if (sleepGesture == "flick") {
                                        val speed = abs(dragVelocityTrackerX) + abs(dragVelocityTrackerY)
                                        if (speed > 60f) {
                                            onFlickSleep()
                                        }
                                    }
                                    dragVelocityTrackerX = 0f
                                    dragVelocityTrackerY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragVelocityTrackerX = dragAmount.x
                                    dragVelocityTrackerY = dragAmount.y
                                    onDrag(dragAmount.x, dragAmount.y)
                                }
                            )
                        }
                    }
                    .pointerInput(sleepGesture) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (sleepGesture == "double tap" || sleepGesture.isBlank()) {
                                    onCollapse()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.TopStart
            ) {
                if (awakeStyle == "all icons") {
                    // --- ALL ICONS MODE ---
                    val isHoriz = baseLayout == "horiz"
                    val isInputFirst = resolveAllIconsOrder(
                        orderSetting = allIconsOrder,
                        isHoriz = isHoriz,
                        windowPosX = windowPosX,
                        windowPosY = windowPosY,
                        screenWidth = screenWidth,
                        screenHeight = screenHeight
                    )

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colors.containerColor,
                        tonalElevation = tonalElevation.dp,
                        shadowElevation = shadowElevation.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        if (isHoriz) {
                            Column(
                                modifier = Modifier
                                    .width(IntrinsicSize.Min)
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                if (isInputFirst) {
                                    InputSubGroupContent(true, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor) { settings.lastUsedInputType = it; onInputSubSelected(it) }
                                    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    ContextSubGroupContent(true, screenGrab, { settings.isScreenGrabActive = it }, appTree, { settings.isAppTreeActive = it }, highlightedText, { settings.isHighlightedTextActive = it }, media, { settings.isMediaActive = it }, pointActive, activeSelection, onTogglePointMode, { onSelectAllContext(); settings.isScreenGrabActive = true; settings.isAppTreeActive = true; settings.isHighlightedTextActive = true; settings.isMediaActive = true }, { onSelectNoneContext(); settings.isScreenGrabActive = false; settings.isAppTreeActive = false; settings.isHighlightedTextActive = false; settings.isMediaActive = false }, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                                } else {
                                    ContextSubGroupContent(true, screenGrab, { settings.isScreenGrabActive = it }, appTree, { settings.isAppTreeActive = it }, highlightedText, { settings.isHighlightedTextActive = it }, media, { settings.isMediaActive = it }, pointActive, activeSelection, onTogglePointMode, { onSelectAllContext(); settings.isScreenGrabActive = true; settings.isAppTreeActive = true; settings.isHighlightedTextActive = true; settings.isMediaActive = true }, { onSelectNoneContext(); settings.isScreenGrabActive = false; settings.isAppTreeActive = false; settings.isHighlightedTextActive = false; settings.isMediaActive = false }, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                                    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    InputSubGroupContent(true, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor) { settings.lastUsedInputType = it; onInputSubSelected(it) }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .height(IntrinsicSize.Min)
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                if (isInputFirst) {
                                    InputSubGroupContent(false, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor) { settings.lastUsedInputType = it; onInputSubSelected(it) }
                                    VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    ContextSubGroupContent(false, screenGrab, { settings.isScreenGrabActive = it }, appTree, { settings.isAppTreeActive = it }, highlightedText, { settings.isHighlightedTextActive = it }, media, { settings.isMediaActive = it }, pointActive, activeSelection, onTogglePointMode, { onSelectAllContext(); settings.isScreenGrabActive = true; settings.isAppTreeActive = true; settings.isHighlightedTextActive = true; settings.isMediaActive = true }, { onSelectNoneContext(); settings.isScreenGrabActive = false; settings.isAppTreeActive = false; settings.isHighlightedTextActive = false; settings.isMediaActive = false }, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                                } else {
                                    ContextSubGroupContent(false, screenGrab, { settings.isScreenGrabActive = it }, appTree, { settings.isAppTreeActive = it }, highlightedText, { settings.isHighlightedTextActive = it }, media, { settings.isMediaActive = it }, pointActive, activeSelection, onTogglePointMode, { onSelectAllContext(); settings.isScreenGrabActive = true; settings.isAppTreeActive = true; settings.isHighlightedTextActive = true; settings.isMediaActive = true }, { onSelectNoneContext(); settings.isScreenGrabActive = false; settings.isAppTreeActive = false; settings.isHighlightedTextActive = false; settings.isMediaActive = false }, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                                    VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    InputSubGroupContent(false, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor) { settings.lastUsedInputType = it; onInputSubSelected(it) }
                                }
                            }
                        }
                    }
                } else {
                    // --- STATIONARY BASE BAR (Double Icon or Single Icon) ---
                    val isSingle = awakeStyle == "single icon"
                    val (lastIcon, lastDesc) = when (lastUsedInput) {
                        "mic_audio" -> Icons.Default.Mic to "Record"
                        "clipboard_paste" -> Icons.Default.ContentPaste to "Paste"
                        "text_snippet" -> Icons.Default.Keyboard to "Type"
                        else -> Icons.Default.RecordVoiceOver to "Dictate"
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colors.containerColor,
                        tonalElevation = tonalElevation.dp,
                        shadowElevation = shadowElevation.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        if (isSingle) {
                            Box(modifier = Modifier.padding(4.dp)) {
                                ToolbarIconButton(
                                    icon = lastIcon,
                                    contentDescription = "$lastDesc (Last Used)",
                                    buttonSize = buttonSize,
                                    iconSize = iconSizeDp,
                                    isActive = target != ExpandedTarget.NONE,
                                    activeContainerColor = colors.activeContainerColor,
                                    activeContentColor = colors.activeContentColor,
                                    contentColor = colors.contentColor,
                                    onClick = onInputClick,
                                    onLongClick = onInputLongClick
                                )
                            }
                        } else if (baseLayout == "horiz") {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                ToolbarIconButton(
                                    icon = lastIcon,
                                    contentDescription = "$lastDesc (Last Used)",
                                    buttonSize = buttonSize,
                                    iconSize = iconSizeDp,
                                    isActive = target == ExpandedTarget.INPUT_ONLY || target == ExpandedTarget.JOINED_BOTH,
                                    activeContainerColor = colors.activeContainerColor,
                                    activeContentColor = colors.activeContentColor,
                                    contentColor = colors.contentColor,
                                    onClick = onInputClick,
                                    onLongClick = onInputLongClick
                                )

                                ToolbarIconButton(
                                    icon = Icons.Default.Menu,
                                    contentDescription = "Context",
                                    buttonSize = buttonSize,
                                    iconSize = iconSizeDp,
                                    isActive = target == ExpandedTarget.CONTEXT_ONLY || target == ExpandedTarget.JOINED_BOTH,
                                    activeContainerColor = colors.activeContainerColor,
                                    activeContentColor = colors.activeContentColor,
                                    contentColor = colors.contentColor,
                                    onClick = onContextClick,
                                    onLongClick = onContextLongClick
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                ToolbarIconButton(
                                    icon = lastIcon,
                                    contentDescription = "$lastDesc (Last Used)",
                                    buttonSize = buttonSize,
                                    iconSize = iconSizeDp,
                                    isActive = target == ExpandedTarget.INPUT_ONLY || target == ExpandedTarget.JOINED_BOTH,
                                    activeContainerColor = colors.activeContainerColor,
                                    activeContentColor = colors.activeContentColor,
                                    contentColor = colors.contentColor,
                                    onClick = onInputClick,
                                    onLongClick = onInputLongClick
                                )

                                ToolbarIconButton(
                                    icon = Icons.Default.Menu,
                                    contentDescription = "Context",
                                    buttonSize = buttonSize,
                                    iconSize = iconSizeDp,
                                    isActive = target == ExpandedTarget.CONTEXT_ONLY || target == ExpandedTarget.JOINED_BOTH,
                                    activeContainerColor = colors.activeContainerColor,
                                    activeContentColor = colors.activeContentColor,
                                    contentColor = colors.contentColor,
                                    onClick = onContextClick,
                                    onLongClick = onContextLongClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Floating Sub-Menu Composable.
 * Renders in an anchored overlay window positioned relative to the base bar.
 */
@Composable
fun FloatingSubMenu(
    expandedTarget: StateFlow<ExpandedTarget>,
    isPointActive: StateFlow<Boolean>,
    hasActiveSelection: StateFlow<Rect?>,
    onDismiss: () -> Unit = {},
    onInputSelected: (String) -> Unit,
    onTogglePointMode: () -> Unit,
    onSelectAllContext: () -> Unit,
    onSelectNoneContext: () -> Unit
) {
    val target by expandedTarget.collectAsState()
    val pointActive by isPointActive.collectAsState()
    val activeSelection by hasActiveSelection.collectAsState()
    val settings = remember { CaptureApplication.instance.settingsRepository }

    val iconSize by settings.toolbarIconSizeFlow.collectAsState()
    val alpha by settings.toolbarAlphaFlow.collectAsState()
    val colorRole by settings.toolbarColorRoleFlow.collectAsState()
    val customSeedColor by settings.toolbarCustomSeedColorFlow.collectAsState()
    val visibleInputIcons by settings.visibleInputIconsFlow.collectAsState()
    val tonalElevation by settings.toolbarTonalElevationFlow.collectAsState()
    val shadowElevation by settings.toolbarShadowElevationFlow.collectAsState()

    val baseLayout by settings.toolbarBaseLayoutFlow.collectAsState()
    val subLayout by settings.toolbarSubLayoutFlow.collectAsState()
    val subConnection by settings.toolbarSubConnectionFlow.collectAsState()
    val awakeStyle by settings.toolbarAwakeStyleFlow.collectAsState()

    val screenGrab by settings.isScreenGrabActiveFlow.collectAsState()
    val appTree by settings.isAppTreeActiveFlow.collectAsState()
    val highlightedText by settings.isHighlightedTextActiveFlow.collectAsState()
    val media by settings.isMediaActiveFlow.collectAsState()

    val isBaseHoriz = baseLayout == "horiz"
    val isFlow = subLayout == "flow axis"
    val isSubHoriz = if (isFlow) isBaseHoriz else !isBaseHoriz
    val isSingle = awakeStyle == "single icon"
    val isJoined = isSingle || subConnection == "joined"

    val iconSizeDp = iconSize.dp
    val buttonSize = (iconSize + 10f).dp.coerceIn(28.dp, 36.dp)
    val menuButtonSize = (iconSize + 4f).dp.coerceIn(24.dp, 32.dp)
    val menuIconSizeDp = (iconSize - 4f).dp.coerceIn(15.dp, 19.dp)
    val dividerSize = (iconSize + 4f).dp
    val baseLongEdgeDp = if (isSingle) (buttonSize + 8.dp) else (buttonSize * 2 + 11.dp)

    val isDark = isSystemInDarkTheme()
    val colors = resolveToolbarColors(
        colorRole = colorRole,
        customSeedColor = customSeedColor,
        alpha = alpha,
        isDark = isDark
    )

    AnimatedVisibility(
        visible = target != ExpandedTarget.NONE,
        enter = fadeIn(tween(150, easing = FastOutSlowInEasing)) + scaleIn(initialScale = 0.92f, animationSpec = tween(150, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(120, easing = FastOutLinearInEasing)) + scaleOut(targetScale = 0.92f, animationSpec = tween(120, easing = FastOutLinearInEasing)),
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onDismiss()
            }
            .padding(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.containerColor,
            tonalElevation = tonalElevation.dp,
            shadowElevation = shadowElevation.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Consume clicks on the surface so it doesn't trigger dismissal
            }
        ) {
            when (target) {
                ExpandedTarget.INPUT_ONLY -> {
                    Box(
                        modifier = Modifier
                            .let {
                                if (isJoined) {
                                    if (isSubHoriz) it.height(baseLongEdgeDp) else it.width(baseLongEdgeDp)
                                } else it
                            }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        InputSubGroupContent(
                            isHoriz = isSubHoriz,
                            visibleInputIcons = visibleInputIcons,
                            buttonSize = buttonSize,
                            iconSizeDp = iconSizeDp,
                            contentColor = colors.contentColor,
                            activeContainerColor = colors.activeContainerColor,
                            activeContentColor = colors.activeContentColor,
                            onInputSelected = onInputSelected
                        )
                    }
                }
                ExpandedTarget.CONTEXT_ONLY -> {
                    Box(
                        modifier = Modifier
                            .let {
                                if (isJoined) {
                                    if (isSubHoriz) it.height(baseLongEdgeDp) else it.width(baseLongEdgeDp)
                                } else it
                            }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ContextSubGroupContent(
                            isHoriz = isSubHoriz,
                            screenGrab = screenGrab,
                            onScreenGrabChange = { settings.isScreenGrabActive = it },
                            appTree = appTree,
                            onAppTreeChange = { settings.isAppTreeActive = it },
                            highlightedText = highlightedText,
                            onHighlightedTextChange = { settings.isHighlightedTextActive = it },
                            media = media,
                            onMediaChange = { settings.isMediaActive = it },
                            pointActive = pointActive,
                            activeSelection = activeSelection,
                            onTogglePointMode = onTogglePointMode,
                            onSelectAllContext = { onSelectAllContext(); settings.isScreenGrabActive = true; settings.isAppTreeActive = true; settings.isHighlightedTextActive = true; settings.isMediaActive = true },
                            onSelectNoneContext = { onSelectNoneContext(); settings.isScreenGrabActive = false; settings.isAppTreeActive = false; settings.isHighlightedTextActive = false; settings.isMediaActive = false },
                            menuButtonSize = menuButtonSize,
                            menuIconSizeDp = menuIconSizeDp,
                            activeContainerColor = colors.activeContainerColor,
                            activeContentColor = colors.activeContentColor,
                            contentColor = colors.contentColor
                        )
                    }
                }
                ExpandedTarget.JOINED_BOTH -> {
                    if (isSubHoriz) {
                        // Horizontal sub-items -> Stack subgroups vertically in a Column
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Min)
                                .let { if (isJoined) it.height(baseLongEdgeDp) else it }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = if (isJoined) Arrangement.SpaceEvenly else Arrangement.spacedBy(3.dp)
                        ) {
                            InputSubGroupContent(true, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor, onInputSelected)
                            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ContextSubGroupContent(true, screenGrab, { settings.isScreenGrabActive = it }, appTree, { settings.isAppTreeActive = it }, highlightedText, { settings.isHighlightedTextActive = it }, media, { settings.isMediaActive = it }, pointActive, activeSelection, onTogglePointMode, { onSelectAllContext(); settings.isScreenGrabActive = true; settings.isAppTreeActive = true; settings.isHighlightedTextActive = true; settings.isMediaActive = true }, { onSelectNoneContext(); settings.isScreenGrabActive = false; settings.isAppTreeActive = false; settings.isHighlightedTextActive = false; settings.isMediaActive = false }, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                        }
                    } else {
                        // Vertical sub-items -> Place subgroups side-by-side in a Row
                        Row(
                            modifier = Modifier
                                .height(IntrinsicSize.Min)
                                .let { if (isJoined) it.width(baseLongEdgeDp) else it }
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if (isJoined) Arrangement.SpaceEvenly else Arrangement.spacedBy(3.dp)
                        ) {
                            InputSubGroupContent(false, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor, onInputSelected)
                            VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ContextSubGroupContent(false, screenGrab, { settings.isScreenGrabActive = it }, appTree, { settings.isAppTreeActive = it }, highlightedText, { settings.isHighlightedTextActive = it }, media, { settings.isMediaActive = it }, pointActive, activeSelection, onTogglePointMode, { onSelectAllContext(); settings.isScreenGrabActive = true; settings.isAppTreeActive = true; settings.isHighlightedTextActive = true; settings.isMediaActive = true }, { onSelectNoneContext(); settings.isScreenGrabActive = false; settings.isAppTreeActive = false; settings.isHighlightedTextActive = false; settings.isMediaActive = false }, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                        }
                    }
                }
                ExpandedTarget.NONE -> {}
            }
        }
    }
}

@Composable
fun InputSubGroupContent(
    isHoriz: Boolean,
    visibleInputIcons: Set<String>,
    buttonSize: Dp,
    iconSizeDp: Dp,
    contentColor: Color,
    activeContainerColor: Color,
    activeContentColor: Color,
    onInputSelected: (String) -> Unit
) {
    val items: @Composable () -> Unit = {
        if (visibleInputIcons.contains("speech_to_text")) {
            ToolbarIconButton(
                icon = Icons.Default.RecordVoiceOver,
                contentDescription = "Dictate",
                buttonSize = buttonSize,
                iconSize = iconSizeDp,
                activeContainerColor = activeContainerColor,
                activeContentColor = activeContentColor,
                contentColor = contentColor,
                onClick = { onInputSelected("speech_to_text") }
            )
        }
        if (visibleInputIcons.contains("mic_audio")) {
            ToolbarIconButton(
                icon = Icons.Default.Mic,
                contentDescription = "Record",
                buttonSize = buttonSize,
                iconSize = iconSizeDp,
                activeContainerColor = activeContainerColor,
                activeContentColor = activeContentColor,
                contentColor = contentColor,
                onClick = { onInputSelected("mic_audio") }
            )
        }
        if (visibleInputIcons.contains("clipboard_paste")) {
            ToolbarIconButton(
                icon = Icons.Default.ContentPaste,
                contentDescription = "Paste",
                buttonSize = buttonSize,
                iconSize = iconSizeDp,
                activeContainerColor = activeContainerColor,
                activeContentColor = activeContentColor,
                contentColor = contentColor,
                onClick = { onInputSelected("clipboard_paste") }
            )
        }
        if (visibleInputIcons.contains("text_snippet")) {
            ToolbarIconButton(
                icon = Icons.Default.Keyboard,
                contentDescription = "Type",
                buttonSize = buttonSize,
                iconSize = iconSizeDp,
                activeContainerColor = activeContainerColor,
                activeContentColor = activeContentColor,
                contentColor = contentColor,
                onClick = { onInputSelected("text_snippet") }
            )
        }
    }
    if (isHoriz) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) { items() }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) { items() }
    }
}

@Composable
fun ContextSubGroupContent(
    isHoriz: Boolean,
    screenGrab: Boolean,
    onScreenGrabChange: (Boolean) -> Unit,
    appTree: Boolean,
    onAppTreeChange: (Boolean) -> Unit,
    highlightedText: Boolean,
    onHighlightedTextChange: (Boolean) -> Unit,
    media: Boolean,
    onMediaChange: (Boolean) -> Unit,
    pointActive: Boolean,
    activeSelection: Any?,
    onTogglePointMode: () -> Unit,
    onSelectAllContext: () -> Unit,
    onSelectNoneContext: () -> Unit,
    menuButtonSize: Dp,
    menuIconSizeDp: Dp,
    activeContainerColor: Color,
    activeContentColor: Color,
    contentColor: Color
) {
    val items: @Composable () -> Unit = {
        ToolbarIconButton(
            icon = Icons.Default.MobileFriendly,
            contentDescription = "All",
            buttonSize = menuButtonSize,
            iconSize = menuIconSizeDp,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
            contentColor = contentColor,
            onClick = onSelectAllContext
        )
        ToolbarIconButton(
            icon = Icons.Default.Block,
            contentDescription = "None",
            buttonSize = menuButtonSize,
            iconSize = menuIconSizeDp,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
            contentColor = contentColor,
            onClick = onSelectNoneContext
        )
        ToolbarIconButton(
            icon = Icons.Default.AdsClick,
            contentDescription = "Point",
            buttonSize = menuButtonSize,
            iconSize = menuIconSizeDp,
            isActive = pointActive || activeSelection != null,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
            contentColor = contentColor,
            onClick = onTogglePointMode
        )
        ToolbarIconButton(
            icon = Icons.Default.Screenshot,
            contentDescription = "Screen",
            buttonSize = menuButtonSize,
            iconSize = menuIconSizeDp,
            isActive = screenGrab,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
            contentColor = contentColor,
            onClick = { onScreenGrabChange(!screenGrab) }
        )
        ToolbarIconButton(
            icon = Icons.Default.AppShortcut,
            contentDescription = "Tree",
            buttonSize = menuButtonSize,
            iconSize = menuIconSizeDp,
            isActive = appTree,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
            contentColor = contentColor,
            onClick = { onAppTreeChange(!appTree) }
        )
        ToolbarIconButton(
            icon = Icons.AutoMirrored.Filled.ShortText,
            contentDescription = "Text",
            buttonSize = menuButtonSize,
            iconSize = menuIconSizeDp,
            isActive = highlightedText,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
            contentColor = contentColor,
            onClick = { onHighlightedTextChange(!highlightedText) }
        )
        ToolbarIconButton(
            icon = Icons.Default.PlayCircle,
            contentDescription = "Media",
            buttonSize = menuButtonSize,
            iconSize = menuIconSizeDp,
            isActive = media,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
            contentColor = contentColor,
            onClick = { onMediaChange(!media) }
        )
    }
    if (isHoriz) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) { items() }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) { items() }
    }
}

fun resolveAllIconsOrder(orderSetting: String, isHoriz: Boolean, windowPosX: Int, windowPosY: Int, screenWidth: Int, screenHeight: Int): Boolean {
    return when (orderSetting) {
        "left" -> true
        "right" -> false
        "up" -> true
        "down" -> false
        "inner" -> if (isHoriz) windowPosX >= (screenWidth / 2) else windowPosY >= (screenHeight / 2)
        "outer" -> if (isHoriz) windowPosX < (screenWidth / 2) else windowPosY < (screenHeight / 2)
        else -> true
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    buttonSize: Dp = 32.dp,
    iconSize: Dp = 18.dp,
    isActive: Boolean = false,
    activeContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        shape = CircleShape,
        color = if (isActive) activeContainerColor else Color.Transparent,
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isActive) activeContentColor else contentColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

data class ToolbarColors(
    val containerColor: Color,
    val contentColor: Color,
    val activeContainerColor: Color,
    val activeContentColor: Color
)

@Composable
fun resolveToolbarColors(
    colorRole: String,
    customSeedColor: Int,
    alpha: Float,
    isDark: Boolean
): ToolbarColors {
    val (bg, content, activeContainer, activeContent) = when (colorRole) {
        "primary_container" -> Quadruple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary
        )
        "secondary_container" -> Quadruple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.onSecondary
        )
        "tertiary_container" -> Quadruple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        "surface_variant" -> Quadruple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary
        )
        "custom" -> {
            val seed = Color(customSeedColor)
            val container = if (isDark) seed.copy(alpha = 0.35f) else seed.copy(alpha = 0.18f)
            val textContent = if (isDark) Color.White else Color(0xFF1C1B1F)
            val activeCont = seed
            val activeText = Color.White
            Quadruple(container, textContent, activeCont, activeText)
        }
        else -> Quadruple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary
        )
    }

    return ToolbarColors(
        containerColor = bg.copy(alpha = alpha),
        contentColor = content,
        activeContainerColor = activeContainer,
        activeContentColor = activeContent
    )
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun ToolbarLayoutEngine(
    baseLayout: String,
    subLayout: String,
    subDirection: String,
    subConnection: String = "separate",
    subAlign: String,
    subAlignModifier: String,
    expandedTarget: ExpandedTarget,
    isSingleIcon: Boolean,
    colors: ToolbarColors,
    buttonSize: Dp,
    iconSizeDp: Dp,
    menuButtonSize: Dp,
    menuIconSizeDp: Dp,
    dividerSize: Dp,
    lastUsedInput: String,
    visibleInputIcons: Set<String>,
    hasActiveContext: Boolean,
    screenGrab: Boolean,
    appTree: Boolean,
    highlightedText: Boolean,
    media: Boolean,
    pointActive: Boolean,
    activeSelection: Any?,
    onInputClick: () -> Unit,
    onInputLongClick: () -> Unit,
    onContextClick: () -> Unit,
    onContextLongClick: () -> Unit,
    onInputSubSelected: (String) -> Unit,
    onScreenGrabChange: (Boolean) -> Unit,
    onAppTreeChange: (Boolean) -> Unit,
    onHighlightedTextChange: (Boolean) -> Unit,
    onMediaChange: (Boolean) -> Unit,
    onTogglePointMode: () -> Unit,
    onSelectAllContext: () -> Unit,
    onSelectNoneContext: () -> Unit,
    tonalElevation: Dp = 4.dp,
    shadowElevation: Dp = 4.dp,
    onBaseOffsetChanged: (Int, Int) -> Unit = { _, _ -> }
) {
    val isBaseHoriz = baseLayout == "horiz"
    val isFlow = subLayout == "flow axis"
    val isSubHoriz = if (isFlow) isBaseHoriz else !isBaseHoriz
    val isJoined = isSingleIcon || subConnection == "joined"
    val baseLongEdgeDp = if (isSingleIcon) (buttonSize + 8.dp) else (buttonSize * 2 + 11.dp)

    val isExpanded = expandedTarget != ExpandedTarget.NONE

    val (lastIcon, lastDesc) = when (lastUsedInput) {
        "mic_audio" -> Icons.Default.Mic to "Record"
        "clipboard_paste" -> Icons.Default.ContentPaste to "Paste"
        "text_snippet" -> Icons.Default.Keyboard to "Type"
        else -> Icons.Default.RecordVoiceOver to "Dictate"
    }

    val baseContent: @Composable () -> Unit = {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.containerColor,
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            if (isSingleIcon) {
                ToolbarIconButton(
                    icon = lastIcon,
                    contentDescription = "$lastDesc (Last Used)",
                    buttonSize = buttonSize,
                    iconSize = iconSizeDp,
                    isActive = expandedTarget != ExpandedTarget.NONE,
                    activeContainerColor = colors.activeContainerColor,
                    activeContentColor = colors.activeContentColor,
                    contentColor = colors.contentColor,
                    onClick = onInputClick,
                    onLongClick = onInputLongClick
                )
            } else if (isBaseHoriz) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    ToolbarIconButton(
                        icon = lastIcon,
                        contentDescription = "$lastDesc (Last Used)",
                        buttonSize = buttonSize,
                        iconSize = iconSizeDp,
                        isActive = expandedTarget == ExpandedTarget.INPUT_ONLY || expandedTarget == ExpandedTarget.JOINED_BOTH,
                        activeContainerColor = colors.activeContainerColor,
                        activeContentColor = colors.activeContentColor,
                        contentColor = colors.contentColor,
                        onClick = onInputClick,
                        onLongClick = onInputLongClick
                    )
                    ToolbarIconButton(
                        icon = Icons.Default.Menu,
                        contentDescription = "Context",
                        buttonSize = buttonSize,
                        iconSize = iconSizeDp,
                        isActive = expandedTarget == ExpandedTarget.CONTEXT_ONLY || expandedTarget == ExpandedTarget.JOINED_BOTH,
                        activeContainerColor = colors.activeContainerColor,
                        activeContentColor = colors.activeContentColor,
                        contentColor = colors.contentColor,
                        onClick = onContextClick,
                        onLongClick = onContextLongClick
                    )
                }
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    ToolbarIconButton(
                        icon = lastIcon,
                        contentDescription = "$lastDesc (Last Used)",
                        buttonSize = buttonSize,
                        iconSize = iconSizeDp,
                        isActive = expandedTarget == ExpandedTarget.INPUT_ONLY || expandedTarget == ExpandedTarget.JOINED_BOTH,
                        activeContainerColor = colors.activeContainerColor,
                        activeContentColor = colors.activeContentColor,
                        contentColor = colors.contentColor,
                        onClick = onInputClick,
                        onLongClick = onInputLongClick
                    )
                    ToolbarIconButton(
                        icon = Icons.Default.Menu,
                        contentDescription = "Context",
                        buttonSize = buttonSize,
                        iconSize = iconSizeDp,
                        isActive = expandedTarget == ExpandedTarget.CONTEXT_ONLY || expandedTarget == ExpandedTarget.JOINED_BOTH,
                        activeContainerColor = colors.activeContainerColor,
                        activeContentColor = colors.activeContentColor,
                        contentColor = colors.contentColor,
                        onClick = onContextClick,
                        onLongClick = onContextLongClick
                    )
                }
            }
        }
    }

    val subContent: @Composable () -> Unit = {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.containerColor,
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.padding(2.dp)
        ) {
            when (expandedTarget) {
                ExpandedTarget.INPUT_ONLY -> {
                    Box(
                        modifier = Modifier
                            .let {
                                if (isJoined) {
                                    if (isSubHoriz) it.height(baseLongEdgeDp) else it.width(baseLongEdgeDp)
                                } else it
                            }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        InputSubGroupContent(
                            isHoriz = isSubHoriz,
                            visibleInputIcons = visibleInputIcons,
                            buttonSize = buttonSize,
                            iconSizeDp = iconSizeDp,
                            contentColor = colors.contentColor,
                            activeContainerColor = colors.activeContainerColor,
                            activeContentColor = colors.activeContentColor,
                            onInputSelected = onInputSubSelected
                        )
                    }
                }
                ExpandedTarget.CONTEXT_ONLY -> {
                    Box(
                        modifier = Modifier
                            .let {
                                if (isJoined) {
                                    if (isSubHoriz) it.height(baseLongEdgeDp) else it.width(baseLongEdgeDp)
                                } else it
                            }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ContextSubGroupContent(
                            isHoriz = isSubHoriz,
                            screenGrab = screenGrab,
                            onScreenGrabChange = onScreenGrabChange,
                            appTree = appTree,
                            onAppTreeChange = onAppTreeChange,
                            highlightedText = highlightedText,
                            onHighlightedTextChange = onHighlightedTextChange,
                            media = media,
                            onMediaChange = onMediaChange,
                            pointActive = pointActive,
                            activeSelection = activeSelection,
                            onTogglePointMode = onTogglePointMode,
                            onSelectAllContext = onSelectAllContext,
                            onSelectNoneContext = onSelectNoneContext,
                            menuButtonSize = menuButtonSize,
                            menuIconSizeDp = menuIconSizeDp,
                            activeContainerColor = colors.activeContainerColor,
                            activeContentColor = colors.activeContentColor,
                            contentColor = colors.contentColor
                        )
                    }
                }
                ExpandedTarget.JOINED_BOTH -> {
                    if (isSubHoriz) {
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Min)
                                .let { if (isJoined) it.height(baseLongEdgeDp) else it }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = if (isJoined) Arrangement.SpaceEvenly else Arrangement.spacedBy(3.dp)
                        ) {
                            InputSubGroupContent(true, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor, onInputSubSelected)
                            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ContextSubGroupContent(true, screenGrab, onScreenGrabChange, appTree, onAppTreeChange, highlightedText, onHighlightedTextChange, media, onMediaChange, pointActive, activeSelection, onTogglePointMode, onSelectAllContext, onSelectNoneContext, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .height(IntrinsicSize.Min)
                                .let { if (isJoined) it.width(baseLongEdgeDp) else it }
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if (isJoined) Arrangement.SpaceEvenly else Arrangement.spacedBy(3.dp)
                        ) {
                            InputSubGroupContent(false, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor, onInputSubSelected)
                            VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ContextSubGroupContent(false, screenGrab, onScreenGrabChange, appTree, onAppTreeChange, highlightedText, onHighlightedTextChange, media, onMediaChange, pointActive, activeSelection, onTogglePointMode, onSelectAllContext, onSelectNoneContext, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                        }
                    }
                }
                ExpandedTarget.NONE -> {}
            }
        }
    }

    Layout(
        content = {
            Box(modifier = Modifier.layoutId("base")) { baseContent() }
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.layoutId("sub")
            ) { subContent() }
        }
    ) { measurables, constraints ->
        val childConstraints = Constraints(0, constraints.maxWidth, 0, constraints.maxHeight)
        val basePlaceable = measurables.firstOrNull { it.layoutId == "base" }?.measure(childConstraints)
        val subPlaceable = measurables.firstOrNull { it.layoutId == "sub" }?.measure(childConstraints)

        val baseW = basePlaceable?.width ?: 0
        val baseH = basePlaceable?.height ?: 0
        val subW = if (isExpanded) (subPlaceable?.width ?: 0) else 0
        val subH = if (isExpanded) (subPlaceable?.height ?: 0) else 0

        val placement = calculateSubPlacement(
            baseAnchorX = 0,
            baseAnchorY = 0,
            baseW = baseW,
            baseH = baseH,
            subW = subW,
            subH = subH,
            screenWidth = 10000,
            screenHeight = 10000,
            baseLayout = baseLayout,
            subLayout = subLayout,
            subDirection = subDirection,
            subConnection = if (isSingleIcon) "joined" else subConnection,
            subAlign = subAlign,
            subAlignModifier = subAlignModifier,
            expandedTarget = expandedTarget,
            stayOnScreen = false,
            gapPx = 16,
            screenMargin = 0
        )

        val minX = minOf(0, if (isExpanded) placement.x else 0)
        val minY = minOf(0, if (isExpanded) placement.y else 0)
        val maxX = maxOf(baseW, if (isExpanded) placement.x + subW else baseW)
        val maxY = maxOf(baseH, if (isExpanded) placement.y + subH else baseH)

        val totalW = (maxX - minX).coerceIn(constraints.minWidth, constraints.maxWidth)
        val totalH = (maxY - minY).coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(totalW, totalH) {
            basePlaceable?.placeRelative(-minX, -minY)
            if (isExpanded) {
                subPlaceable?.placeRelative(placement.x - minX, placement.y - minY)
            }
        }
    }
}
