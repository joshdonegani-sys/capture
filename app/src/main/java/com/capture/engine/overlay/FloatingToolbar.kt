package com.capture.engine.overlay

import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingToolbar(
    isExpanded: StateFlow<Boolean>,
    isPointActive: StateFlow<Boolean>,
    hasActiveSelection: StateFlow<Rect?>,
    onExpandToggle: () -> Unit,
    onCollapse: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onInputSelected: (String) -> Unit,
    onSelectAllContext: () -> Unit,
    onSelectNoneContext: () -> Unit,
    onTogglePointMode: () -> Unit
) {
    val expanded by isExpanded.collectAsState()
    val pointActive by isPointActive.collectAsState()
    val activeSelection by hasActiveSelection.collectAsState()
    val settings = remember { CaptureApplication.instance.settingsRepository }

    // Dynamic customization settings
    val iconSize by settings.toolbarIconSizeFlow.collectAsState()
    val alpha by settings.toolbarAlphaFlow.collectAsState()
    val displayMode by settings.inputSelectionDisplayModeFlow.collectAsState()
    val lastUsedInput by settings.lastUsedInputTypeFlow.collectAsState()
    val colorRole by settings.toolbarColorRoleFlow.collectAsState()
    val customSeedColor by settings.toolbarCustomSeedColorFlow.collectAsState()
    val contextDisplayMode by settings.contextSourcesDisplayModeFlow.collectAsState()
    val visibleInputIcons by settings.visibleInputIconsFlow.collectAsState()
    val showDragHandle by settings.showDragHandleFlow.collectAsState()

    var screenGrab by remember { mutableStateOf(settings.isScreenGrabActive) }
    var appTree by remember { mutableStateOf(settings.isAppTreeActive) }
    var highlightedText by remember { mutableStateOf(settings.isHighlightedTextActive) }
    var media by remember { mutableStateOf(settings.isMediaActive) }

    var isMenuExpanded by remember { mutableStateOf(false) }
    var isLastUsedExpanded by remember { mutableStateOf(false) }

    // Auto-close sub-menus when collapsed
    LaunchedEffect(expanded) {
        if (!expanded) {
            isMenuExpanded = false
            isLastUsedExpanded = false
        }
    }

    // Dynamic dimension tokens
    val iconSizeDp = iconSize.dp
    val buttonSize = (iconSize + 16f).dp
    val menuButtonSize = (iconSize + 8f).dp.coerceIn(30.dp, 38.dp)
    val menuIconSizeDp = (iconSize - 3f).dp.coerceIn(17.dp, 22.dp)
    val dividerHeight = (iconSize + 6f).dp

    // Resolve M3 Dynamic / Role colors
    val isDark = isSystemInDarkTheme()
    val colors = resolveToolbarColors(
        colorRole = colorRole,
        customSeedColor = customSeedColor,
        alpha = alpha,
        isDark = isDark
    )

    if (!expanded) {
        // Collapsed State: Wrapped in Box with bleed padding so dropshadow never clips against Window bounds
        Box(
            modifier = Modifier.padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val drag = awaitTouchSlopOrCancellation(down.id) { change, overSlop ->
                                change.consume()
                                onDrag(overSlop.x, overSlop.y)
                            }
                            if (drag != null) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.changedToUp()) {
                                        break
                                    }
                                    val dragAmount = change.positionChange()
                                    change.consume()
                                    onDrag(dragAmount.x, dragAmount.y)
                                }
                            } else {
                                onExpandToggle()
                            }
                        }
                    },
                shape = CircleShape,
                color = colors.containerColor,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
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
    } else {
        // Expanded State: Wrapped in Box with bleed padding to eliminate shadow edge clipping
        Box(
            modifier = Modifier
                .padding(14.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                },
            contentAlignment = Alignment.TopStart
        ) {
            val hasActiveContext = screenGrab || appTree || highlightedText || media || pointActive || activeSelection != null

            when (contextDisplayMode) {
                "above" -> {
                    // Unified single card: Context Sources on row 1, Input Selection on row 2
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = colors.containerColor,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            ContextStrip(
                                screenGrab = screenGrab,
                                onScreenGrabChange = { screenGrab = it; settings.isScreenGrabActive = it },
                                appTree = appTree,
                                onAppTreeChange = { appTree = it; settings.isAppTreeActive = it },
                                highlightedText = highlightedText,
                                onHighlightedTextChange = { highlightedText = it; settings.isHighlightedTextActive = it },
                                media = media,
                                onMediaChange = { media = it; settings.isMediaActive = it },
                                pointActive = pointActive,
                                activeSelection = activeSelection,
                                onTogglePointMode = onTogglePointMode,
                                onSelectAllContext = {
                                    onSelectAllContext()
                                    screenGrab = true; appTree = true; highlightedText = true; media = true
                                },
                                onSelectNoneContext = {
                                    onSelectNoneContext()
                                    screenGrab = false; appTree = false; highlightedText = false; media = false
                                },
                                menuButtonSize = menuButtonSize,
                                menuIconSizeDp = menuIconSizeDp,
                                dividerHeight = dividerHeight * 0.8f,
                                activeContainerColor = colors.activeContainerColor,
                                activeContentColor = colors.activeContentColor,
                                contentColor = colors.contentColor
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                            InputSelectionRow(
                                displayMode = displayMode,
                                visibleInputIcons = visibleInputIcons,
                                isLastUsedExpanded = isLastUsedExpanded,
                                lastUsedInput = lastUsedInput,
                                showDragHandle = showDragHandle,
                                buttonSize = buttonSize,
                                iconSizeDp = iconSizeDp,
                                dividerHeight = dividerHeight,
                                contentColor = colors.contentColor,
                                activeContainerColor = colors.activeContainerColor,
                                activeContentColor = colors.activeContentColor,
                                onInputSelected = { type ->
                                    settings.lastUsedInputType = type
                                    isLastUsedExpanded = false
                                    onInputSelected(type)
                                },
                                onLastUsedExpandToggle = { isLastUsedExpanded = !isLastUsedExpanded },
                                onDrag = onDrag,
                                showMenuToggle = false,
                                isMenuExpanded = false,
                                hasActiveContext = hasActiveContext,
                                onMenuToggle = {}
                            )
                        }
                    }
                }
                "below" -> {
                    // Unified single card: Input Selection on row 1, Context Sources on row 2
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = colors.containerColor,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            InputSelectionRow(
                                displayMode = displayMode,
                                visibleInputIcons = visibleInputIcons,
                                isLastUsedExpanded = isLastUsedExpanded,
                                lastUsedInput = lastUsedInput,
                                showDragHandle = showDragHandle,
                                buttonSize = buttonSize,
                                iconSizeDp = iconSizeDp,
                                dividerHeight = dividerHeight,
                                contentColor = colors.contentColor,
                                activeContainerColor = colors.activeContainerColor,
                                activeContentColor = colors.activeContentColor,
                                onInputSelected = { type ->
                                    settings.lastUsedInputType = type
                                    isLastUsedExpanded = false
                                    onInputSelected(type)
                                },
                                onLastUsedExpandToggle = { isLastUsedExpanded = !isLastUsedExpanded },
                                onDrag = onDrag,
                                showMenuToggle = false,
                                isMenuExpanded = false,
                                hasActiveContext = hasActiveContext,
                                onMenuToggle = {}
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                            ContextStrip(
                                screenGrab = screenGrab,
                                onScreenGrabChange = { screenGrab = it; settings.isScreenGrabActive = it },
                                appTree = appTree,
                                onAppTreeChange = { appTree = it; settings.isAppTreeActive = it },
                                highlightedText = highlightedText,
                                onHighlightedTextChange = { highlightedText = it; settings.isHighlightedTextActive = it },
                                media = media,
                                onMediaChange = { media = it; settings.isMediaActive = it },
                                pointActive = pointActive,
                                activeSelection = activeSelection,
                                onTogglePointMode = onTogglePointMode,
                                onSelectAllContext = {
                                    onSelectAllContext()
                                    screenGrab = true; appTree = true; highlightedText = true; media = true
                                },
                                onSelectNoneContext = {
                                    onSelectNoneContext()
                                    screenGrab = false; appTree = false; highlightedText = false; media = false
                                },
                                menuButtonSize = menuButtonSize,
                                menuIconSizeDp = menuIconSizeDp,
                                dividerHeight = dividerHeight * 0.8f,
                                activeContainerColor = colors.activeContainerColor,
                                activeContentColor = colors.activeContentColor,
                                contentColor = colors.contentColor
                            )
                        }
                    }
                }
                else -> {
                    // Default "menu" mode: Main bar anchored at top-start, flyout menu drops down below without moving the main bar
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Main Floating Bar: Input Selection + Menu Toggle (FIRST, perfectly stationary)
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = colors.containerColor,
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            InputSelectionRow(
                                displayMode = displayMode,
                                visibleInputIcons = visibleInputIcons,
                                isLastUsedExpanded = isLastUsedExpanded,
                                lastUsedInput = lastUsedInput,
                                showDragHandle = showDragHandle,
                                buttonSize = buttonSize,
                                iconSizeDp = iconSizeDp,
                                dividerHeight = dividerHeight,
                                contentColor = colors.contentColor,
                                activeContainerColor = colors.activeContainerColor,
                                activeContentColor = colors.activeContentColor,
                                onInputSelected = { type ->
                                    settings.lastUsedInputType = type
                                    isLastUsedExpanded = false
                                    onInputSelected(type)
                                },
                                onLastUsedExpandToggle = { isLastUsedExpanded = !isLastUsedExpanded },
                                onDrag = onDrag,
                                showMenuToggle = true,
                                isMenuExpanded = isMenuExpanded,
                                hasActiveContext = hasActiveContext,
                                onMenuToggle = { isMenuExpanded = !isMenuExpanded }
                            )
                        }

                        // Flyout Menu: Context Controls & Sources (Drops down directly below main bar)
                        AnimatedVisibility(
                            visible = isMenuExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = colors.containerColor,
                                tonalElevation = 8.dp,
                                shadowElevation = 8.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ContextStrip(
                                        screenGrab = screenGrab,
                                        onScreenGrabChange = { screenGrab = it; settings.isScreenGrabActive = it },
                                        appTree = appTree,
                                        onAppTreeChange = { appTree = it; settings.isAppTreeActive = it },
                                        highlightedText = highlightedText,
                                        onHighlightedTextChange = { highlightedText = it; settings.isHighlightedTextActive = it },
                                        media = media,
                                        onMediaChange = { media = it; settings.isMediaActive = it },
                                        pointActive = pointActive,
                                        activeSelection = activeSelection,
                                        onTogglePointMode = onTogglePointMode,
                                        onSelectAllContext = {
                                            onSelectAllContext()
                                            screenGrab = true; appTree = true; highlightedText = true; media = true
                                        },
                                        onSelectNoneContext = {
                                            onSelectNoneContext()
                                            screenGrab = false; appTree = false; highlightedText = false; media = false
                                        },
                                        menuButtonSize = menuButtonSize,
                                        menuIconSizeDp = menuIconSizeDp,
                                        dividerHeight = dividerHeight,
                                        activeContainerColor = colors.activeContainerColor,
                                        activeContentColor = colors.activeContentColor,
                                        contentColor = colors.contentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    buttonSize: androidx.compose.ui.unit.Dp = 40.dp,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
    isActive: Boolean = false,
    hasBadge: Boolean = false,
    activeContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
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
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize)
            )
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier.size(buttonSize)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = contentColor,
                    modifier = Modifier.size(iconSize)
                )
                if (hasBadge) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(activeContentColor)
                    )
                }
            }
        }
    }
}

