package com.capture.engine.overlay

import android.graphics.BlurMaskFilter
import android.graphics.Rect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MobileFriendly
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
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
    subAlign: String,
    subAlignModifier: String,
    expandedTarget: ExpandedTarget = ExpandedTarget.JOINED_BOTH,
    stayOnScreen: Boolean,
    gapPx: Int = 16,
    screenMargin: Int = 16
): SubPlacement {
    val effectiveGap = gapPx
    var effectiveDir = subDirection

    // Auto-flip for clearance if stayOnScreen is enabled and opposite side has more room
    if (stayOnScreen) {
        when (subDirection) {
            "up" -> {
                val idealY = baseAnchorY - subH - effectiveGap
                val spaceAbove = baseAnchorY - effectiveGap - screenMargin
                val spaceBelow = screenHeight - screenMargin - (baseAnchorY + baseH + effectiveGap)
                if (idealY < screenMargin && spaceBelow > spaceAbove) {
                    effectiveDir = "down"
                }
            }
            "down" -> {
                val idealY = baseAnchorY + baseH + effectiveGap
                val spaceBelow = screenHeight - screenMargin - (baseAnchorY + baseH + effectiveGap)
                val spaceAbove = baseAnchorY - effectiveGap - screenMargin
                if (idealY + subH > screenHeight - screenMargin && spaceAbove > spaceBelow) {
                    effectiveDir = "up"
                }
            }
            "left" -> {
                val idealX = baseAnchorX - subW - effectiveGap
                val spaceLeft = baseAnchorX - effectiveGap - screenMargin
                val spaceRight = screenWidth - screenMargin - (baseAnchorX + baseW + effectiveGap)
                if (idealX < screenMargin && spaceRight > spaceLeft) {
                    effectiveDir = "right"
                }
            }
            "right" -> {
                val idealX = baseAnchorX + baseW + effectiveGap
                val spaceRight = screenWidth - screenMargin - (baseAnchorX + baseW + effectiveGap)
                val spaceLeft = baseAnchorX - effectiveGap - screenMargin
                if (idealX + subW > screenWidth - screenMargin && spaceLeft > spaceRight) {
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
            // Anchor relative to base icon (menu or action button at end of base bar)
            val iconW = if (baseW > 0) baseW / 2 else 0
            val anchorStart = baseAnchorX + baseW - iconW
            val anchorCenter = baseAnchorX + baseW - (iconW / 2)
            val anchorEnd = baseAnchorX + baseW
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
            // Anchor relative to base icon (menu or action button at bottom of vertical bar)
            val iconH = if (baseH > 0) baseH / 2 else 0
            val iconStartY = baseAnchorY + baseH - iconH
            val iconCenterY = baseAnchorY + baseH - (iconH / 2)
            val iconEndY = baseAnchorY + baseH
            targetY = when (subAlignModifier) {
                "start", "top", "up" -> iconStartY
                "end", "bottom", "down" -> iconEndY - subH
                else -> iconCenterY - (subH / 2)
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
        val maxY = (screenHeight - subH - screenMargin).coerceAtLeast(minY)

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
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
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
    val customIcons by settings.toolbarCustomIconsFlow.collectAsState()
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

    val dormantW: Dp = when (dormantStyle) {
        "small icon" -> (iconSize + 12f).dp.coerceAtLeast(36.dp)
        "vertical bar" -> 20.dp
        else -> (iconSize * 2.5f).dp.coerceAtLeast(60.dp)
    }
    val dormantH: Dp = when (dormantStyle) {
        "small icon" -> (iconSize + 12f).dp.coerceAtLeast(36.dp)
        "vertical bar" -> (iconSize * 2.5f).dp.coerceAtLeast(60.dp)
        else -> 20.dp
    }
    val dormantRadiusDp: Dp = when (dormantStyle) {
        "small icon" -> ((iconSize + 12f) / 2).dp
        else -> 10.dp
    }

    val isHoriz = baseLayout == "horiz"
    val customCount = customIcons.size.coerceAtLeast(1)

    val awakeW: Dp
    val awakeH: Dp

    if (awakeStyle == "all icons") {
        val inputClusterW = (buttonSize + 3.dp) * 6 + 8.dp
        val contextClusterW = (menuButtonSize + 3.dp) * 6 + 8.dp
        val maxRowW = if (inputClusterW > contextClusterW) inputClusterW else contextClusterW
        val stackedH = buttonSize + menuButtonSize + 15.dp

        if (isHoriz) {
            awakeW = maxRowW
            awakeH = stackedH
        } else {
            awakeW = stackedH
            awakeH = maxRowW
        }
    } else {
        val longAxisDp = (buttonSize + 3.dp) * customCount + 5.dp
        val shortAxisDp = buttonSize + 8.dp

        if (isHoriz) {
            awakeW = longAxisDp
            awakeH = shortAxisDp
        } else {
            awakeW = shortAxisDp
            awakeH = longAxisDp
        }
    }

    val morphProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.74f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ToolbarMorphProgress"
    )

    val animatedWidth by animateDpAsState(
        targetValue = if (expanded) awakeW else dormantW,
        animationSpec = spring(
            dampingRatio = 0.74f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ToolbarMorphWidth"
    )

    val animatedHeight by animateDpAsState(
        targetValue = if (expanded) awakeH else dormantH,
        animationSpec = spring(
            dampingRatio = 0.74f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ToolbarMorphHeight"
    )

    val currentRadiusDp = lerp(dormantRadiusDp, 20.dp, morphProgress)

    var dragVelocityTrackerX by remember { mutableStateOf(0f) }
    var dragVelocityTrackerY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .padding(12.dp)
            .pointerInput(expanded, dormantLocked, awakeLocked, wakeGesture, sleepGesture) {
                if (expanded) {
                    if (!awakeLocked) {
                        detectDragGestures(
                            onDragStart = {
                                onDragStart()
                            },
                            onDragEnd = {
                                if (sleepGesture == "flick") {
                                    val speed = abs(dragVelocityTrackerX) + abs(dragVelocityTrackerY)
                                    if (speed > 60f) {
                                        onFlickSleep()
                                    }
                                }
                                dragVelocityTrackerX = 0f
                                dragVelocityTrackerY = 0f
                                onDragEnd()
                            },
                            onDragCancel = {
                                dragVelocityTrackerX = 0f
                                dragVelocityTrackerY = 0f
                                onDragEnd()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragVelocityTrackerX = dragAmount.x
                                dragVelocityTrackerY = dragAmount.y
                                onDrag(dragAmount.x, dragAmount.y)
                            }
                        )
                    }
                } else {
                    if (!dormantLocked) {
                        detectDragGestures(
                            onDragStart = {
                                onDragStart()
                            },
                            onDragEnd = {
                                onDragEnd()
                            },
                            onDragCancel = {
                                onDragEnd()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            }
                        )
                    }
                }
            }
            .pointerInput(expanded, wakeGesture, sleepGesture) {
                if (!expanded) {
                    when (wakeGesture) {
                        "double tap" -> {
                            detectTapGestures(
                                onDoubleTap = { onExpandToggle() }
                            )
                        }
                        "long tap" -> {
                            detectTapGestures(
                                onLongPress = { onExpandToggle() }
                            )
                        }
                        else -> {
                            // "single tap" (default) -> ONLY onTap, completely omitting onDoubleTap/onLongPress
                            // This eliminates Compose's double-tap timeout wait and fires INSTANTANEOUSLY on tap!
                            detectTapGestures(
                                onTap = { onExpandToggle() }
                            )
                        }
                    }
                } else {
                    if (sleepGesture == "double tap" || sleepGesture.isBlank()) {
                        detectTapGestures(
                            onDoubleTap = { onCollapse() }
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val toolbarShape = RoundedCornerShape(currentRadiusDp)
        Surface(
            shape = toolbarShape,
            color = colors.containerColor,
            tonalElevation = tonalElevation.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier
                .size(animatedWidth, animatedHeight)
                .cleanDropShadow(
                    shape = toolbarShape,
                    elevation = shadowElevation.dp
                )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 1. DORMANT GLYPH (fades out as morphProgress goes 0 -> 0.3)
                if (morphProgress < 0.95f) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                this.alpha = (1f - morphProgress * 3.0f).coerceIn(0f, 1f)
                                this.scaleX = 0.7f + 0.3f * (1f - morphProgress)
                                this.scaleY = 0.7f + 0.3f * (1f - morphProgress)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when (dormantStyle) {
                            "small icon" -> {
                                Box(
                                    modifier = Modifier
                                        .size((iconSize + 12f).dp.coerceAtLeast(36.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Flare,
                                        contentDescription = "Dormant Toolbar Icon",
                                        tint = colors.contentColor,
                                        modifier = Modifier.size(iconSizeDp * 0.85f)
                                    )
                                }
                            }
                            "vertical bar" -> {
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height((iconSize * 2.5f).dp.coerceAtLeast(60.dp)),
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
                                        .height(20.dp),
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

                // 2. AWAKE ICONS CLUSTER (fades in as morphProgress goes 0.15 -> 1.0)
                if (morphProgress > 0.05f) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                this.alpha = ((morphProgress - 0.15f) / 0.85f).coerceIn(0f, 1f)
                                this.scaleX = 0.85f + 0.15f * morphProgress
                                this.scaleY = 0.85f + 0.15f * morphProgress
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (awakeStyle == "all icons") {
                            val isInputFirst = resolveAllIconsOrder(
                                orderSetting = allIconsOrder,
                                isHoriz = isHoriz,
                                windowPosX = windowPosX,
                                windowPosY = windowPosY,
                                screenWidth = screenWidth,
                                screenHeight = screenHeight
                            )

                            if (isHoriz) {
                                Column(
                                    modifier = Modifier
                                        .width(IntrinsicSize.Min)
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    if (isInputFirst) {
                                        InputSubGroupContent(true, visibleInputIcons, pointActive, activeSelection, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor) { settings.lastUsedInputType = it; onInputSubSelected(it) }
                                        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                        ContextSubGroupContent(true, screenGrab, { settings.isScreenGrabActive = it }, appTree, { settings.isAppTreeActive = it }, highlightedText, { settings.isHighlightedTextActive = it }, media, { settings.isMediaActive = it }, { onSelectAllContext(); settings.isScreenGrabActive = true; settings.isAppTreeActive = true; settings.isHighlightedTextActive = true; settings.isMediaActive = true }, { onSelectNoneContext(); settings.isScreenGrabActive = false; settings.isAppTreeActive = false; settings.isHighlightedTextActive = false; settings.isMediaActive = false }, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                                    } else {
                                        ContextSubGroupContent(true, screenGrab, { settings.isScreenGrabActive = it }, appTree, { settings.isAppTreeActive = it }, highlightedText, { settings.isHighlightedTextActive = it }, media, { settings.isMediaActive = it }, { onSelectAllContext(); settings.isScreenGrabActive = true; settings.isAppTreeActive = true; settings.isHighlightedTextActive = true; settings.isMediaActive = true }, { onSelectNoneContext(); settings.isScreenGrabActive = false; settings.isAppTreeActive = false; settings.isHighlightedTextActive = false; settings.isMediaActive = false }, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                                        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                        InputSubGroupContent(true, visibleInputIcons, pointActive, activeSelection, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor) { settings.lastUsedInputType = it; onInputSubSelected(it) }
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
                                        InputSubGroupContent(false, visibleInputIcons, pointActive, activeSelection, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor) { settings.lastUsedInputType = it; onInputSubSelected(it) }
                                        VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                        ContextSubGroupContent(false, screenGrab, { settings.isScreenGrabActive = it }, appTree, { settings.isAppTreeActive = it }, highlightedText, { settings.isHighlightedTextActive = it }, media, { settings.isMediaActive = it }, { onSelectAllContext(); settings.isScreenGrabActive = true; settings.isAppTreeActive = true; settings.isHighlightedTextActive = true; settings.isMediaActive = true }, { onSelectNoneContext(); settings.isScreenGrabActive = false; settings.isAppTreeActive = false; settings.isHighlightedTextActive = false; settings.isMediaActive = false }, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                                    } else {
                                        ContextSubGroupContent(false, screenGrab, { settings.isScreenGrabActive = it }, appTree, { settings.isAppTreeActive = it }, highlightedText, { settings.isHighlightedTextActive = it }, media, { settings.isMediaActive = it }, { onSelectAllContext(); settings.isScreenGrabActive = true; settings.isAppTreeActive = true; settings.isHighlightedTextActive = true; settings.isMediaActive = true }, { onSelectNoneContext(); settings.isScreenGrabActive = false; settings.isAppTreeActive = false; settings.isHighlightedTextActive = false; settings.isMediaActive = false }, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                                        VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                        InputSubGroupContent(false, visibleInputIcons, pointActive, activeSelection, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor) { settings.lastUsedInputType = it; onInputSubSelected(it) }
                                    }
                                }
                            }
                        } else {
                            val (lastIcon, lastDesc) = when (lastUsedInput) {
                                "mic_audio" -> Icons.Default.Mic to "Record"
                                "clipboard_paste" -> Icons.Default.ContentPaste to "Paste"
                                "text_snippet" -> Icons.Default.Keyboard to "Type"
                                "camera" -> Icons.Default.PhotoCamera to "Camera"
                                "point_mode" -> Icons.Default.AdsClick to "Point"
                                else -> Icons.Default.RecordVoiceOver to "Dictate"
                            }

                            val renderItem: @Composable (String) -> Unit = { key ->
                                when (key) {
                                    "speech_to_text" -> ToolbarIconButton(
                                        icon = Icons.Default.RecordVoiceOver,
                                        contentDescription = "Dictate",
                                        buttonSize = buttonSize,
                                        iconSize = iconSizeDp,
                                        activeContainerColor = colors.activeContainerColor,
                                        activeContentColor = colors.activeContentColor,
                                        contentColor = colors.contentColor,
                                        onClick = { onInputSubSelected("speech_to_text") }
                                    )
                                    "mic_audio" -> ToolbarIconButton(
                                        icon = Icons.Default.Mic,
                                        contentDescription = "Record Audio",
                                        buttonSize = buttonSize,
                                        iconSize = iconSizeDp,
                                        activeContainerColor = colors.activeContainerColor,
                                        activeContentColor = colors.activeContentColor,
                                        contentColor = colors.contentColor,
                                        onClick = { onInputSubSelected("mic_audio") }
                                    )
                                    "clipboard_paste" -> ToolbarIconButton(
                                        icon = Icons.Default.ContentPaste,
                                        contentDescription = "Paste Clipboard",
                                        buttonSize = buttonSize,
                                        iconSize = iconSizeDp,
                                        activeContainerColor = colors.activeContainerColor,
                                        activeContentColor = colors.activeContentColor,
                                        contentColor = colors.contentColor,
                                        onClick = { onInputSubSelected("clipboard_paste") }
                                    )
                                    "text_snippet" -> ToolbarIconButton(
                                        icon = Icons.Default.Keyboard,
                                        contentDescription = "Type Text",
                                        buttonSize = buttonSize,
                                        iconSize = iconSizeDp,
                                        activeContainerColor = colors.activeContainerColor,
                                        activeContentColor = colors.activeContentColor,
                                        contentColor = colors.contentColor,
                                        onClick = { onInputSubSelected("text_snippet") }
                                    )
                                    "camera" -> ToolbarIconButton(
                                        icon = Icons.Default.PhotoCamera,
                                        contentDescription = "Camera",
                                        buttonSize = buttonSize,
                                        iconSize = iconSizeDp,
                                        activeContainerColor = colors.activeContainerColor,
                                        activeContentColor = colors.activeContentColor,
                                        contentColor = colors.contentColor,
                                        onClick = { onInputSubSelected("camera") }
                                    )
                                    "point_mode" -> ToolbarIconButton(
                                        icon = Icons.Default.AdsClick,
                                        contentDescription = "Point Mode",
                                        buttonSize = buttonSize,
                                        iconSize = iconSizeDp,
                                        isActive = pointActive,
                                        activeContainerColor = colors.activeContainerColor,
                                        activeContentColor = colors.activeContentColor,
                                        contentColor = colors.contentColor,
                                        onClick = onTogglePointMode
                                    )
                                    "last_used" -> ToolbarIconButton(
                                        icon = lastIcon,
                                        contentDescription = "$lastDesc (Last Used)",
                                        buttonSize = buttonSize,
                                        iconSize = iconSizeDp,
                                        isActive = target == ExpandedTarget.INPUT_ONLY || target == ExpandedTarget.JOINED_BOTH,
                                        activeContainerColor = colors.activeContainerColor,
                                        activeContentColor = colors.activeContentColor,
                                        contentColor = colors.contentColor,
                                        onClick = onInputClick
                                    )
                                    "menu" -> ToolbarIconButton(
                                        icon = Icons.Default.Menu,
                                        contentDescription = "Menu",
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

                            if (baseLayout == "horiz") {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    customIcons.forEach { key -> renderItem(key) }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    customIcons.forEach { key -> renderItem(key) }
                                }
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
    val awakeStyle by settings.toolbarAwakeStyleFlow.collectAsState()

    val screenGrab by settings.isScreenGrabActiveFlow.collectAsState()
    val appTree by settings.isAppTreeActiveFlow.collectAsState()
    val highlightedText by settings.isHighlightedTextActiveFlow.collectAsState()
    val media by settings.isMediaActiveFlow.collectAsState()

    val isBaseHoriz = baseLayout == "horiz"
    val isFlow = subLayout == "flow axis"
    val isSubHoriz = if (isFlow) isBaseHoriz else !isBaseHoriz

    val customIcons by settings.toolbarCustomIconsFlow.collectAsState()
    val iconSizeDp = (iconSize - 4f).dp.coerceIn(15.dp, 20.dp)
    val buttonSize = (iconSize + 8f).dp.coerceIn(28.dp, 36.dp)

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
        val subMenuShape = RoundedCornerShape(20.dp)
        Surface(
            shape = subMenuShape,
            color = colors.containerColor,
            tonalElevation = tonalElevation.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier
                .cleanDropShadow(
                    shape = subMenuShape,
                    elevation = shadowElevation.dp
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Consume clicks on the surface so it doesn't trigger dismissal
                }
        ) {
            if (isSubHoriz) {
                // Horizontal sub-items -> Stack subgroups vertically in a Column (6x2 grid)
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Min)
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    InputSubGroupContent(
                        isHoriz = true,
                        pointActive = pointActive,
                        activeSelection = activeSelection,
                        buttonSize = buttonSize,
                        iconSizeDp = iconSizeDp,
                        contentColor = colors.contentColor,
                        activeContainerColor = colors.activeContainerColor,
                        activeContentColor = colors.activeContentColor,
                        onInputSelected = onInputSelected
                    )
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    ContextSubGroupContent(
                        isHoriz = true,
                        screenGrab = screenGrab,
                        onScreenGrabChange = { settings.isScreenGrabActive = it },
                        appTree = appTree,
                        onAppTreeChange = { settings.isAppTreeActive = it },
                        highlightedText = highlightedText,
                        onHighlightedTextChange = { settings.isHighlightedTextActive = it },
                        media = media,
                        onMediaChange = { settings.isMediaActive = it },
                        onSelectAllContext = {
                            onSelectAllContext()
                            settings.isScreenGrabActive = true
                            settings.isAppTreeActive = true
                            settings.isHighlightedTextActive = true
                            settings.isMediaActive = true
                        },
                        onSelectNoneContext = {
                            onSelectNoneContext()
                            settings.isScreenGrabActive = false
                            settings.isAppTreeActive = false
                            settings.isHighlightedTextActive = false
                            settings.isMediaActive = false
                        },
                        menuButtonSize = buttonSize,
                        menuIconSizeDp = iconSizeDp,
                        activeContainerColor = colors.activeContainerColor,
                        activeContentColor = colors.activeContentColor,
                        contentColor = colors.contentColor
                    )
                }
            } else {
                // Vertical sub-items -> Place subgroups side-by-side in a Row (2x6 grid)
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    InputSubGroupContent(
                        isHoriz = false,
                        pointActive = pointActive,
                        activeSelection = activeSelection,
                        buttonSize = buttonSize,
                        iconSizeDp = iconSizeDp,
                        contentColor = colors.contentColor,
                        activeContainerColor = colors.activeContainerColor,
                        activeContentColor = colors.activeContentColor,
                        onInputSelected = onInputSelected
                    )
                    VerticalDivider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    ContextSubGroupContent(
                        isHoriz = false,
                        screenGrab = screenGrab,
                        onScreenGrabChange = { settings.isScreenGrabActive = it },
                        appTree = appTree,
                        onAppTreeChange = { settings.isAppTreeActive = it },
                        highlightedText = highlightedText,
                        onHighlightedTextChange = { settings.isHighlightedTextActive = it },
                        media = media,
                        onMediaChange = { settings.isMediaActive = it },
                        onSelectAllContext = {
                            onSelectAllContext()
                            settings.isScreenGrabActive = true
                            settings.isAppTreeActive = true
                            settings.isHighlightedTextActive = true
                            settings.isMediaActive = true
                        },
                        onSelectNoneContext = {
                            onSelectNoneContext()
                            settings.isScreenGrabActive = false
                            settings.isAppTreeActive = false
                            settings.isHighlightedTextActive = false
                            settings.isMediaActive = false
                        },
                        menuButtonSize = buttonSize,
                        menuIconSizeDp = iconSizeDp,
                        activeContainerColor = colors.activeContainerColor,
                        activeContentColor = colors.activeContentColor,
                        contentColor = colors.contentColor
                    )
                }
            }
        }
    }
}

@Composable
fun InputSubGroupContent(
    isHoriz: Boolean,
    visibleInputIcons: Set<String> = emptySet(),
    pointActive: Boolean = false,
    activeSelection: Any? = null,
    buttonSize: Dp,
    iconSizeDp: Dp,
    contentColor: Color,
    activeContainerColor: Color,
    activeContentColor: Color,
    onInputSelected: (String) -> Unit
) {
    val items: @Composable () -> Unit = {
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
        ToolbarIconButton(
            icon = Icons.Default.PhotoCamera,
            contentDescription = "Camera",
            buttonSize = buttonSize,
            iconSize = iconSizeDp,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
            contentColor = contentColor,
            onClick = { onInputSelected("camera") }
        )
        ToolbarIconButton(
            icon = Icons.Default.AdsClick,
            contentDescription = "Point",
            buttonSize = buttonSize,
            iconSize = iconSizeDp,
            isActive = pointActive || activeSelection != null,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
            contentColor = contentColor,
            onClick = { onInputSelected("point_mode") }
        )
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

/**
 * Custom drop shadow modifier that clips out the shape interior.
 * When the surface has transparency/alpha, the shadow will NOT bleed or darken
 * the translucent interior, keeping transparency crystal clear while rendering
 * a realistic ambient and spot drop shadow outside the shape.
 */
fun Modifier.cleanDropShadow(
    shape: Shape,
    elevation: Dp,
    shadowColor: Color = Color.Black
): Modifier = if (elevation <= 0.dp) this else this.drawBehind {
    val elevPx = elevation.toPx()
    if (elevPx <= 0f) return@drawBehind

    val shapeOutline = shape.createOutline(size, layoutDirection, this)
    val shapePath = Path().apply {
        addOutline(shapeOutline)
    }
    val androidPath = shapePath.asAndroidPath()

    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas

        nativeCanvas.save()
        // Clip out the interior of the shape using Difference so shadow is strictly outside
        canvas.clipPath(shapePath, ClipOp.Difference)

        // 1. Ambient shadow (soft, centered)
        val ambientBlur = (elevPx * 1.5f).coerceAtLeast(1f)
        val ambientAlpha = (0.08f + 0.02f * (elevPx / 4f)).coerceIn(0.04f, 0.18f)
        val ambientPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = shadowColor.copy(alpha = ambientAlpha).toArgb()
            maskFilter = BlurMaskFilter(ambientBlur, BlurMaskFilter.Blur.NORMAL)
        }
        nativeCanvas.drawPath(androidPath, ambientPaint)

        // 2. Spot shadow (directional, slight downward offset)
        val spotBlur = (elevPx * 2.2f).coerceAtLeast(1f)
        val spotDy = (elevPx * 0.75f).coerceAtLeast(0.5f)
        val spotAlpha = (0.12f + 0.03f * (elevPx / 4f)).coerceIn(0.06f, 0.28f)
        val spotPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = shadowColor.copy(alpha = spotAlpha).toArgb()
            maskFilter = BlurMaskFilter(spotBlur, BlurMaskFilter.Blur.NORMAL)
        }
        nativeCanvas.save()
        nativeCanvas.translate(0f, spotDy)
        nativeCanvas.drawPath(androidPath, spotPaint)
        nativeCanvas.restore()

        nativeCanvas.restore()
    }
}

@Composable
fun ToolbarLayoutEngine(
    baseLayout: String,
    subLayout: String,
    subDirection: String,
    subAlign: String,
    subAlignModifier: String,
    expandedTarget: ExpandedTarget,
    awakeStyle: String = "custom",
    customIcons: List<String> = listOf("last_used", "menu"),
    allIconsOrder: String = "inner",
    colors: ToolbarColors,
    buttonSize: Dp,
    iconSizeDp: Dp,
    menuButtonSize: Dp,
    menuIconSizeDp: Dp,
    dividerSize: Dp,
    lastUsedInput: String,
    visibleInputIcons: Set<String> = emptySet(),
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
    val isAllIcons = awakeStyle == "all icons"
    val baseLongEdgeDp = if (isAllIcons) {
        buttonSize * 6 + menuButtonSize * 6 + 24.dp
    } else {
        (buttonSize * customIcons.size.coerceAtLeast(1)) + ((customIcons.size - 1).coerceAtLeast(0) * 3).dp + 8.dp
    }

    val isExpanded = expandedTarget != ExpandedTarget.NONE

    val (lastIcon, lastDesc) = when (lastUsedInput) {
        "mic_audio" -> Icons.Default.Mic to "Record"
        "clipboard_paste" -> Icons.Default.ContentPaste to "Paste"
        "text_snippet" -> Icons.Default.Keyboard to "Type"
        "camera" -> Icons.Default.PhotoCamera to "Camera"
        "point_mode" -> Icons.Default.AdsClick to "Point"
        else -> Icons.Default.RecordVoiceOver to "Dictate"
    }

    val baseContent: @Composable () -> Unit = {
        val previewBaseShape = RoundedCornerShape(20.dp)
        Surface(
            shape = previewBaseShape,
            color = colors.containerColor,
            tonalElevation = tonalElevation,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.cleanDropShadow(
                shape = previewBaseShape,
                elevation = shadowElevation
            )
        ) {
            if (isAllIcons) {
                val isInputFirst = resolveAllIconsOrder(
                    orderSetting = allIconsOrder,
                    isHoriz = isBaseHoriz,
                    windowPosX = 0,
                    windowPosY = 0,
                    screenWidth = 1000,
                    screenHeight = 1000
                )
                if (isBaseHoriz) {
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Min)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (isInputFirst) {
                            InputSubGroupContent(true, visibleInputIcons, pointActive, activeSelection, buttonSize = buttonSize, iconSizeDp = iconSizeDp, contentColor = colors.contentColor, activeContainerColor = colors.activeContainerColor, activeContentColor = colors.activeContentColor, onInputSelected = onInputSubSelected)
                            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            ContextSubGroupContent(true, screenGrab, onScreenGrabChange, appTree, onAppTreeChange, highlightedText, onHighlightedTextChange, media, onMediaChange, onSelectAllContext, onSelectNoneContext, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                        } else {
                            ContextSubGroupContent(true, screenGrab, onScreenGrabChange, appTree, onAppTreeChange, highlightedText, onHighlightedTextChange, media, onMediaChange, onSelectAllContext, onSelectNoneContext, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            InputSubGroupContent(true, visibleInputIcons, pointActive, activeSelection, buttonSize = buttonSize, iconSizeDp = iconSizeDp, contentColor = colors.contentColor, activeContainerColor = colors.activeContainerColor, activeContentColor = colors.activeContentColor, onInputSelected = onInputSubSelected)
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
                            InputSubGroupContent(false, visibleInputIcons, pointActive, activeSelection, buttonSize = buttonSize, iconSizeDp = iconSizeDp, contentColor = colors.contentColor, activeContainerColor = colors.activeContainerColor, activeContentColor = colors.activeContentColor, onInputSelected = onInputSubSelected)
                            VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            ContextSubGroupContent(false, screenGrab, onScreenGrabChange, appTree, onAppTreeChange, highlightedText, onHighlightedTextChange, media, onMediaChange, onSelectAllContext, onSelectNoneContext, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                        } else {
                            ContextSubGroupContent(false, screenGrab, onScreenGrabChange, appTree, onAppTreeChange, highlightedText, onHighlightedTextChange, media, onMediaChange, onSelectAllContext, onSelectNoneContext, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                            VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            InputSubGroupContent(false, visibleInputIcons, pointActive, activeSelection, buttonSize = buttonSize, iconSizeDp = iconSizeDp, contentColor = colors.contentColor, activeContainerColor = colors.activeContainerColor, activeContentColor = colors.activeContentColor, onInputSelected = onInputSubSelected)
                        }
                    }
                }
            } else {
                val renderItem: @Composable (String) -> Unit = { key ->
                    when (key) {
                        "speech_to_text" -> ToolbarIconButton(
                            icon = Icons.Default.RecordVoiceOver,
                            contentDescription = "Dictate",
                            buttonSize = buttonSize,
                            iconSize = iconSizeDp,
                            activeContainerColor = colors.activeContainerColor,
                            activeContentColor = colors.activeContentColor,
                            contentColor = colors.contentColor,
                            onClick = { onInputSubSelected("speech_to_text") }
                        )
                        "mic_audio" -> ToolbarIconButton(
                            icon = Icons.Default.Mic,
                            contentDescription = "Record Audio",
                            buttonSize = buttonSize,
                            iconSize = iconSizeDp,
                            activeContainerColor = colors.activeContainerColor,
                            activeContentColor = colors.activeContentColor,
                            contentColor = colors.contentColor,
                            onClick = { onInputSubSelected("mic_audio") }
                        )
                        "clipboard_paste" -> ToolbarIconButton(
                            icon = Icons.Default.ContentPaste,
                            contentDescription = "Paste Clipboard",
                            buttonSize = buttonSize,
                            iconSize = iconSizeDp,
                            activeContainerColor = colors.activeContainerColor,
                            activeContentColor = colors.activeContentColor,
                            contentColor = colors.contentColor,
                            onClick = { onInputSubSelected("clipboard_paste") }
                        )
                        "text_snippet" -> ToolbarIconButton(
                            icon = Icons.Default.Keyboard,
                            contentDescription = "Type Text",
                            buttonSize = buttonSize,
                            iconSize = iconSizeDp,
                            activeContainerColor = colors.activeContainerColor,
                            activeContentColor = colors.activeContentColor,
                            contentColor = colors.contentColor,
                            onClick = { onInputSubSelected("text_snippet") }
                        )
                        "camera" -> ToolbarIconButton(
                            icon = Icons.Default.PhotoCamera,
                            contentDescription = "Camera",
                            buttonSize = buttonSize,
                            iconSize = iconSizeDp,
                            activeContainerColor = colors.activeContainerColor,
                            activeContentColor = colors.activeContentColor,
                            contentColor = colors.contentColor,
                            onClick = { onInputSubSelected("camera") }
                        )
                        "point_mode" -> ToolbarIconButton(
                            icon = Icons.Default.AdsClick,
                            contentDescription = "Point Mode",
                            buttonSize = buttonSize,
                            iconSize = iconSizeDp,
                            isActive = pointActive,
                            activeContainerColor = colors.activeContainerColor,
                            activeContentColor = colors.activeContentColor,
                            contentColor = colors.contentColor,
                            onClick = onTogglePointMode
                        )
                        "last_used" -> ToolbarIconButton(
                            icon = lastIcon,
                            contentDescription = "$lastDesc (Last Used)",
                            buttonSize = buttonSize,
                            iconSize = iconSizeDp,
                            isActive = expandedTarget == ExpandedTarget.INPUT_ONLY || expandedTarget == ExpandedTarget.JOINED_BOTH,
                            activeContainerColor = colors.activeContainerColor,
                            activeContentColor = colors.activeContentColor,
                            contentColor = colors.contentColor,
                            onClick = onInputClick
                        )
                        "menu" -> ToolbarIconButton(
                            icon = Icons.Default.Menu,
                            contentDescription = "Menu",
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

                if (isBaseHoriz) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        customIcons.forEach { key -> renderItem(key) }
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        customIcons.forEach { key -> renderItem(key) }
                    }
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
            if (isSubHoriz) {
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Min)
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    InputSubGroupContent(
                        isHoriz = true,
                        pointActive = pointActive,
                        activeSelection = activeSelection,
                        buttonSize = buttonSize,
                        iconSizeDp = iconSizeDp,
                        contentColor = colors.contentColor,
                        activeContainerColor = colors.activeContainerColor,
                        activeContentColor = colors.activeContentColor,
                        onInputSelected = onInputSubSelected
                    )
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    ContextSubGroupContent(
                        isHoriz = true,
                        screenGrab = screenGrab,
                        onScreenGrabChange = onScreenGrabChange,
                        appTree = appTree,
                        onAppTreeChange = onAppTreeChange,
                        highlightedText = highlightedText,
                        onHighlightedTextChange = onHighlightedTextChange,
                        media = media,
                        onMediaChange = onMediaChange,
                        onSelectAllContext = onSelectAllContext,
                        onSelectNoneContext = onSelectNoneContext,
                        menuButtonSize = buttonSize,
                        menuIconSizeDp = iconSizeDp,
                        activeContainerColor = colors.activeContainerColor,
                        activeContentColor = colors.activeContentColor,
                        contentColor = colors.contentColor
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    InputSubGroupContent(
                        isHoriz = false,
                        pointActive = pointActive,
                        activeSelection = activeSelection,
                        buttonSize = buttonSize,
                        iconSizeDp = iconSizeDp,
                        contentColor = colors.contentColor,
                        activeContainerColor = colors.activeContainerColor,
                        activeContentColor = colors.activeContentColor,
                        onInputSelected = onInputSubSelected
                    )
                    VerticalDivider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    ContextSubGroupContent(
                        isHoriz = false,
                        screenGrab = screenGrab,
                        onScreenGrabChange = onScreenGrabChange,
                        appTree = appTree,
                        onAppTreeChange = onAppTreeChange,
                        highlightedText = highlightedText,
                        onHighlightedTextChange = onHighlightedTextChange,
                        media = media,
                        onMediaChange = onMediaChange,
                        onSelectAllContext = onSelectAllContext,
                        onSelectNoneContext = onSelectNoneContext,
                        menuButtonSize = buttonSize,
                        menuIconSizeDp = iconSizeDp,
                        activeContainerColor = colors.activeContainerColor,
                        activeContentColor = colors.activeContentColor,
                        contentColor = colors.contentColor
                    )
                }
            }
        }
    }

    val density = LocalDensity.current.density
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

        val gapPx = (16 * density).toInt()
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
            subAlign = subAlign,
            subAlignModifier = subAlignModifier,
            expandedTarget = expandedTarget,
            stayOnScreen = false,
            gapPx = gapPx,
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
