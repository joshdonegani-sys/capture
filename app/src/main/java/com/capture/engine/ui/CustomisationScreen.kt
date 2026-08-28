package com.capture.engine.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.AlignHorizontalLeft
import androidx.compose.material.icons.filled.AlignHorizontalRight
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MobileFriendly
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capture.engine.CaptureApplication
import com.capture.engine.overlay.ExpandedTarget
import com.capture.engine.overlay.ToolbarLayoutEngine
import com.capture.engine.overlay.resolveToolbarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomisationScreen(
    modifier: Modifier = Modifier
) {
    val settings = remember { CaptureApplication.instance.settingsRepository }

    // Core Settings Flow
    val iconSize by settings.toolbarIconSizeFlow.collectAsState()
    val alpha by settings.toolbarAlphaFlow.collectAsState()
    val colorRole by settings.toolbarColorRoleFlow.collectAsState()
    val customSeedColor by settings.toolbarCustomSeedColorFlow.collectAsState()
    val visibleInputIcons by settings.visibleInputIconsFlow.collectAsState()
    val lastUsedInput by settings.lastUsedInputTypeFlow.collectAsState()

    // Toolbar Layout Settings
    val baseLayout by settings.toolbarBaseLayoutFlow.collectAsState()
    val subLayout by settings.toolbarSubLayoutFlow.collectAsState()
    val subDirection by settings.toolbarSubDirectionFlow.collectAsState()
    val subConnection by settings.toolbarSubConnectionFlow.collectAsState()
    val subAlign by settings.toolbarSubAlignFlow.collectAsState()
    val subAlignModifier by settings.toolbarSubAlignModifierFlow.collectAsState()

    // Dormant & Awake Configs
    val dormantStyle by settings.toolbarDormantStyleFlow.collectAsState()
    val dormantLocked by settings.toolbarDormantLockedFlow.collectAsState()
    val dormantPositionMode by settings.toolbarDormantPositionModeFlow.collectAsState()
    val awakeStyle by settings.toolbarAwakeStyleFlow.collectAsState()
    val awakeLocked by settings.toolbarAwakeLockedFlow.collectAsState()
    val awakePosition by settings.toolbarAwakePositionFlow.collectAsState()
    val allIconsOrder by settings.toolbarAllIconsOrderFlow.collectAsState()
    val wakeGesture by settings.toolbarWakeGestureFlow.collectAsState()
    val sleepGesture by settings.toolbarSleepGestureFlow.collectAsState()

    var showColorDialog by remember { mutableStateOf(false) }
    var previewExpandedTarget by remember { mutableStateOf(ExpandedTarget.JOINED_BOTH) }

    val isDark = isSystemInDarkTheme()
    val previewColors = resolveToolbarColors(
        colorRole = colorRole,
        customSeedColor = customSeedColor,
        alpha = alpha,
        isDark = isDark
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Toolbar Layout & State Engine",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // --- 1. LIVE PREVIEW CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE INTERACTIVE PREVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            previewExpandedTarget = when (previewExpandedTarget) {
                                ExpandedTarget.NONE -> ExpandedTarget.JOINED_BOTH
                                ExpandedTarget.JOINED_BOTH -> ExpandedTarget.INPUT_ONLY
                                ExpandedTarget.INPUT_ONLY -> ExpandedTarget.CONTEXT_ONLY
                                ExpandedTarget.CONTEXT_ONLY -> ExpandedTarget.NONE
                            }
                        }
                    ) {
                        Text(
                            text = when (previewExpandedTarget) {
                                ExpandedTarget.NONE -> "Collapsed"
                                ExpandedTarget.JOINED_BOTH -> "Joined Expanded"
                                ExpandedTarget.INPUT_ONLY -> "Input Expanded"
                                ExpandedTarget.CONTEXT_ONLY -> "Context Expanded"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                val buttonSize = (iconSize + 14f).dp
                val iconSizeDp = (iconSize - 2f).dp.coerceAtLeast(16.dp)
                val menuButtonSize = (iconSize + 6f).dp.coerceIn(28.dp, 36.dp)
                val menuIconSizeDp = (iconSize - 4f).dp.coerceIn(16.dp, 20.dp)
                val dividerSize = (iconSize + 4f).dp

                Box(
                    modifier = Modifier.padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ToolbarLayoutEngine(
                        baseLayout = baseLayout,
                        subLayout = subLayout,
                        subDirection = subDirection,
                        subAlign = subAlign,
                        subAlignModifier = subAlignModifier,
                        expandedTarget = if (awakeStyle == "all icons") ExpandedTarget.JOINED_BOTH else previewExpandedTarget,
                        isSingleIcon = awakeStyle == "single icon",
                        colors = previewColors,
                        buttonSize = buttonSize,
                        iconSizeDp = iconSizeDp,
                        menuButtonSize = menuButtonSize,
                        menuIconSizeDp = menuIconSizeDp,
                        dividerSize = dividerSize,
                        lastUsedInput = lastUsedInput,
                        visibleInputIcons = visibleInputIcons,
                        hasActiveContext = true,
                        screenGrab = true,
                        appTree = false,
                        highlightedText = true,
                        media = false,
                        pointActive = false,
                        activeSelection = null,
                        onInputClick = {},
                        onInputLongClick = {},
                        onContextClick = {},
                        onContextLongClick = {},
                        onInputSubSelected = {},
                        onScreenGrabChange = {},
                        onAppTreeChange = {},
                        onHighlightedTextChange = {},
                        onMediaChange = {},
                        onTogglePointMode = {},
                        onSelectAllContext = {},
                        onSelectNoneContext = {}
                    )
                }
            }
        }

        // --- 2. AWAKE CONFIGURATION CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Awake State Configuration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                // Awake Style (Single Icon, Double Icon, All Icons)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Awake Style", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = awakeStyle == "single icon",
                            onClick = {
                                settings.toolbarAwakeStyle = "single icon"
                                settings.toolbarSubConnection = "joined"
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                        ) {
                            Text("Single Icon")
                        }
                        SegmentedButton(
                            selected = awakeStyle == "double icon",
                            onClick = { settings.toolbarAwakeStyle = "double icon" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                        ) {
                            Text("Double Icon")
                        }
                        SegmentedButton(
                            selected = awakeStyle == "all icons",
                            onClick = { settings.toolbarAwakeStyle = "all icons" },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                        ) {
                            Text("All Icons")
                        }
                    }
                    Text(
                        text = when (awakeStyle) {
                            "single icon" -> "Single Icon: Shows only the last used input type. Expands both sub-groups simultaneously."
                            "all icons" -> "All Icons: Base nodes skipped. Subgroups rendered directly as a single cluster."
                            else -> "Double Icon: Renders both Input Type and Context Menu base nodes."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Awake Position Mode
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Awake Position Behavior", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = awakePosition == "free",
                            onClick = { settings.toolbarAwakePosition = "free" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                        ) {
                            Text("Free")
                        }
                        SegmentedButton(
                            selected = awakePosition == "dockable",
                            onClick = { settings.toolbarAwakePosition = "dockable" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                        ) {
                            Text("Dockable")
                        }
                        SegmentedButton(
                            selected = awakePosition == "always docked",
                            onClick = { settings.toolbarAwakePosition = "always docked" },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                        ) {
                            Text("Always Docked")
                        }
                    }
                    Text(
                        text = when (awakePosition) {
                            "free" -> "Free: Absolute X,Y placement anywhere on screen."
                            "dockable" -> "Dockable: Snaps cleanly to viewport edges when dragged near boundaries."
                            else -> "Always Docked: Restricted strictly to 1D track along the viewport edge."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Awake Locked switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lock Awake Position", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Disables drag gestures when awake.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = awakeLocked, onCheckedChange = { settings.toolbarAwakeLocked = it })
                }

                // If All Icons, show Order Setting
                if (awakeStyle == "all icons") {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("All Icons Cluster Order", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        val orderOptions = if (baseLayout == "horiz") listOf("inner", "outer", "left", "right") else listOf("inner", "outer", "up", "down")
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            orderOptions.forEachIndexed { idx, opt ->
                                SegmentedButton(
                                    selected = allIconsOrder == opt,
                                    onClick = { settings.toolbarAllIconsOrder = opt },
                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = orderOptions.size)
                                ) {
                                    Text(opt.replaceFirstChar { it.uppercase() })
                                }
                            }
                        }
                        Text(
                            text = "Inner/Outer dynamically swaps based on distance to the screen center axis.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // --- 3. BASE & SUB LAYOUT ENGINE CARD (Active when not All Icons) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Dashboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Base & Sub Layout Rules", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                // Base Layout (Horizontal / Vertical)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Base Container Axis", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = baseLayout == "horiz",
                            onClick = { settings.toolbarBaseLayout = "horiz" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("Horizontal (Row)")
                        }
                        SegmentedButton(
                            selected = baseLayout == "vert",
                            onClick = { settings.toolbarBaseLayout = "vert" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("Vertical (Column)")
                        }
                    }
                }

                if (awakeStyle != "all icons") {
                    // Sub Layout (Flow Axis vs Cross Axis)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Sub Expansion Axis", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = subLayout == "flow axis",
                                onClick = { settings.toolbarSubLayout = "flow axis" },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("Flow Axis (Parallel)")
                            }
                            SegmentedButton(
                                selected = subLayout == "cross axis",
                                onClick = { settings.toolbarSubLayout = "cross axis" },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("Cross Axis (Perpendicular)")
                            }
                        }
                    }

                    // Sub Direction (Down, Up, Left, Right)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Sub Physical Direction", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf("down", "up", "left", "right").forEachIndexed { idx, dir ->
                                SegmentedButton(
                                    selected = subDirection == dir,
                                    onClick = { settings.toolbarSubDirection = dir },
                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = 4)
                                ) {
                                    Text(dir.replaceFirstChar { it.uppercase() })
                                }
                            }
                        }
                    }

                    // Sub Connection (Separate vs Joined)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Sub Expansion Connection", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = subConnection == "separate" && awakeStyle != "single icon",
                                onClick = { settings.toolbarSubConnection = "separate" },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                enabled = awakeStyle != "single icon"
                            ) {
                                Text("Separate")
                            }
                            SegmentedButton(
                                selected = subConnection == "joined" || awakeStyle == "single icon",
                                onClick = { settings.toolbarSubConnection = "joined" },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                enabled = awakeStyle != "single icon"
                            ) {
                                Text("Joined")
                            }
                        }
                        if (awakeStyle == "single icon") {
                            Text("Single Icon awake style automatically forces Joined connection.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Sub Align (Flow Axis only)
                    if (subLayout == "flow axis") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Sub Alignment (Flow Axis)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = subAlign == "full base" || subConnection == "joined" || awakeStyle == "single icon",
                                    onClick = { settings.toolbarSubAlign = "full base" },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                    enabled = subConnection != "joined" && awakeStyle != "single icon"
                                ) {
                                    Text("Full Base")
                                }
                                SegmentedButton(
                                    selected = subAlign == "base icon" && subConnection != "joined" && awakeStyle != "single icon",
                                    onClick = { settings.toolbarSubAlign = "base icon" },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    enabled = subConnection != "joined" && awakeStyle != "single icon"
                                ) {
                                    Text("Base Icon")
                                }
                            }

                            // Sub Align Modifiers (Top/Middle/Bottom or Start/Center/End)
                            if (subAlign == "full base" || subConnection == "joined" || awakeStyle == "single icon") {
                                val modLabels = if (baseLayout == "horiz") listOf("start", "middle", "end") else listOf("top", "middle", "bottom")
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                    listOf("top", "middle", "bottom").forEachIndexed { idx, mod ->
                                        SegmentedButton(
                                            selected = subAlignModifier == mod,
                                            onClick = { settings.toolbarSubAlignModifier = mod },
                                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = 3)
                                        ) {
                                            Text(modLabels[idx].replaceFirstChar { it.uppercase() })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4. DORMANT STATE & GESTURES CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Nightlight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Dormant State & Gestures", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                // Dormant Style (Small Icon / Horiz Bar / Vert Bar)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Dormant Indicator Style", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = dormantStyle == "horizontal bar",
                            onClick = { settings.toolbarDormantStyle = "horizontal bar" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                        ) {
                            Text("Horiz Bar")
                        }
                        SegmentedButton(
                            selected = dormantStyle == "vertical bar",
                            onClick = { settings.toolbarDormantStyle = "vertical bar" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                        ) {
                            Text("Vert Bar")
                        }
                        SegmentedButton(
                            selected = dormantStyle == "small icon",
                            onClick = { settings.toolbarDormantStyle = "small icon" },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                        ) {
                            Text("Small Icon")
                        }
                    }
                }

                // Dormant Position Mode (Off / Defined)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Dormant Position Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = dormantPositionMode == "off",
                            onClick = { settings.toolbarDormantPositionMode = "off" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("Inherit Awake")
                        }
                        SegmentedButton(
                            selected = dormantPositionMode == "defined",
                            onClick = { settings.toolbarDormantPositionMode = "defined" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("Defined Spot")
                        }
                    }
                }

                // Wake Gesture
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Wake Gesture", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = wakeGesture == "single tap",
                            onClick = { settings.toolbarWakeGesture = "single tap" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                        ) {
                            Text("Single Tap")
                        }
                        SegmentedButton(
                            selected = wakeGesture == "double tap",
                            onClick = { settings.toolbarWakeGesture = "double tap" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                        ) {
                            Text("Double Tap")
                        }
                        SegmentedButton(
                            selected = wakeGesture == "long tap",
                            onClick = { settings.toolbarWakeGesture = "long tap" },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                        ) {
                            Text("Long Tap")
                        }
                    }
                }

                // Sleep Gesture
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Sleep Gesture", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = sleepGesture == "double tap",
                            onClick = { settings.toolbarSleepGesture = "double tap" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                        ) {
                            Text("Double Tap")
                        }
                        SegmentedButton(
                            selected = sleepGesture == "flick",
                            onClick = { settings.toolbarSleepGesture = "flick" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                        ) {
                            Text("Flick")
                        }
                        SegmentedButton(
                            selected = sleepGesture == "drag to edge",
                            onClick = { settings.toolbarSleepGesture = "drag to edge" },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                        ) {
                            Text("Drag Edge")
                        }
                    }
                }

                // Dormant Locked switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lock Dormant Indicator", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Disables drag gestures when dormant.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = dormantLocked, onCheckedChange = { settings.toolbarDormantLocked = it })
                }
            }
        }

        // --- 5. APPEARANCE & COLOUR CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Appearance & Styling", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                // Size Slider
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Icon Size", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("${iconSize.toInt()} dp", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = iconSize,
                        onValueChange = { settings.toolbarIconSize = it },
                        valueRange = 18f..36f,
                        steps = 8
                    )
                }

                // Transparency Slider
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Transparency", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("${(alpha * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = alpha,
                        onValueChange = { settings.toolbarAlpha = it },
                        valueRange = 0.20f..1.0f,
                        steps = 15
                    )
                }

                // Colour Swatches
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Material 3 Role Presets & Tonal Seed", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorSwatch("Default", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant, colorRole == "surface_container") { settings.toolbarColorRole = "surface_container" }
                        ColorSwatch("Primary", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, colorRole == "primary_container") { settings.toolbarColorRole = "primary_container" }
                        ColorSwatch("Secondary", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, colorRole == "secondary_container") { settings.toolbarColorRole = "secondary_container" }
                        ColorSwatch("Tertiary", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, colorRole == "tertiary_container") { settings.toolbarColorRole = "tertiary_container" }
                        ColorSwatch("Variant", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, colorRole == "surface_variant") { settings.toolbarColorRole = "surface_variant" }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(customSeedColor))
                                .border(if (colorRole == "custom") 2.5.dp else 1.dp, if (colorRole == "custom") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable {
                                    settings.toolbarColorRole = "custom"
                                    showColorDialog = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (colorRole == "custom") Icons.Default.Check else Icons.Default.Palette, null, Modifier.size(20.dp), tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showColorDialog) {
        CustomColorPickerDialog(
            initialColor = customSeedColor,
            onDismiss = { showColorDialog = false },
            onColorSelected = {
                settings.toolbarCustomSeedColor = it
                settings.toolbarColorRole = "custom"
                showColorDialog = false
            }
        )
    }
}

@Composable
private fun ColorSwatch(label: String, containerColor: Color, contentColor: Color, isSelected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(containerColor)
                .border(if (isSelected) 2.5.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = contentColor)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun CustomColorPickerDialog(initialColor: Int, onDismiss: () -> Unit, onColorSelected: (Int) -> Unit) {
    val presetSeeds = listOf(
        0xFF6750A4.toInt(), 0xFF3F51B5.toInt(), 0xFF007AFF.toInt(), 0xFF009688.toInt(),
        0xFF2E7D32.toInt(), 0xFFFF9800.toInt(), 0xFFFF5722.toInt(), 0xFFE91E63.toInt()
    )
    var selectedColor by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Seed Colour", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Pick a seed colour to generate dynamic tonal container and icon colours.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    presetSeeds.take(4).forEach { colorInt ->
                        PresetColorDot(colorInt, selectedColor == colorInt) { selectedColor = colorInt }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    presetSeeds.drop(4).forEach { colorInt ->
                        PresetColorDot(colorInt, selectedColor == colorInt) { selectedColor = colorInt }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onColorSelected(selectedColor) }) { Text("Select", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PresetColorDot(colorInt: Int, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color(colorInt))
            .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
