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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingToolbar(
    isExpanded: StateFlow<Boolean>,
    isPointActive: StateFlow<Boolean>,
    hasActiveSelection: StateFlow<Rect?>,
    windowPosX: Int,
    windowPosY: Int,
    screenWidth: Int,
    screenHeight: Int,
    onExpandToggle: () -> Unit,
    onCollapse: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onFlickSleep: () -> Unit,
    onInputSelected: (String) -> Unit,
    onSelectAllContext: () -> Unit,
    onSelectNoneContext: () -> Unit,
    onTogglePointMode: () -> Unit,
    onBaseOffsetChanged: (Int, Int) -> Unit = { _, _ -> }
) {
    val expanded by isExpanded.collectAsState()
    val pointActive by isPointActive.collectAsState()
    val activeSelection by hasActiveSelection.collectAsState()
    val settings = remember { CaptureApplication.instance.settingsRepository }

    // Dimensions and appearance
    val iconSize by settings.toolbarIconSizeFlow.collectAsState()
    val alpha by settings.toolbarAlphaFlow.collectAsState()
    val colorRole by settings.toolbarColorRoleFlow.collectAsState()
    val customSeedColor by settings.toolbarCustomSeedColorFlow.collectAsState()
    val visibleInputIcons by settings.visibleInputIconsFlow.collectAsState()
    val lastUsedInput by settings.lastUsedInputTypeFlow.collectAsState()

    // Core layout settings
    val baseLayout by settings.toolbarBaseLayoutFlow.collectAsState() // "horiz", "vert"
    val subLayout by settings.toolbarSubLayoutFlow.collectAsState() // "flow axis", "cross axis"
    val subDirection by settings.toolbarSubDirectionFlow.collectAsState() // "down", "up", "left", "right"
    val subConnection by settings.toolbarSubConnectionFlow.collectAsState() // "separate", "joined"
    val subAlign by settings.toolbarSubAlignFlow.collectAsState() // "full base", "base icon"
    val subAlignModifier by settings.toolbarSubAlignModifierFlow.collectAsState() // "top", "middle", "bottom", "start", "center", "end"

    // Dormant & Awake configs
    val dormantStyle by settings.toolbarDormantStyleFlow.collectAsState() // "horizontal bar", "small icon", "vertical bar"
    val dormantLocked by settings.toolbarDormantLockedFlow.collectAsState()
    val awakeStyle by settings.toolbarAwakeStyleFlow.collectAsState() // "double icon", "single icon", "all icons"
    val awakeLocked by settings.toolbarAwakeLockedFlow.collectAsState()
    val allIconsOrder by settings.toolbarAllIconsOrderFlow.collectAsState() // "inner", "outer", "left", "right", "up", "down"
    val wakeGesture by settings.toolbarWakeGestureFlow.collectAsState()
    val sleepGesture by settings.toolbarSleepGestureFlow.collectAsState()

    // Active Context states
    var screenGrab by remember { mutableStateOf(settings.isScreenGrabActive) }
    var appTree by remember { mutableStateOf(settings.isAppTreeActive) }
    var highlightedText by remember { mutableStateOf(settings.isHighlightedTextActive) }
    var media by remember { mutableStateOf(settings.isMediaActive) }

    // Sub-expansion state
    var expandedTarget by remember { mutableStateOf(ExpandedTarget.NONE) }

    LaunchedEffect(expanded) {
        if (!expanded) {
            expandedTarget = ExpandedTarget.NONE
        }
    }

    // Dynamic dimension tokens (compact to prevent overflowing screens)
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

    Crossfade(
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
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
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
                when (awakeStyle) {
                    "all icons" -> {
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
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            if (isHoriz) {
                                Column(
                                    modifier = Modifier
                                        .width(IntrinsicSize.Min)
                                        .padding(horizontal = 4.dp, vertical = 3.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    if (isInputFirst) {
                                        InputSubGroupContent(true, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor) { settings.lastUsedInputType = it; onInputSelected(it) }
                                        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                        ContextSubGroupContent(true, screenGrab, { screenGrab = it; settings.isScreenGrabActive = it }, appTree, { appTree = it; settings.isAppTreeActive = it }, highlightedText, { highlightedText = it; settings.isHighlightedTextActive = it }, media, { media = it; settings.isMediaActive = it }, pointActive, activeSelection, onTogglePointMode, { onSelectAllContext(); screenGrab = true; appTree = true; highlightedText = true; media = true }, { onSelectNoneContext(); screenGrab = false; appTree = false; highlightedText = false; media = false }, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                                    } else {
                                        ContextSubGroupContent(true, screenGrab, { screenGrab = it; settings.isScreenGrabActive = it }, appTree, { appTree = it; settings.isAppTreeActive = it }, highlightedText, { highlightedText = it; settings.isHighlightedTextActive = it }, media, { media = it; settings.isMediaActive = it }, pointActive, activeSelection, onTogglePointMode, { onSelectAllContext(); screenGrab = true; appTree = true; highlightedText = true; media = true }, { onSelectNoneContext(); screenGrab = false; appTree = false; highlightedText = false; media = false }, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                                        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                        InputSubGroupContent(true, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor) { settings.lastUsedInputType = it; onInputSelected(it) }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .height(IntrinsicSize.Min)
                                        .padding(horizontal = 3.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    if (isInputFirst) {
                                        InputSubGroupContent(false, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor) { settings.lastUsedInputType = it; onInputSelected(it) }
                                        VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                        ContextSubGroupContent(false, screenGrab, { screenGrab = it; settings.isScreenGrabActive = it }, appTree, { appTree = it; settings.isAppTreeActive = it }, highlightedText, { highlightedText = it; settings.isHighlightedTextActive = it }, media, { media = it; settings.isMediaActive = it }, pointActive, activeSelection, onTogglePointMode, { onSelectAllContext(); screenGrab = true; appTree = true; highlightedText = true; media = true }, { onSelectNoneContext(); screenGrab = false; appTree = false; highlightedText = false; media = false }, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                                    } else {
                                        ContextSubGroupContent(false, screenGrab, { screenGrab = it; settings.isScreenGrabActive = it }, appTree, { appTree = it; settings.isAppTreeActive = it }, highlightedText, { highlightedText = it; settings.isHighlightedTextActive = it }, media, { media = it; settings.isMediaActive = it }, pointActive, activeSelection, onTogglePointMode, { onSelectAllContext(); screenGrab = true; appTree = true; highlightedText = true; media = true }, { onSelectNoneContext(); screenGrab = false; appTree = false; highlightedText = false; media = false }, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                                        VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                        InputSubGroupContent(false, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor) { settings.lastUsedInputType = it; onInputSelected(it) }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        val isSingle = awakeStyle == "single icon"
                        val effectiveConnection = if (isSingle) "joined" else subConnection
                        val onInputLongTap = {
                            expandedTarget = if (effectiveConnection == "joined") {
                                if (expandedTarget == ExpandedTarget.JOINED_BOTH) ExpandedTarget.NONE else ExpandedTarget.JOINED_BOTH
                            } else {
                                if (expandedTarget == ExpandedTarget.INPUT_ONLY) ExpandedTarget.NONE else ExpandedTarget.INPUT_ONLY
                            }
                        }
                        val onContextLongTap = {
                            expandedTarget = if (effectiveConnection == "joined") {
                                if (expandedTarget == ExpandedTarget.JOINED_BOTH) ExpandedTarget.NONE else ExpandedTarget.JOINED_BOTH
                            } else {
                                if (expandedTarget == ExpandedTarget.CONTEXT_ONLY) ExpandedTarget.NONE else ExpandedTarget.CONTEXT_ONLY
                            }
                        }

                        ToolbarLayoutEngine(
                            baseLayout = baseLayout,
                            subLayout = subLayout,
                            subDirection = subDirection,
                            subAlign = subAlign,
                            subAlignModifier = subAlignModifier,
                            expandedTarget = expandedTarget,
                            isSingleIcon = isSingle,
                            colors = colors,
                            buttonSize = buttonSize,
                            iconSizeDp = iconSizeDp,
                            menuButtonSize = menuButtonSize,
                            menuIconSizeDp = menuIconSizeDp,
                            dividerSize = dividerSize,
                            lastUsedInput = lastUsedInput,
                            visibleInputIcons = visibleInputIcons,
                            hasActiveContext = hasActiveContext,
                            screenGrab = screenGrab,
                            appTree = appTree,
                            highlightedText = highlightedText,
                            media = media,
                            pointActive = pointActive,
                            activeSelection = activeSelection,
                            onInputClick = { onInputSelected(lastUsedInput) },
                            onInputLongClick = onInputLongTap,
                            onContextClick = { onContextLongTap() },
                            onContextLongClick = onContextLongTap,
                            onInputSubSelected = { settings.lastUsedInputType = it; expandedTarget = ExpandedTarget.NONE; onInputSelected(it) },
                            onScreenGrabChange = { screenGrab = it; settings.isScreenGrabActive = it },
                            onAppTreeChange = { appTree = it; settings.isAppTreeActive = it },
                            onHighlightedTextChange = { highlightedText = it; settings.isHighlightedTextActive = it },
                            onMediaChange = { media = it; settings.isMediaActive = it },
                            onTogglePointMode = onTogglePointMode,
                            onSelectAllContext = { onSelectAllContext(); screenGrab = true; appTree = true; highlightedText = true; media = true },
                            onSelectNoneContext = { onSelectNoneContext(); screenGrab = false; appTree = false; highlightedText = false; media = false },
                            onBaseOffsetChanged = onBaseOffsetChanged
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2D Custom Layout Matrix Engine.
 * Replaces nested Row/Column conditionals with mathematical placement on a 2D coordinate grid.
 */
@Composable
fun ToolbarMatrixLayout(
    modifier: Modifier = Modifier,
    baseLayout: String,
    subLayout: String,
    subDirection: String,
    subAlign: String,
    subAlignModifier: String,
    expandedTarget: ExpandedTarget,
    spacing: Dp = 6.dp,
    onBaseOffsetChanged: ((Int, Int) -> Unit)? = null,
    baseContent: @Composable () -> Unit,
    subContent: @Composable () -> Unit
) {
    val isExpanded = expandedTarget != ExpandedTarget.NONE

    // When joined, alignment is locked to full base but honors subAlignModifier.
    val effectiveSubAlign = if (expandedTarget == ExpandedTarget.JOINED_BOTH) "full base" else subAlign
    val effectiveSubAlignModifier = subAlignModifier

    Layout(
        content = {
            Box(modifier = Modifier.layoutId("base")) {
                baseContent()
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(160, easing = FastOutSlowInEasing)) + scaleIn(initialScale = 0.92f, animationSpec = tween(160, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(120, easing = FastOutLinearInEasing)) + scaleOut(targetScale = 0.92f, animationSpec = tween(120, easing = FastOutLinearInEasing)),
                modifier = Modifier.layoutId("sub")
            ) {
                subContent()
            }
        },
        modifier = modifier
    ) { measurables, constraints ->
        // Loose constraints so child composables wrap their intrinsic content cleanly
        val childConstraints = Constraints(
            minWidth = 0,
            maxWidth = constraints.maxWidth,
            minHeight = 0,
            maxHeight = constraints.maxHeight
        )

        val baseMeasurable = measurables.firstOrNull { it.layoutId == "base" }
        val subMeasurable = measurables.firstOrNull { it.layoutId == "sub" }

        val basePlaceable = baseMeasurable?.measure(childConstraints)
        val subPlaceable = subMeasurable?.measure(childConstraints)

        val baseW = basePlaceable?.width ?: 0
        val baseH = basePlaceable?.height ?: 0
        val subW = if (isExpanded) (subPlaceable?.width ?: 0) else 0
        val subH = if (isExpanded) (subPlaceable?.height ?: 0) else 0

        val hasSub = isExpanded && subW > 0 && subH > 0
        val gapPx = if (hasSub) spacing.roundToPx() else 0

        var totalW = baseW
        var totalH = baseH
        var baseX = 0
        var baseY = 0
        var subX = 0
        var subY = 0

        if (!hasSub) {
            totalW = baseW
            totalH = baseH
            baseX = 0
            baseY = 0
        } else {
            when (subDirection) {
                "down", "up" -> {
                    // Vertical expansion -> Cross axis is Horizontal (X)
                    val rawSubX = if (effectiveSubAlign == "base icon") {
                        if (baseLayout == "horiz") {
                            val (iconStart, iconCenter, iconEnd) = when (expandedTarget) {
                                ExpandedTarget.INPUT_ONLY -> Triple(0, (baseW * 0.25f).toInt(), (baseW * 0.5f).toInt())
                                ExpandedTarget.CONTEXT_ONLY -> Triple((baseW * 0.5f).toInt(), (baseW * 0.75f).toInt(), baseW)
                                else -> Triple(0, (baseW * 0.5f).toInt(), baseW)
                            }
                            when (effectiveSubAlignModifier) {
                                "start", "top", "left" -> iconStart
                                "end", "bottom", "right" -> iconEnd - subW
                                else -> iconCenter - (subW / 2)
                            }
                        } else {
                            // Vertical base bar -> both icons share same X center & edges
                            when (effectiveSubAlignModifier) {
                                "start", "top", "left" -> 0
                                "end", "bottom", "right" -> baseW - subW
                                else -> (baseW - subW) / 2
                            }
                        }
                    } else {
                        // Full Base
                        when (effectiveSubAlignModifier) {
                            "start", "top", "left" -> 0
                            "end", "bottom", "right" -> baseW - subW
                            else -> (baseW - subW) / 2
                        }
                    }

                    val minX = minOf(0, rawSubX)
                    val maxX = maxOf(baseW, rawSubX + subW)
                    totalW = maxX - minX
                    baseX = -minX
                    subX = rawSubX - minX

                    if (subDirection == "down") {
                        totalH = baseH + gapPx + subH
                        baseY = 0
                        subY = baseH + gapPx
                    } else {
                        totalH = subH + gapPx + baseH
                        subY = 0
                        baseY = subH + gapPx
                    }
                }
                else -> { // "left", "right"
                    // Horizontal expansion -> Cross axis is Vertical (Y)
                    val rawSubY = if (effectiveSubAlign == "base icon") {
                        if (baseLayout == "vert") {
                            val (iconStart, iconCenter, iconEnd) = when (expandedTarget) {
                                ExpandedTarget.INPUT_ONLY -> Triple(0, (baseH * 0.25f).toInt(), (baseH * 0.5f).toInt())
                                ExpandedTarget.CONTEXT_ONLY -> Triple((baseH * 0.5f).toInt(), (baseH * 0.75f).toInt(), baseH)
                                else -> Triple(0, (baseH * 0.5f).toInt(), baseH)
                            }
                            when (effectiveSubAlignModifier) {
                                "start", "top", "up" -> iconStart
                                "end", "bottom", "down" -> iconEnd - subH
                                else -> iconCenter - (subH / 2)
                            }
                        } else {
                            // Horizontal base bar -> both icons share same Y center & edges
                            when (effectiveSubAlignModifier) {
                                "start", "top", "up" -> 0
                                "end", "bottom", "down" -> baseH - subH
                                else -> (baseH - subH) / 2
                            }
                        }
                    } else {
                        // Full Base
                        when (effectiveSubAlignModifier) {
                            "start", "top", "up" -> 0
                            "end", "bottom", "down" -> baseH - subH
                            else -> (baseH - subH) / 2
                        }
                    }

                    val minY = minOf(0, rawSubY)
                    val maxY = maxOf(baseH, rawSubY + subH)
                    totalH = maxY - minY
                    baseY = -minY
                    subY = rawSubY - minY

                    if (subDirection == "right") {
                        totalW = baseW + gapPx + subW
                        baseX = 0
                        subX = baseW + gapPx
                    } else {
                        totalW = subW + gapPx + baseW
                        subX = 0
                        baseX = subW + gapPx
                    }
                }
            }
        }

        layout(
            totalW.coerceIn(constraints.minWidth, constraints.maxWidth),
            totalH.coerceIn(constraints.minHeight, constraints.maxHeight)
        ) {
            onBaseOffsetChanged?.invoke(baseX, baseY)
            basePlaceable?.placeRelative(baseX, baseY)
            if (isExpanded) {
                subPlaceable?.placeRelative(subX, subY)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolbarLayoutEngine(
    baseLayout: String,
    subLayout: String,
    subDirection: String,
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
    activeSelection: Rect?,
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
    onBaseOffsetChanged: (Int, Int) -> Unit = { _, _ -> }
) {
    val isBaseHoriz = baseLayout == "horiz"
    val isFlow = subLayout == "flow axis"
    val isSubHoriz = if (isFlow) isBaseHoriz else !isBaseHoriz

    val subContent: @Composable () -> Unit = {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.containerColor,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        ) {
            when (expandedTarget) {
                ExpandedTarget.INPUT_ONLY -> {
                    if (isSubHoriz) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            InputSubGroupContent(true, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor, onInputSubSelected)
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            InputSubGroupContent(false, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor, onInputSubSelected)
                        }
                    }
                }
                ExpandedTarget.CONTEXT_ONLY -> {
                    if (isSubHoriz) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            ContextSubGroupContent(true, screenGrab, onScreenGrabChange, appTree, onAppTreeChange, highlightedText, onHighlightedTextChange, media, onMediaChange, pointActive, activeSelection, onTogglePointMode, onSelectAllContext, onSelectNoneContext, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            ContextSubGroupContent(false, screenGrab, onScreenGrabChange, appTree, onAppTreeChange, highlightedText, onHighlightedTextChange, media, onMediaChange, pointActive, activeSelection, onTogglePointMode, onSelectAllContext, onSelectNoneContext, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                        }
                    }
                }
                ExpandedTarget.JOINED_BOTH -> {
                    if (isSubHoriz) {
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Min)
                                .padding(horizontal = 4.dp, vertical = 3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            ContextSubGroupContent(true, screenGrab, onScreenGrabChange, appTree, onAppTreeChange, highlightedText, onHighlightedTextChange, media, onMediaChange, pointActive, activeSelection, onTogglePointMode, onSelectAllContext, onSelectNoneContext, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            InputSubGroupContent(true, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor, onInputSubSelected)
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .height(IntrinsicSize.Min)
                                .padding(horizontal = 3.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            ContextSubGroupContent(false, screenGrab, onScreenGrabChange, appTree, onAppTreeChange, highlightedText, onHighlightedTextChange, media, onMediaChange, pointActive, activeSelection, onTogglePointMode, onSelectAllContext, onSelectNoneContext, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                            VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            InputSubGroupContent(false, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor, onInputSubSelected)
                        }
                    }
                }
                ExpandedTarget.NONE -> {}
            }
        }
    }

    val baseContent: @Composable () -> Unit = {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colors.containerColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            val (lastIcon, lastDesc) = when (lastUsedInput) {
                "mic_audio" -> Pair(Icons.Default.Mic, "Record")
                "clipboard_paste" -> Pair(Icons.Default.ContentPaste, "Paste")
                "text_snippet" -> Pair(Icons.Default.Keyboard, "Type")
                else -> Pair(Icons.Default.RecordVoiceOver, "Dictate")
            }

            if (isBaseHoriz) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
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

                    if (!isSingleIcon) {
                        VerticalDivider(
                            modifier = Modifier
                                .height(dividerSize)
                                .padding(horizontal = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        ToolbarIconButton(
                            icon = Icons.Default.Menu,
                            contentDescription = "Context",
                            buttonSize = buttonSize,
                            iconSize = iconSizeDp,
                            isActive = expandedTarget == ExpandedTarget.CONTEXT_ONLY || expandedTarget == ExpandedTarget.JOINED_BOTH,
                            hasBadge = hasActiveContext && expandedTarget == ExpandedTarget.NONE,
                            activeContainerColor = colors.activeContainerColor,
                            activeContentColor = colors.activeContentColor,
                            contentColor = colors.contentColor,
                            onClick = onContextClick,
                            onLongClick = onContextLongClick
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp),
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

                    if (!isSingleIcon) {
                        HorizontalDivider(
                            modifier = Modifier
                                .width(dividerSize)
                                .padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        ToolbarIconButton(
                            icon = Icons.Default.Menu,
                            contentDescription = "Context",
                            buttonSize = buttonSize,
                            iconSize = iconSizeDp,
                            isActive = expandedTarget == ExpandedTarget.CONTEXT_ONLY || expandedTarget == ExpandedTarget.JOINED_BOTH,
                            hasBadge = hasActiveContext && expandedTarget == ExpandedTarget.NONE,
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

    ToolbarMatrixLayout(
        baseLayout = baseLayout,
        subLayout = subLayout,
        subDirection = subDirection,
        subAlign = subAlign,
        subAlignModifier = subAlignModifier,
        expandedTarget = expandedTarget,
        spacing = 6.dp,
        onBaseOffsetChanged = onBaseOffsetChanged,
        baseContent = baseContent,
        subContent = subContent
    )
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
        if (isHoriz) {
            VerticalDivider(
                modifier = Modifier.height(menuButtonSize * 0.75f).padding(horizontal = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        } else {
            HorizontalDivider(
                modifier = Modifier.width(menuButtonSize * 0.75f).padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
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
    hasBadge: Boolean = false,
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
            if (hasBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(activeContentColor)
                )
            }
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
