package com.capture.engine.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.capture.engine.CaptureApplication

@Composable
fun DormantPlacementContent(
    initialX: Int,
    initialY: Int,
    onConfirm: (x: Int, y: Int) -> Unit,
    onCancel: () -> Unit
) {
    val settings = CaptureApplication.instance.settingsRepository
    val dormantStyle by settings.toolbarDormantStyleFlow.collectAsState()
    val iconSize by settings.toolbarIconSizeFlow.collectAsState()
    val alpha by settings.toolbarAlphaFlow.collectAsState()
    val colorRole by settings.toolbarColorRoleFlow.collectAsState()
    val customSeedColor by settings.toolbarCustomSeedColorFlow.collectAsState()
    val tonalElevation by settings.toolbarTonalElevationFlow.collectAsState()
    val shadowElevation by settings.toolbarShadowElevationFlow.collectAsState()

    val isDark = isSystemInDarkTheme()
    val colors = resolveToolbarColors(
        colorRole = colorRole,
        customSeedColor = customSeedColor,
        alpha = alpha,
        isDark = isDark
    )

    val iconSizeDp = iconSize.dp
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
    ) {
        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        // Outer padding in FloatingToolbar is 12.dp on all sides
        val totalWidthPx = with(density) { (dormantW + 24.dp).toPx() }
        val totalHeightPx = with(density) { (dormantH + 24.dp).toPx() }

        val maxX = (screenWidthPx - totalWidthPx).toInt().coerceAtLeast(0)
        val maxY = (screenHeightPx - totalHeightPx).toInt().coerceAtLeast(0)

        var posX by remember { mutableStateOf(initialX.coerceIn(0, maxX)) }
        var posY by remember { mutableStateOf(initialY.coerceIn(0, maxY)) }

        // Tap background to reposition
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val targetX = (tapOffset.x - totalWidthPx / 2f).toInt().coerceIn(0, maxX)
                        val targetY = (tapOffset.y - totalHeightPx / 2f).toInt().coerceIn(0, maxY)
                        posX = targetX
                        posY = targetY
                    }
                }
        )

        // Centered Actions: Checkmark and Cross horizontally adjacent
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                        .shadow(4.dp, CircleShape)
                ) {
                    Text(
                        text = "Drag indicator to place",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cross (Cancel) icon
                    FilledIconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(6.dp, CircleShape),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel positioning",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Checkmark (Confirm) icon
                    FilledIconButton(
                        onClick = { onConfirm(posX, posY) },
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(6.dp, CircleShape),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm defined position",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        // Draggable Dormant Indicator
        Box(
            modifier = Modifier
                .offset { IntOffset(posX, posY) }
                .padding(12.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        posX = (posX + dragAmount.x.toInt()).coerceIn(0, maxX)
                        posY = (posY + dragAmount.y.toInt()).coerceIn(0, maxY)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val placementShape = RoundedCornerShape(dormantRadiusDp)
            Surface(
                shape = placementShape,
                color = colors.containerColor,
                tonalElevation = tonalElevation.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                modifier = Modifier
                    .size(dormantW, dormantH)
                    .cleanDropShadow(
                        shape = placementShape,
                        elevation = shadowElevation.dp
                    )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (dormantStyle) {
                        "small icon" -> {
                            Box(
                                modifier = Modifier.size((iconSize + 12f).dp.coerceAtLeast(36.dp)),
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
        }
    }
}
