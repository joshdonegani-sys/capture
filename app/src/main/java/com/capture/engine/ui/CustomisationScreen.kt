package com.capture.engine.ui

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
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MobileFriendly
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewStream
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
import com.capture.engine.overlay.resolveToolbarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomisationScreen(
    modifier: Modifier = Modifier
) {
    val settings = remember { CaptureApplication.instance.settingsRepository }
    val iconSize by settings.toolbarIconSizeFlow.collectAsState()
    val alpha by settings.toolbarAlphaFlow.collectAsState()
    val displayMode by settings.inputSelectionDisplayModeFlow.collectAsState()
    val lastUsedInput by settings.lastUsedInputTypeFlow.collectAsState()
    val colorRole by settings.toolbarColorRoleFlow.collectAsState()
    val customSeedColor by settings.toolbarCustomSeedColorFlow.collectAsState()
    val contextDisplayMode by settings.contextSourcesDisplayModeFlow.collectAsState()
    val visibleInputIcons by settings.visibleInputIconsFlow.collectAsState()
    val showDragHandle by settings.showDragHandleFlow.collectAsState()

    var showColorDialog by remember { mutableStateOf(false) }
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
        // Section Header
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
                text = "Toolbar Customisation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Live Preview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
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
                Text(
                    text = "LIVE PREVIEW",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val buttonSize = (iconSize + 14f).dp
                val iconSizeDp = (iconSize - 2f).dp.coerceAtLeast(16.dp)

                // Simulated Floating Toolbar
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = previewColors.containerColor,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // Simulated Context Strip if "Above"
                        if (contextDisplayMode == "above") {
                            SimulatedContextStrip(iconSizeDp, previewColors.contentColor)
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        }

                        // Input Selection Row
                        Row(
                            modifier = Modifier.padding(
                                start = if (showDragHandle) 4.dp else 8.dp,
                                end = if (contextDisplayMode == "menu") 4.dp else 8.dp,
                                top = 2.dp,
                                bottom = 2.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (showDragHandle) {
                                Surface(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(14.dp),
                                    shape = CircleShape,
                                    color = previewColors.contentColor.copy(alpha = 0.5f)
                                ) {}
                            }

                            if (displayMode == "all") {
                                if (visibleInputIcons.contains("speech_to_text")) {
                                    Icon(
                                        imageVector = Icons.Default.RecordVoiceOver,
                                        contentDescription = null,
                                        modifier = Modifier.size(buttonSize).padding(6.dp),
                                        tint = previewColors.contentColor
                                    )
                                }
                                if (visibleInputIcons.contains("mic_audio")) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        modifier = Modifier.size(buttonSize).padding(6.dp),
                                        tint = previewColors.contentColor
                                    )
                                }
                                if (visibleInputIcons.contains("clipboard_paste")) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = null,
                                        modifier = Modifier.size(buttonSize).padding(6.dp),
                                        tint = previewColors.contentColor
                                    )
                                }
                                if (visibleInputIcons.contains("text_snippet")) {
                                    Icon(
                                        imageVector = Icons.Default.Keyboard,
                                        contentDescription = null,
                                        modifier = Modifier.size(buttonSize).padding(6.dp),
                                        tint = previewColors.contentColor
                                    )
                                }
                            } else {
                                val previewIcon = when (lastUsedInput) {
                                    "mic_audio" -> Icons.Default.Mic
                                    "clipboard_paste" -> Icons.Default.ContentPaste
                                    "text_snippet" -> Icons.Default.Keyboard
                                    else -> Icons.Default.RecordVoiceOver
                                }
                                Icon(
                                    imageVector = previewIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(buttonSize).padding(6.dp),
                                    tint = previewColors.contentColor
                                )
                            }

                            if (contextDisplayMode == "menu") {
                                VerticalDivider(
                                    modifier = Modifier
                                        .height((iconSize + 4f).dp)
                                        .padding(horizontal = 3.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )

                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = null,
                                    modifier = Modifier.size(buttonSize).padding(6.dp),
                                    tint = previewColors.contentColor
                                )
                            }
                        }

                        // Simulated Context Strip if "Below"
                        if (contextDisplayMode == "below") {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                            SimulatedContextStrip(iconSizeDp, previewColors.contentColor)
                        }
                    }
                }
            }
        }

        // Toolbar Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Toolbar Settings",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 1. Size Slider
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoSizeSelectLarge,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Size",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "${iconSize.toInt()} dp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Controls icon size and overall floating toolbar dimensions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = iconSize,
                        onValueChange = { settings.toolbarIconSize = it },
                        valueRange = 18f..36f,
                        steps = 8
                    )
                }

                // 2. Transparency Slider
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Opacity,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Transparency",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "${(alpha * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Adjust background opacity of the floating toolbar surface.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = alpha,
                        onValueChange = { settings.toolbarAlpha = it },
                        valueRange = 0.20f..1.0f,
                        steps = 15
                    )
                }

                // 3. Colour Swatches (5 M3 Roles + 1 Custom Seed Swatch)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ColorLens,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Colour",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "Material 3 container role presets and dynamic tonal seed generator.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Swatch 1: Surface Container High (Default)
                        ColorSwatch(
                            label = "Default",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            isSelected = colorRole == "surface_container",
                            onClick = { settings.toolbarColorRole = "surface_container" }
                        )

                        // Swatch 2: Primary Container
                        ColorSwatch(
                            label = "Primary",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            isSelected = colorRole == "primary_container",
                            onClick = { settings.toolbarColorRole = "primary_container" }
                        )

                        // Swatch 3: Secondary Container
                        ColorSwatch(
                            label = "Secondary",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            isSelected = colorRole == "secondary_container",
                            onClick = { settings.toolbarColorRole = "secondary_container" }
                        )

                        // Swatch 4: Tertiary Container
                        ColorSwatch(
                            label = "Tertiary",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            isSelected = colorRole == "tertiary_container",
                            onClick = { settings.toolbarColorRole = "tertiary_container" }
                        )

                        // Swatch 5: Surface Variant
                        ColorSwatch(
                            label = "Variant",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            isSelected = colorRole == "surface_variant",
                            onClick = { settings.toolbarColorRole = "surface_variant" }
                        )

                        // Swatch 6: Custom Seed Swatch
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(customSeedColor))
                                .border(
                                    width = if (colorRole == "custom") 2.5.dp else 1.dp,
                                    color = if (colorRole == "custom") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                                .clickable {
                                    settings.toolbarColorRole = "custom"
                                    showColorDialog = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (colorRole == "custom") Icons.Default.Check else Icons.Default.Palette,
                                contentDescription = "Custom Seed Colour",
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                        }
                    }
                }

                // 4. Context Sources Display Mode (Menu, Above, Below)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewStream,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Context Sources Display",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = contextDisplayMode == "menu",
                            onClick = { settings.contextSourcesDisplayMode = "menu" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                        ) {
                            Text("Menu")
                        }
                        SegmentedButton(
                            selected = contextDisplayMode == "above",
                            onClick = { settings.contextSourcesDisplayMode = "above" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                        ) {
                            Text("Above")
                        }
                        SegmentedButton(
                            selected = contextDisplayMode == "below",
                            onClick = { settings.contextSourcesDisplayMode = "below" },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                        ) {
                            Text("Below")
                        }
                    }

                    Text(
                        text = when (contextDisplayMode) {
                            "above" -> "Above: Context sources displayed directly above input selection icons in a unified card."
                            "below" -> "Below: Context sources displayed directly below input selection icons in a unified card."
                            else -> "Menu: Context sources accessible via the flyout hamburger menu icon."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 5. Input Selection Display Mode
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Input Selection Display",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = displayMode == "all",
                            onClick = { settings.inputSelectionDisplayMode = "all" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("All Icons")
                        }
                        SegmentedButton(
                            selected = displayMode == "last_used",
                            onClick = { settings.inputSelectionDisplayMode = "last_used" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("Last Used")
                        }
                    }

                    Text(
                        text = if (displayMode == "all") {
                            "All: Toolbar displays selected Input Selection items."
                        } else {
                            "Last used: Toolbar shows a single icon of the latest input used. Tap to trigger action; long-press to expand all icons."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 6. Visible Input Icons Selection (Only when displayMode == "all")
                    if (displayMode == "all") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Visible Input Icons",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Choose which input items appear on the toolbar (at least 1 must remain active).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InputIconToggle(
                                icon = Icons.Default.RecordVoiceOver,
                                contentDescription = "Dictate",
                                isSelected = visibleInputIcons.contains("speech_to_text"),
                                onClick = { settings.toggleInputIconVisibility("speech_to_text") }
                            )
                            InputIconToggle(
                                icon = Icons.Default.Mic,
                                contentDescription = "Record",
                                isSelected = visibleInputIcons.contains("mic_audio"),
                                onClick = { settings.toggleInputIconVisibility("mic_audio") }
                            )
                            InputIconToggle(
                                icon = Icons.Default.ContentPaste,
                                contentDescription = "Paste",
                                isSelected = visibleInputIcons.contains("clipboard_paste"),
                                onClick = { settings.toggleInputIconVisibility("clipboard_paste") }
                            )
                            InputIconToggle(
                                icon = Icons.Default.Keyboard,
                                contentDescription = "Type",
                                isSelected = visibleInputIcons.contains("text_snippet"),
                                onClick = { settings.toggleInputIconVisibility("text_snippet") }
                            )
                        }
                    }
                }

                // 7. Display Drag Handle Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragIndicator,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Display Drag Handle",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Show visual grip indicator on toolbar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = showDragHandle,
                        onCheckedChange = { settings.showDragHandle = it }
                    )
                }
            }
        }
    }

    // Custom Seed Color Picker Dialog
    if (showColorDialog) {
        CustomColorPickerDialog(
            initialColor = customSeedColor,
            onDismiss = { showColorDialog = false },
            onColorSelected = { selected ->
                settings.toolbarCustomSeedColor = selected
                settings.toolbarColorRole = "custom"
                showColorDialog = false
            }
        )
    }
}

