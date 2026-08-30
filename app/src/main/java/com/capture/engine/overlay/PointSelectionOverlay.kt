package com.capture.engine.overlay

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

@Composable
fun PointSelectionOverlay(
    activeRect: Rect? = null,
    onSelectionComplete: (Rect) -> Unit,
    onCancel: () -> Unit
) {
    var startOffset by remember { mutableStateOf<Offset?>(null) }
    var currentOffset by remember { mutableStateOf<Offset?>(null) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val scrimColor = Color.Black.copy(alpha = 0.55f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (activeRect == null) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                startOffset = offset
                                currentOffset = offset
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentOffset = change.position
                            },
                            onDragEnd = {
                                val start = startOffset
                                val end = currentOffset
                                if (start != null && end != null) {
                                    val left = min(start.x, end.x).toInt()
                                    val top = min(start.y, end.y).toInt()
                                    val right = max(start.x, end.x).toInt()
                                    val bottom = max(start.y, end.y).toInt()

                                    // Minimum 10px selection
                                    if (right - left > 10 && bottom - top > 10) {
                                        onSelectionComplete(Rect(left, top, right, bottom))
                                    } else {
                                        onCancel()
                                    }
                                } else {
                                    onCancel()
                                }
                            },
                            onDragCancel = {
                                onCancel()
                            }
                        )
                    }
                } else Modifier
            )
    ) {
        // Draw inverted selection bounding box: outside is shaded, inside is clear
        Canvas(modifier = Modifier.fillMaxSize()) {
            val currentRect: androidx.compose.ui.geometry.Rect? = when {
                startOffset != null && currentOffset != null -> {
                    val s = startOffset!!
                    val e = currentOffset!!
                    val l = min(s.x, e.x)
                    val t = min(s.y, e.y)
                    val r = max(s.x, e.x)
                    val b = max(s.y, e.y)
                    if (r - l > 2 && b - t > 2) androidx.compose.ui.geometry.Rect(l, t, r, b) else null
                }
                activeRect != null -> {
                    androidx.compose.ui.geometry.Rect(
                        activeRect.left.toFloat(),
                        activeRect.top.toFloat(),
                        activeRect.right.toFloat(),
                        activeRect.bottom.toFloat()
                    )
                }
                else -> null
            }

            if (currentRect != null) {
                val path = Path().apply {
                    addRect(currentRect)
                }
                // Punch out the selection box: clip to outside of path
                clipPath(path = path, clipOp = ClipOp.Difference) {
                    drawRect(color = scrimColor)
                }

                // Primary border around selection cutout
                drawRect(
                    color = primaryColor,
                    topLeft = Offset(currentRect.left, currentRect.top),
                    size = Size(currentRect.width, currentRect.height),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        // Cancel Button at top right (only shown during selection drawing before active selection completes)
        if (activeRect == null) {
            FilledIconButton(
                onClick = onCancel,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel Selection"
                )
            }
        }
    }
}
