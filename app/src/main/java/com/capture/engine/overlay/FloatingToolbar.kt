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
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha),
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Flyout Menu: Icon-only adjacent groups (Context Controls first, then Context Sources)
                AnimatedVisibility(
                    visible = isMenuExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha),
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Group 1: Context Controls (All, None, Point)
                            ToolbarIconButton(
                                icon = Icons.Default.MobileFriendly,
                                contentDescription = "Select All Context Sources",
                                buttonSize = menuButtonSize,
                                iconSize = menuIconSizeDp,
                                onClick = {
                                    onSelectAllContext()
                                    screenGrab = true
                                    appTree = true
                                    highlightedText = true
                                    media = true
                                }
                            )
                            ToolbarIconButton(
                                icon = Icons.Default.Block,
                                contentDescription = "Deselect All Context Sources",
                                buttonSize = menuButtonSize,
                                iconSize = menuIconSizeDp,
                                onClick = {
                                    onSelectNoneContext()
                                    screenGrab = false
                                    appTree = false
                                    highlightedText = false
                                    media = false
                                }
                            )
                            val isPointSelected = pointActive || activeSelection != null
                            ToolbarIconButton(
                                icon = Icons.Default.AdsClick,
                                contentDescription = "Point Selection Mode",
                                buttonSize = menuButtonSize,
                                iconSize = menuIconSizeDp,
                                isActive = isPointSelected,
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
                                onClick = {
                                    screenGrab = !screenGrab
                                    settings.isScreenGrabActive = screenGrab
                                }
                            )
                            ToolbarIconButton(
                                icon = Icons.Default.AppShortcut,
                                contentDescription = "App UI Tree Source",
                                buttonSize = menuButtonSize,
                                iconSize = menuIconSizeDp,
                                isActive = appTree,
                                onClick = {
                                    appTree = !appTree
                                    settings.isAppTreeActive = appTree
                                }
                            )
                            ToolbarIconButton(
                                icon = Icons.AutoMirrored.Filled.ShortText,
                                contentDescription = "Highlighted Text Source",
                                buttonSize = menuButtonSize,
                                iconSize = menuIconSizeDp,
                                isActive = highlightedText,
                                onClick = {
                                    highlightedText = !highlightedText
                                    settings.isHighlightedTextActive = highlightedText
                                }
                            )
                            ToolbarIconButton(
                                icon = Icons.Default.PlayCircle,
                                contentDescription = "Media Source",
                                buttonSize = menuButtonSize,
                                iconSize = menuIconSizeDp,
                                isActive = media,
                                onClick = {
                                    media = !media
                                    settings.isMediaActive = media
                                }
                            )
                        }
                    }
                }

                // Main Floating Bar: Input Selection + Menu Toggle
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Leading Drag Handle
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            ) {}
                        }
                        // Input Selection items based on Display Mode
                        if (displayMode == "all" || isLastUsedExpanded) {
                            ToolbarIconButton(
                                icon = Icons.Default.RecordVoiceOver,
                                contentDescription = "Dictate",
                                buttonSize = buttonSize,
                                iconSize = iconSizeDp,
                                onClick = {
                                    settings.lastUsedInputType = "speech_to_text"
                                    isLastUsedExpanded = false
                                    onInputSelected("speech_to_text")
                                }
                            )
                            ToolbarIconButton(
                                icon = Icons.Default.Mic,
                                contentDescription = "Record",
                                buttonSize = buttonSize,
                                iconSize = iconSizeDp,
                                onClick = {
                                    settings.lastUsedInputType = "mic_audio"
                                    isLastUsedExpanded = false
                                    onInputSelected("mic_audio")
                                }
                            )
                            ToolbarIconButton(
                                icon = Icons.Default.ContentPaste,
                                contentDescription = "Paste",
                                buttonSize = buttonSize,
                                iconSize = iconSizeDp,
                                onClick = {
                                    settings.lastUsedInputType = "clipboard_paste"
                                    isLastUsedExpanded = false
                                    onInputSelected("clipboard_paste")
                                }
                            )
                            ToolbarIconButton(
                                icon = Icons.Default.Keyboard,
                                contentDescription = "Type",
                                buttonSize = buttonSize,
                                iconSize = iconSizeDp,
                                onClick = {
                                    settings.lastUsedInputType = "text_snippet"
                                    isLastUsedExpanded = false
                                    onInputSelected("text_snippet")
                                }
                            )
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
                                        onLongClick = { isLastUsedExpanded = true }
                                    )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = lastIcon,
                                        contentDescription = "$lastDesc (Long-press to expand)",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(iconSizeDp)
                                    )
                                }
                            }
                        }

                        VerticalDivider(
                            modifier = Modifier
                                .height(dividerHeight)
                                .padding(horizontal = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        // Hamburger Menu Toggle
                        val hasActiveContext = screenGrab || appTree || highlightedText || media || pointActive || activeSelection != null
                        ToolbarIconButton(
                            icon = Icons.Default.Menu,
                            contentDescription = "Context Sources Menu",
                            buttonSize = buttonSize,
                            iconSize = iconSizeDp,
                            onClick = { isMenuExpanded = !isMenuExpanded },
                            isActive = isMenuExpanded,
                            hasBadge = hasActiveContext && !isMenuExpanded
                        )
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
    buttonSize: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    isActive: Boolean = false,
    hasBadge: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (isActive) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier.size(buttonSize),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(iconSize)
                )
                if (hasBadge) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}
