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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

@Composable
fun PointSelectionOverlay(
    onSelectionComplete: (Rect) -> Unit,
    onCancel: () -> Unit
) {
    var startOffset by remember { mutableStateOf<Offset?>(null) }
    var currentOffset by remember { mutableStateOf<Offset?>(null) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val fillColor = primaryColor.copy(alpha = 0.18f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
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
    ) {
        // Draw selection bounding box
        Canvas(modifier = Modifier.fillMaxSize()) {
            val start = startOffset
            val end = currentOffset
            if (start != null && end != null) {
                val left = min(start.x, end.x)
                val top = min(start.y, end.y)
                val width = max(start.x, end.x) - left
                val height = max(start.y, end.y) - top

                // Semi-transparent fill
                drawRect(
                    color = fillColor,
                    topLeft = Offset(left, top),
                    size = Size(width, height)
                )

                // Pure M3 Primary stroke
                drawRect(
                    color = primaryColor,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        // Cancel Button at top right
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
