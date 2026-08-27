package com.capture.engine.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.capture.engine.CaptureApplication
import com.capture.engine.model.CaptureEntity
import com.capture.engine.model.DeliveryStatus
import com.capture.engine.network.WebhookClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val app = CaptureApplication.instance
    val dao = app.database.captureDao()
    val settings = app.settingsRepository
    val scope = rememberCoroutineScope()

    val captures by dao.getAllCapturesFlow().collectAsState(initial = emptyList())

    // Filters
    var selectedTypeFilter by remember { mutableStateOf<String?>(null) }
    var filterScreenGrabOnly by remember { mutableStateOf(false) }
    var filterAppTreeOnly by remember { mutableStateOf(false) }

    // Dialog state for viewing full JSON
    var viewingJsonPayload by remember { mutableStateOf<String?>(null) }

    val filteredCaptures = captures.filter { capture ->
        (selectedTypeFilter == null || capture.type == selectedTypeFilter) &&
                (!filterScreenGrabOnly || capture.hasScreenGrab) &&
                (!filterAppTreeOnly || capture.hasAppTree)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedTypeFilter == null && !filterScreenGrabOnly && !filterAppTreeOnly,
                onClick = {
                    selectedTypeFilter = null
                    filterScreenGrabOnly = false
                    filterAppTreeOnly = false
                },
                label = { Text("All") }
            )
            FilterChip(
                selected = selectedTypeFilter == "speech_to_text",
                onClick = { selectedTypeFilter = if (selectedTypeFilter == "speech_to_text") null else "speech_to_text" },
                label = { Text("Voice") }
            )
            FilterChip(
                selected = selectedTypeFilter == "mic_audio",
                onClick = { selectedTypeFilter = if (selectedTypeFilter == "mic_audio") null else "mic_audio" },
                label = { Text("Audio") }
            )
            FilterChip(
                selected = selectedTypeFilter == "text_snippet",
                onClick = { selectedTypeFilter = if (selectedTypeFilter == "text_snippet") null else "text_snippet" },
                label = { Text("Text") }
            )
            FilterChip(
                selected = selectedTypeFilter == "clipboard_paste",
                onClick = { selectedTypeFilter = if (selectedTypeFilter == "clipboard_paste") null else "clipboard_paste" },
                label = { Text("Paste") }
            )
            FilterChip(
                selected = filterScreenGrabOnly,
                onClick = { filterScreenGrabOnly = !filterScreenGrabOnly },
                label = { Text("With Screenshot") }
            )
            FilterChip(
                selected = filterAppTreeOnly,
                onClick = { filterAppTreeOnly = !filterAppTreeOnly },
                label = { Text("With App Tree") }
            )
        }

        if (filteredCaptures.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No captures recorded yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredCaptures, key = { it.id }) { item ->
                    CaptureItemCard(
                        item = item,
                        onViewJson = { viewingJsonPayload = item.rawJson },
                        onRetry = {
                            scope.launch(Dispatchers.IO) {
                                val client = WebhookClient()
                                dao.update(item.copy(status = DeliveryStatus.SENDING))
                                val result = client.sendPayload(
                                    url = settings.webhookUrl,
                                    jsonPayload = item.rawJson,
                                    apiKey = settings.apiKey.ifBlank { null }
                                )
                                dao.update(
                                    item.copy(
                                        status = if (result.isSuccess) DeliveryStatus.SUCCESS else DeliveryStatus.ERROR,
                                        responseCode = result.statusCode,
                                        responseTimeMs = result.responseTimeMs,
                                        errorMessage = result.errorMessage,
                                        retryCount = item.retryCount + 1
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }

        // View JSON Dialog
        if (viewingJsonPayload != null) {
            AlertDialog(
                onDismissRequest = { viewingJsonPayload = null },
                title = { Text("Payload JSON") },
                text = {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        LazyColumn(modifier = Modifier.padding(12.dp)) {
                            item {
                                Text(
                                    text = viewingJsonPayload ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewingJsonPayload = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
fun CaptureItemCard(
    item: CaptureEntity,
    onViewJson: () -> Unit,
    onRetry: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss · dd MMM", Locale.getDefault()) }
    val formattedDate = remember(item.timestamp) { dateFormat.format(Date(item.timestamp)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewJson() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Status badge, Timestamp, Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when (item.status) {
                        DeliveryStatus.SUCCESS -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Sent (${item.responseCode ?: 200})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        DeliveryStatus.ERROR -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Error (${item.responseCode ?: "Failed"})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        DeliveryStatus.SENDING -> {
                            Icon(
                                imageVector = Icons.Default.HourglassEmpty,
                                contentDescription = "Sending",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Sending...",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    if (item.responseTimeMs != null) {
                        Text(
                            text = "· ${item.responseTimeMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body: Input Preview
            Text(
                text = item.inputPreview ?: "[No Input Text]",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!item.packageName.isNullOrBlank()) {
                Text(
                    text = "App: ${item.packageName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Context Included Chips and Retry Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.hasScreenGrab) {
                        Icon(
                            imageVector = Icons.Default.Screenshot,
                            contentDescription = "Has Screenshot",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (item.hasAppTree) {
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = "Has App Tree",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (item.hasHighlightedText) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = "Has Highlighted Text",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (item.hasMedia) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Has Media",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.status == DeliveryStatus.ERROR) {
                        OutlinedButton(
                            onClick = onRetry,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    IconButton(onClick = onViewJson) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "View JSON",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