@Composable
private fun ColorSwatch(
    label: String,
    containerColor: Color,
    contentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(containerColor)
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "$label Selected",
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun InputIconToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    if (isSelected) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(42.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
        }
    } else {
        OutlinedIconButton(
            onClick = onClick,
            modifier = Modifier.size(42.dp),
            colors = IconButtonDefaults.outlinedIconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SimulatedContextStrip(iconSizeDp: androidx.compose.ui.unit.Dp, contentColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(Icons.Default.MobileFriendly, null, Modifier.size(iconSizeDp), tint = contentColor)
        Icon(Icons.Default.Block, null, Modifier.size(iconSizeDp), tint = contentColor)
        Icon(Icons.Default.AdsClick, null, Modifier.size(iconSizeDp), tint = contentColor)
        VerticalDivider(
            modifier = Modifier
                .height(iconSizeDp * 0.8f)
                .padding(horizontal = 2.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Icon(Icons.Default.Screenshot, null, Modifier.size(iconSizeDp), tint = contentColor)
        Icon(Icons.Default.AppShortcut, null, Modifier.size(iconSizeDp), tint = contentColor)
        Icon(Icons.AutoMirrored.Filled.ShortText, null, Modifier.size(iconSizeDp), tint = contentColor)
        Icon(Icons.Default.PlayCircle, null, Modifier.size(iconSizeDp), tint = contentColor)
    }
}

@Composable
private fun CustomColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit
) {
    val presetSeeds = listOf(
        0xFF6750A4.toInt(), // Deep Purple
        0xFF3F51B5.toInt(), // Indigo
        0xFF007AFF.toInt(), // Vibrant Blue
        0xFF009688.toInt(), // Teal
        0xFF2E7D32.toInt(), // Forest Green
        0xFFFF9800.toInt(), // Amber
        0xFFFF5722.toInt(), // Coral
        0xFFE91E63.toInt()  // Rose
    )
    var selectedColor by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select Seed Colour", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Pick a vibrant seed colour to generate custom Material 3 container and icon tones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Preset Swatches Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    presetSeeds.take(4).forEach { colorInt ->
                        PresetColorDot(
                            colorInt = colorInt,
                            isSelected = selectedColor == colorInt,
                            onClick = { selectedColor = colorInt }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    presetSeeds.drop(4).forEach { colorInt ->
                        PresetColorDot(
                            colorInt = colorInt,
                            isSelected = selectedColor == colorInt,
                            onClick = { selectedColor = colorInt }
                        )
                    }
                }

                // Live Preview of Generated Tones
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(selectedColor).copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, Color(selectedColor)),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(selectedColor))
                        )
                        Text(
                            "Generated M3 Tonal Palette Preview",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(selectedColor) }) {
                Text("Select", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PresetColorDot(
    colorInt: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color(colorInt))
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
