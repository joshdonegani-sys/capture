package com.capture.engine.overlay

import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
    onTogglePointMode: () -> Unit
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
    val subAlignModifier by settings.toolbarSubAlignModifierFlow.collectAsState() // "top", "middle", "bottom"

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

    // Dynamic dimension tokens
    val iconSizeDp = iconSize.dp
    val buttonSize = (iconSize + 16f).dp
    val menuButtonSize = (iconSize + 8f).dp.coerceIn(30.dp, 38.dp)
    val menuIconSizeDp = (iconSize - 3f).dp.coerceIn(17.dp, 22.dp)
    val dividerSize = (iconSize + 6f).dp

    val isDark = isSystemInDarkTheme()
    val colors = resolveToolbarColors(
        colorRole = colorRole,
        customSeedColor = customSeedColor,
        alpha = alpha,
        isDark = isDark
    )

    val hasActiveContext = screenGrab || appTree || highlightedText || media || pointActive || activeSelection != null

    if (!expanded) {
        // --- DORMANT STATE ---
        Box(
            modifier = Modifier
                .padding(20.dp)
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
                .padding(20.dp)
                .pointerInput(awakeLocked, sleepGesture) {
                    if (!awakeLocked) {
                        detectDragGestures(
                            onDragEnd = {
                                if (sleepGesture == "flick") {
                                    val speed = abs(dragVelocityTrackerX) + abs(dragVelocityTrackerY)
                                    if (speed > 80f) {
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
                        shape = RoundedCornerShape(24.dp),
                        color = colors.containerColor,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        if (isHoriz) {
                            // Horizontal base: sub-groups are horizontal rows stacked in a Column so long edges are adjacent
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isInputFirst) {
                                    InputSubGroupContent(true, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor) { settings.lastUsedInputType = it; onInputSelected(it) }
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    ContextSubGroupContent(true, screenGrab, { screenGrab = it; settings.isScreenGrabActive = it }, appTree, { appTree = it; settings.isAppTreeActive = it }, highlightedText, { highlightedText = it; settings.isHighlightedTextActive = it }, media, { media = it; settings.isMediaActive = it }, pointActive, activeSelection, onTogglePointMode, { onSelectAllContext(); screenGrab = true; appTree = true; highlightedText = true; media = true }, { onSelectNoneContext(); screenGrab = false; appTree = false; highlightedText = false; media = false }, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                                } else {
                                    ContextSubGroupContent(true, screenGrab, { screenGrab = it; settings.isScreenGrabActive = it }, appTree, { appTree = it; settings.isAppTreeActive = it }, highlightedText, { highlightedText = it; settings.isHighlightedTextActive = it }, media, { media = it; settings.isMediaActive = it }, pointActive, activeSelection, onTogglePointMode, { onSelectAllContext(); screenGrab = true; appTree = true; highlightedText = true; media = true }, { onSelectNoneContext(); screenGrab = false; appTree = false; highlightedText = false; media = false }, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    InputSubGroupContent(true, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor) { settings.lastUsedInputType = it; onInputSelected(it) }
                                }
                            }
                        } else {
                            // Vertical base: sub-groups are vertical columns placed in a Row so long edges are adjacent
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isInputFirst) {
                                    InputSubGroupContent(false, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor) { settings.lastUsedInputType = it; onInputSelected(it) }
                                    VerticalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    ContextSubGroupContent(false, screenGrab, { screenGrab = it; settings.isScreenGrabActive = it }, appTree, { appTree = it; settings.isAppTreeActive = it }, highlightedText, { highlightedText = it; settings.isHighlightedTextActive = it }, media, { media = it; settings.isMediaActive = it }, pointActive, activeSelection, onTogglePointMode, { onSelectAllContext(); screenGrab = true; appTree = true; highlightedText = true; media = true }, { onSelectNoneContext(); screenGrab = false; appTree = false; highlightedText = false; media = false }, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                                } else {
                                    ContextSubGroupContent(false, screenGrab, { screenGrab = it; settings.isScreenGrabActive = it }, appTree, { appTree = it; settings.isAppTreeActive = it }, highlightedText, { highlightedText = it; settings.isHighlightedTextActive = it }, media, { media = it; settings.isMediaActive = it }, pointActive, activeSelection, onTogglePointMode, { onSelectAllContext(); screenGrab = true; appTree = true; highlightedText = true; media = true }, { onSelectNoneContext(); screenGrab = false; appTree = false; highlightedText = false; media = false }, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor)
                                    VerticalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    InputSubGroupContent(false, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor) { settings.lastUsedInputType = it; onInputSelected(it) }
                                }
                            }
                        }
                    }
                }
                else -> {
                    val isSingle = awakeStyle == "single icon"
                    val effectiveConnection = if (isSingle) "joined" else subConnection
                    val onInputLongTap = { expandedTarget = if (effectiveConnection == "joined") (if (expandedTarget == ExpandedTarget.JOINED_BOTH) ExpandedTarget.NONE else ExpandedTarget.JOINED_BOTH) else (if (expandedTarget == ExpandedTarget.INPUT_ONLY) ExpandedTarget.NONE else ExpandedTarget.INPUT_ONLY) }
                    val onContextLongTap = { expandedTarget = if (effectiveConnection == "joined") (if (expandedTarget == ExpandedTarget.JOINED_BOTH) ExpandedTarget.NONE else ExpandedTarget.JOINED_BOTH) else (if (expandedTarget == ExpandedTarget.CONTEXT_ONLY) ExpandedTarget.NONE else ExpandedTarget.CONTEXT_ONLY) }

                    ToolbarLayoutEngine(baseLayout, subLayout, subDirection, subAlign, subAlignModifier, expandedTarget, isSingle, colors, buttonSize, iconSizeDp, menuButtonSize, menuIconSizeDp, dividerSize, lastUsedInput, visibleInputIcons, hasActiveContext, screenGrab, appTree, highlightedText, media, pointActive, activeSelection, { onInputSelected(lastUsedInput) }, onInputLongTap, { onContextLongTap() }, onContextLongTap, { settings.lastUsedInputType = it; expandedTarget = ExpandedTarget.NONE; onInputSelected(it) }, { screenGrab = it; settings.isScreenGrabActive = it }, { appTree = it; settings.isAppTreeActive = it }, { highlightedText = it; settings.isHighlightedTextActive = it }, { media = it; settings.isMediaActive = it }, onTogglePointMode, { onSelectAllContext(); screenGrab = true; appTree = true; highlightedText = true; media = true }, { onSelectNoneContext(); screenGrab = false; appTree = false; highlightedText = false; media = false })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolbarLayoutEngine(baseLayout: String, subLayout: String, subDirection: String, subAlign: String, subAlignModifier: String, expandedTarget: ExpandedTarget, isSingleIcon: Boolean, colors: ToolbarColors, buttonSize: Dp, iconSizeDp: Dp, menuButtonSize: Dp, menuIconSizeDp: Dp, dividerSize: Dp, lastUsedInput: String, visibleInputIcons: Set<String>, hasActiveContext: Boolean, screenGrab: Boolean, appTree: Boolean, highlightedText: Boolean, media: Boolean, pointActive: Boolean, activeSelection: Rect?, onInputClick: () -> Unit, onInputLongClick: () -> Unit, onContextClick: () -> Unit, onContextLongClick: () -> Unit, onInputSubSelected: (String) -> Unit, onScreenGrabChange: (Boolean) -> Unit, onAppTreeChange: (Boolean) -> Unit, onHighlightedTextChange: (Boolean) -> Unit, onMediaChange: (Boolean) -> Unit, onTogglePointMode: () -> Unit, onSelectAllContext: () -> Unit, onSelectNoneContext: () -> Unit) {
    val isBaseHoriz = baseLayout == "horiz"
    val isFlow = subLayout == "flow axis"
    val isSubHoriz = if (isFlow) isBaseHoriz else !isBaseHoriz
    val subContent: @Composable () -> Unit = {
        Surface(shape = RoundedCornerShape(24.dp), color = colors.containerColor, tonalElevation = 8.dp, shadowElevation = 8.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))) {
            when (expandedTarget) {
                ExpandedTarget.INPUT_ONLY -> {
                    if (isSubHoriz) Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { InputSubGroupContent(true, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor, onInputSubSelected) }
                    else Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) { InputSubGroupContent(false, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor, onInputSubSelected) }
                }
                ExpandedTarget.CONTEXT_ONLY -> {
                    if (isSubHoriz) Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { ContextSubGroupContent(true, screenGrab, onScreenGrabChange, appTree, onAppTreeChange, highlightedText, onHighlightedTextChange, media, onMediaChange, pointActive, activeSelection, onTogglePointMode, onSelectAllContext, onSelectNoneContext, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor) }
                    else Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) { ContextSubGroupContent(false, screenGrab, onScreenGrabChange, appTree, onAppTreeChange, highlightedText, onHighlightedTextChange, media, onMediaChange, pointActive, activeSelection, onTogglePointMode, onSelectAllContext, onSelectNoneContext, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor) }
                }
                ExpandedTarget.JOINED_BOTH -> {
                    if (isSubHoriz) Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) { ContextSubGroupContent(true, screenGrab, onScreenGrabChange, appTree, onAppTreeChange, highlightedText, onHighlightedTextChange, media, onMediaChange, pointActive, activeSelection, onTogglePointMode, onSelectAllContext, onSelectNoneContext, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor); HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)); InputSubGroupContent(true, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor, onInputSubSelected) }
                    else Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { ContextSubGroupContent(false, screenGrab, onScreenGrabChange, appTree, onAppTreeChange, highlightedText, onHighlightedTextChange, media, onMediaChange, pointActive, activeSelection, onTogglePointMode, onSelectAllContext, onSelectNoneContext, menuButtonSize, menuIconSizeDp, colors.activeContainerColor, colors.activeContentColor, colors.contentColor); VerticalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)); InputSubGroupContent(false, visibleInputIcons, buttonSize, iconSizeDp, colors.contentColor, colors.activeContainerColor, colors.activeContentColor, onInputSubSelected) }
                }
                ExpandedTarget.NONE -> {}
            }
        }
    }
    val baseContent: @Composable () -> Unit = {
        Surface(shape = RoundedCornerShape(28.dp), color = colors.containerColor, tonalElevation = 6.dp, shadowElevation = 8.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))) {
            val (lastIcon, lastDesc) = when (lastUsedInput) { "mic_audio" -> Pair(Icons.Default.Mic, "Record"); "clipboard_paste" -> Pair(Icons.Default.ContentPaste, "Paste"); "text_snippet" -> Pair(Icons.Default.Keyboard, "Type"); else -> Pair(Icons.Default.RecordVoiceOver, "Dictate") }
            if (isBaseHoriz) {
                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(shape = CircleShape, color = if (expandedTarget == ExpandedTarget.INPUT_ONLY || expandedTarget == ExpandedTarget.JOINED_BOTH) colors.activeContainerColor else Color.Transparent, modifier = Modifier.size(buttonSize).clip(CircleShape).combinedClickable(onClick = onInputClick, onLongClick = onInputLongClick)) { Box(contentAlignment = Alignment.Center) { Icon(lastIcon, "$lastDesc (Last Used)", tint = if (expandedTarget == ExpandedTarget.INPUT_ONLY || expandedTarget == ExpandedTarget.JOINED_BOTH) colors.activeContentColor else colors.contentColor, modifier = Modifier.size(iconSizeDp)) } }
                    if (!isSingleIcon) {
                        VerticalDivider(modifier = Modifier.height(dividerSize).padding(horizontal = 2.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Surface(shape = CircleShape, color = if (expandedTarget == ExpandedTarget.CONTEXT_ONLY || expandedTarget == ExpandedTarget.JOINED_BOTH) colors.activeContainerColor else Color.Transparent, modifier = Modifier.size(buttonSize).clip(CircleShape).combinedClickable(onClick = onContextClick, onLongClick = onContextLongClick)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Menu, "Context", tint = if (expandedTarget == ExpandedTarget.CONTEXT_ONLY || expandedTarget == ExpandedTarget.JOINED_BOTH) colors.activeContentColor else colors.contentColor, modifier = Modifier.size(iconSizeDp)); if (hasActiveContext && expandedTarget == ExpandedTarget.NONE) Box(modifier = Modifier.align(Alignment.TopEnd).size(6.dp).clip(CircleShape).background(colors.activeContainerColor)) } }
                    }
                }
            } else {
                Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(shape = CircleShape, color = if (expandedTarget == ExpandedTarget.INPUT_ONLY || expandedTarget == ExpandedTarget.JOINED_BOTH) colors.activeContainerColor else Color.Transparent, modifier = Modifier.size(buttonSize).clip(CircleShape).combinedClickable(onClick = onInputClick, onLongClick = onInputLongClick)) { Box(contentAlignment = Alignment.Center) { Icon(lastIcon, "$lastDesc (Last Used)", tint = if (expandedTarget == ExpandedTarget.INPUT_ONLY || expandedTarget == ExpandedTarget.JOINED_BOTH) colors.activeContentColor else colors.contentColor, modifier = Modifier.size(iconSizeDp)) } }
                    if (!isSingleIcon) {
                        HorizontalDivider(modifier = Modifier.width(dividerSize).padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Surface(shape = CircleShape, color = if (expandedTarget == ExpandedTarget.CONTEXT_ONLY || expandedTarget == ExpandedTarget.JOINED_BOTH) colors.activeContainerColor else Color.Transparent, modifier = Modifier.size(buttonSize).clip(CircleShape).combinedClickable(onClick = onContextClick, onLongClick = onContextLongClick)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Menu, "Context", tint = if (expandedTarget == ExpandedTarget.CONTEXT_ONLY || expandedTarget == ExpandedTarget.JOINED_BOTH) colors.activeContentColor else colors.contentColor, modifier = Modifier.size(iconSizeDp)); if (hasActiveContext && expandedTarget == ExpandedTarget.NONE) Box(modifier = Modifier.align(Alignment.TopEnd).size(6.dp).clip(CircleShape).background(colors.activeContainerColor)) } }
                    }
                }
            }
        }
    }
    val isExpandedVisible = expandedTarget != ExpandedTarget.NONE
    val horizontalAlignment = when { !isFlow -> Alignment.Start; subAlign == "base icon" && expandedTarget == ExpandedTarget.INPUT_ONLY -> Alignment.Start; subAlign == "base icon" && expandedTarget == ExpandedTarget.CONTEXT_ONLY -> Alignment.End; subAlignModifier == "top" || subAlignModifier == "start" -> Alignment.Start; subAlignModifier == "bottom" || subAlignModifier == "end" -> Alignment.End; else -> Alignment.CenterHorizontally }
    val verticalAlignment = when { !isFlow -> Alignment.Top; subAlign == "base icon" && expandedTarget == ExpandedTarget.INPUT_ONLY -> Alignment.Top; subAlign == "base icon" && expandedTarget == ExpandedTarget.CONTEXT_ONLY -> Alignment.Bottom; subAlignModifier == "top" || subAlignModifier == "start" -> Alignment.Top; subAlignModifier == "bottom" || subAlignModifier == "end" -> Alignment.Bottom; else -> Alignment.CenterVertically }
    when (subDirection) {
        "down" -> Column(horizontalAlignment = horizontalAlignment, verticalArrangement = Arrangement.spacedBy(8.dp)) { baseContent(); AnimatedVisibility(visible = isExpandedVisible, enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) + scaleIn(initialScale = 0.92f), exit = fadeOut(tween(140, easing = FastOutLinearInEasing)) + scaleOut(targetScale = 0.92f)) { subContent() } }
        "up" -> Column(horizontalAlignment = horizontalAlignment, verticalArrangement = Arrangement.spacedBy(8.dp)) { AnimatedVisibility(visible = isExpandedVisible, enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) + scaleIn(initialScale = 0.92f), exit = fadeOut(tween(140, easing = FastOutLinearInEasing)) + scaleOut(targetScale = 0.92f)) { subContent() }; baseContent() }
        "left" -> Row(verticalAlignment = verticalAlignment, horizontalArrangement = Arrangement.spacedBy(8.dp)) { AnimatedVisibility(visible = isExpandedVisible, enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) + scaleIn(initialScale = 0.92f), exit = fadeOut(tween(140, easing = FastOutLinearInEasing)) + scaleOut(targetScale = 0.92f)) { subContent() }; baseContent() }
        else -> Row(verticalAlignment = verticalAlignment, horizontalArrangement = Arrangement.spacedBy(8.dp)) { baseContent(); AnimatedVisibility(visible = isExpandedVisible, enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) + scaleIn(initialScale = 0.92f), exit = fadeOut(tween(140, easing = FastOutLinearInEasing)) + scaleOut(targetScale = 0.92f)) { subContent() } }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { items() }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) { items() }
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
                modifier = Modifier.height(menuButtonSize * 0.8f).padding(horizontal = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        } else {
            HorizontalDivider(
                modifier = Modifier.width(menuButtonSize * 0.8f).padding(vertical = 2.dp),
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

@Composable
fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    buttonSize: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    isActive: Boolean = false,
    hasBadge: Boolean = false,
    activeContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (isActive) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier.size(buttonSize),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = activeContainerColor,
                contentColor = activeContentColor
            )
        ) {
            Icon(icon, contentDescription, modifier = Modifier.size(iconSize))
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier.size(buttonSize)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(icon, contentDescription, tint = contentColor, modifier = Modifier.size(iconSize))
                if (hasBadge) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(activeContentColor))
                }
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