@Composable
fun ContextStrip(
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
    menuButtonSize: androidx.compose.ui.unit.Dp,
    menuIconSizeDp: androidx.compose.ui.unit.Dp,
    dividerHeight: androidx.compose.ui.unit.Dp,
    activeContainerColor: Color,
    activeContentColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Group 1: Context Controls (All, None, Point)
        ToolbarIconButton(
            icon = Icons.Default.MobileFriendly,
            contentDescription = "Select All Context Sources",
            buttonSize = menuButtonSize,
            iconSize = menuIconSizeDp,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
            contentColor = contentColor,
            onClick = onSelectAllContext
        )
        ToolbarIconButton(
            icon = Icons.Default.Block,
            contentDescription = "Deselect All Context Sources",
            buttonSize = menuButtonSize,
            iconSize = menuIconSizeDp,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
            contentColor = contentColor,
            onClick = onSelectNoneContext
        )
        val isPointSelected = pointActive || activeSelection != null
        ToolbarIconButton(
            icon = Icons.Default.AdsClick,
            contentDescription = "Point Selection Mode",
            buttonSize = menuButtonSize,
            iconSize = menuIconSizeDp,
            isActive = isPointSelected,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
            contentColor = contentColor,
            onClick = onTogglePointMode
        )

        // Subtle Vertical Divider separating adjacent groups
        VerticalDivider(
            modifier = Modifier
                .height(dividerHeight)
                .padding(horizontal = 2.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // Group 2: Context Sources (Screen, App, Text, Media)
        ToolbarIconButton(
            icon = Icons.Default.Screenshot,
            contentDescription = "Screenshot Source",
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
            contentDescription = "App UI Tree Source",
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
            contentDescription = "Highlighted Text Source",
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
            contentDescription = "Media Source",
            buttonSize = menuButtonSize,
            iconSize = menuIconSizeDp,
            isActive = media,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
            contentColor = contentColor,
            onClick = { onMediaChange(!media) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InputSelectionRow(
    displayMode: String,
    visibleInputIcons: Set<String>,
    isLastUsedExpanded: Boolean,
    lastUsedInput: String,
    showDragHandle: Boolean,
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSizeDp: androidx.compose.ui.unit.Dp,
    dividerHeight: androidx.compose.ui.unit.Dp,
    contentColor: Color,
    activeContainerColor: Color,
    activeContentColor: Color,
    onInputSelected: (String) -> Unit,
    onLastUsedExpandToggle: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    showMenuToggle: Boolean,
    isMenuExpanded: Boolean,
    hasActiveContext: Boolean,
    onMenuToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(
            start = if (showDragHandle) 4.dp else 8.dp,
            end = if (showMenuToggle) 4.dp else 8.dp,
            top = 4.dp,
            bottom = 4.dp
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Optional Leading Drag Handle
        if (showDragHandle) {
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(dividerHeight)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .width(3.dp)
                        .height(14.dp),
                    shape = CircleShape,
                    color = contentColor.copy(alpha = 0.5f)
                ) {}
            }
        }

        // Input Selection items based on Display Mode
        if (displayMode == "all" || isLastUsedExpanded) {
            if (visibleInputIcons.contains("speech_to_text")) {
                ToolbarIconButton(
                    icon = Icons.Default.RecordVoiceOver,
                    contentDescription = "Dictate",
                    buttonSize = buttonSize,
                    iconSize = iconSizeDp,
                    contentColor = contentColor,
                    activeContainerColor = activeContainerColor,
                    activeContentColor = activeContentColor,
                    onClick = { onInputSelected("speech_to_text") }
                )
            }
            if (visibleInputIcons.contains("mic_audio")) {
                ToolbarIconButton(
                    icon = Icons.Default.Mic,
                    contentDescription = "Record",
                    buttonSize = buttonSize,
                    iconSize = iconSizeDp,
                    contentColor = contentColor,
                    activeContainerColor = activeContainerColor,
                    activeContentColor = activeContentColor,
                    onClick = { onInputSelected("mic_audio") }
                )
            }
            if (visibleInputIcons.contains("clipboard_paste")) {
                ToolbarIconButton(
                    icon = Icons.Default.ContentPaste,
                    contentDescription = "Paste",
                    buttonSize = buttonSize,
                    iconSize = iconSizeDp,
                    contentColor = contentColor,
                    activeContainerColor = activeContainerColor,
                    activeContentColor = activeContentColor,
                    onClick = { onInputSelected("clipboard_paste") }
                )
            }
            if (visibleInputIcons.contains("text_snippet")) {
                ToolbarIconButton(
                    icon = Icons.Default.Keyboard,
                    contentDescription = "Type",
                    buttonSize = buttonSize,
                    iconSize = iconSizeDp,
                    contentColor = contentColor,
                    activeContainerColor = activeContainerColor,
                    activeContentColor = activeContentColor,
                    onClick = { onInputSelected("text_snippet") }
                )
            }
        } else {
            // "Last used" mode: Single icon with tap-to-trigger and long-press to expand
            val (lastIcon, lastDesc, lastType) = when (lastUsedInput) {
                "mic_audio" -> Triple(Icons.Default.Mic, "Record", "mic_audio")
                "clipboard_paste" -> Triple(Icons.Default.ContentPaste, "Paste", "clipboard_paste")
                "text_snippet" -> Triple(Icons.Default.Keyboard, "Type", "text_snippet")
                else -> Triple(Icons.Default.RecordVoiceOver, "Dictate", "speech_to_text")
            }

            Surface(
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier
                    .size(buttonSize)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = { onInputSelected(lastType) },
                        onLongClick = onLastUsedExpandToggle
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = lastIcon,
                        contentDescription = "$lastDesc (Last Used - Long press to expand)",
                        tint = contentColor,
                        modifier = Modifier.size(iconSizeDp)
                    )
                }
            }
        }

        // Show divider and Menu Toggle only when showMenuToggle is true (Menu mode)
        if (showMenuToggle) {
            VerticalDivider(
                modifier = Modifier
                    .height(dividerHeight)
                    .padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            ToolbarIconButton(
                icon = Icons.Default.Menu,
                contentDescription = "Context Sources Menu",
                buttonSize = buttonSize,
                iconSize = iconSizeDp,
                isActive = isMenuExpanded,
                hasBadge = hasActiveContext && !isMenuExpanded,
                activeContainerColor = activeContainerColor,
                activeContentColor = activeContentColor,
                contentColor = contentColor,
                onClick = onMenuToggle
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
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(customSeedColor, hsv)
            val hue = hsv[0]
            val sat = (hsv[1] * 0.4f).coerceIn(0.12f, 0.45f)
            val (valBg, valContent) = if (isDark) Pair(0.24f, 0.92f) else Pair(0.90f, 0.12f)
            val container = Color.hsv(hue, sat, valBg)
            val onContainer = Color.hsv(hue, (sat * 0.5f).coerceAtLeast(0.05f), valContent)
            val active = Color(customSeedColor)
            val onActive = if (valBg > 0.5f) Color.White else Color.Black
            Quadruple(container, onContainer, active, onActive)
        }
        else -> Quadruple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
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
