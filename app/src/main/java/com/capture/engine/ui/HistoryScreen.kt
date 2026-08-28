package com.capture.engine.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.capture.engine.CaptureApplication
import com.capture.engine.model.CaptureEntity
import com.capture.engine.model.CapturePayload
import com.capture.engine.model.DeliveryStatus
import com.capture.engine.model.MediaContext
import com.capture.engine.network.WebhookClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class HistoryTab(val title: String, val icon: ImageVector) {
    data object Overview : HistoryTab("Overview", Icons.Default.Info)
    data object Screenshot : HistoryTab("Screenshot", Icons.Default.Screenshot)
    data object AppTree : HistoryTab("App Tree", Icons.Default.AccountTree)
    data object Media : HistoryTab("Media & Audio", Icons.Default.PlayCircle)
    data object RawJson : HistoryTab("Raw JSON", Icons.Default.Code)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = CaptureApplication.instance
    val dao = app.database.captureDao()
    val settings = app.settingsRepository
    val scope = rememberCoroutineScope()

    val captures by dao.getAllCapturesFlow().collectAsState(initial = emptyList())

    // Filters
    var selectedTypeFilter by remember { mutableStateOf<String?>(null) }
    var filterScreenGrabOnly by remember { mutableStateOf(false) }
    var filterAppTreeOnly by remember { mutableStateOf(false) }

    // BottomSheet inspection state for full-screen view
    var inspectingCaptureItem by remember { mutableStateOf<CaptureEntity?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                        onOpenFullscreen = { inspectingCaptureItem = item },
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

        // Fullscreen Modal BottomSheet for deep inspection
        if (inspectingCaptureItem != null) {
            val currentItem = inspectingCaptureItem!!
            ModalBottomSheet(
                onDismissRequest = { inspectingCaptureItem = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    CaptureDetailContent(
                        item = currentItem,
                        onRetry = {
                            scope.launch(Dispatchers.IO) {
                                val client = WebhookClient()
                                dao.update(currentItem.copy(status = DeliveryStatus.SENDING))
                                val result = client.sendPayload(
                                    url = settings.webhookUrl,
                                    jsonPayload = currentItem.rawJson,
                                    apiKey = settings.apiKey.ifBlank { null }
                                )
                                dao.update(
                                    currentItem.copy(
                                        status = if (result.isSuccess) DeliveryStatus.SUCCESS else DeliveryStatus.ERROR,
                                        responseCode = result.statusCode,
                                        responseTimeMs = result.responseTimeMs,
                                        errorMessage = result.errorMessage,
                                        retryCount = currentItem.retryCount + 1
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CaptureItemCard(
    item: CaptureEntity,
    onOpenFullscreen: () -> Unit,
    onRetry: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss · dd MMM", Locale.getDefault()) }
    val formattedDate = remember(item.timestamp) { dateFormat.format(Date(item.timestamp)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(250)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row (Clickable to expand/collapse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Status Icon & Badge
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Summary Content preview (always visible or truncated when collapsed)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp)
            ) {
                if (!item.inputPreview.isNullOrBlank()) {
                    Text(
                        text = item.inputPreview,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = if (isExpanded) 4 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (!item.packageName.isNullOrBlank()) {
                    Text(
                        text = "App: ${item.packageName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Summary chips (context icons)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = if (isExpanded) 8.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.hasScreenGrab) {
                            ContextIndicatorChip(icon = Icons.Default.Screenshot, label = "Screenshot")
                        }
                        if (item.hasAppTree) {
                            ContextIndicatorChip(icon = Icons.Default.CropFree, label = "App Tree")
                        }
                        if (item.hasHighlightedText) {
                            ContextIndicatorChip(icon = Icons.Default.TextFields, label = "Highlighted")
                        }
                        if (item.hasMedia) {
                            ContextIndicatorChip(icon = Icons.Default.PlayCircle, label = "Media")
                        }
                    }

                    if (item.status == DeliveryStatus.ERROR) {
                        OutlinedButton(
                            onClick = onRetry,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Expanded Tabbed View
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    CaptureDetailContent(
                        item = item,
                        onRetry = onRetry,
                        onOpenFullscreen = onOpenFullscreen,
                        isEmbeddedInCard = true
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextIndicatorChip(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.height(22.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CaptureDetailContent(
    item: CaptureEntity,
    onRetry: () -> Unit,
    onOpenFullscreen: (() -> Unit)? = null,
    isEmbeddedInCard: Boolean = false
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val payload = remember(item.rawJson) {
        try {
            Gson().fromJson(item.rawJson, CapturePayload::class.java)
        } catch (e: Exception) {
            null
        }
    }

    val availableTabs = remember(item, payload) {
        buildList {
            add(HistoryTab.Overview)
            if (item.hasScreenGrab || !payload?.screenshotBase64.isNullOrBlank()) {
                add(HistoryTab.Screenshot)
            }
            if (item.hasAppTree || !payload?.appTreeText.isNullOrBlank() || !payload?.highlightedText.isNullOrBlank()) {
                add(HistoryTab.AppTree)
            }
            if (item.hasMedia || payload?.mediaContext != null || !payload?.audioBase64.isNullOrBlank()) {
                add(HistoryTab.Media)
            }
            add(HistoryTab.RawJson) // Final tab
        }
    }

    val currentTabIndex = selectedTabIndex.coerceIn(0, availableTabs.lastIndex)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Tab Row Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ScrollableTabRow(
                selectedTabIndex = currentTabIndex,
                modifier = Modifier.weight(1f),
                edgePadding = 12.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                availableTabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = currentTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(tab.title, maxLines = 1) },
                        icon = { Icon(tab.icon, contentDescription = tab.title, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            if (onOpenFullscreen != null && isEmbeddedInCard) {
                IconButton(
                    onClick = onOpenFullscreen,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Expand Fullscreen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            when (availableTabs.getOrNull(currentTabIndex)) {
                is HistoryTab.Overview -> CaptureOverviewTab(item = item, payload = payload, onRetry = onRetry)
                is HistoryTab.Screenshot -> CaptureScreenshotTab(payload = payload)
                is HistoryTab.AppTree -> CaptureAppTreeTab(payload = payload)
                is HistoryTab.Media -> CaptureMediaTab(payload = payload)
                is HistoryTab.RawJson -> CaptureRawJsonTab(item = item)
                null -> Unit
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptureOverviewTab(
    item: CaptureEntity,
    payload: CapturePayload?,
    onRetry: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status and Delivery Summary Card
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            )
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Webhook Delivery",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (item.responseTimeMs != null) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Latency: ${item.responseTimeMs} ms",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val statusText = when (item.status) {
                        DeliveryStatus.SUCCESS -> "Delivered (HTTP ${item.responseCode ?: 200})"
                        DeliveryStatus.ERROR -> "Failed (${item.errorMessage ?: "HTTP ${item.responseCode ?: "Error"}"})"
                        DeliveryStatus.SENDING -> "In Progress / Sending..."
                    }
                    val statusColor = when (item.status) {
                        DeliveryStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                        DeliveryStatus.ERROR -> MaterialTheme.colorScheme.error
                        DeliveryStatus.SENDING -> MaterialTheme.colorScheme.tertiary
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )
                }

                if (!item.errorMessage.isNullOrBlank()) {
                    Text(
                        text = "Error detail: ${item.errorMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (item.retryCount > 0) {
                    Text(
                        text = "Retry attempts: ${item.retryCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Primary Input Content Section
        val inputContent = payload?.inputContent ?: item.inputPreview
        if (!inputContent.isNullOrBlank()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Input / Transcription",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Input Content", inputContent)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "Input copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Text(
                        text = inputContent,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Metadata Grid Section
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            )
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Event Metadata",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                MetaRow(label = "Capture Type", value = item.type)
                MetaRow(label = "Source", value = item.source)
                MetaRow(label = "Target Package", value = item.packageName ?: "None (System / Overlay)")

                // Active context tags
                val activeContexts = buildList {
                    if (item.hasScreenGrab) add("Screenshot")
                    if (item.hasAppTree) add("App Tree")
                    if (item.hasHighlightedText) add("Highlighted Text")
                    if (item.hasMedia) add("Media")
                }
                if (activeContexts.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Attached Context",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            activeContexts.forEach { ctxName ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = ctxName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun CaptureScreenshotTab(payload: CapturePayload?) {
    val context = LocalContext.current
    val base64Str = payload?.screenshotBase64
    var showFullPreviewDialog by remember { mutableStateOf(false) }

    val decodedBitmap: Bitmap? = remember(base64Str) {
        decodeBase64ToBitmap(base64Str)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (decodedBitmap != null) {
            val imageBitmap = remember(decodedBitmap) { decodedBitmap.asImageBitmap() }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Resolution: ${decodedBitmap.width} × ${decodedBitmap.height} px",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextButton(
                    onClick = {
                        val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        val filename = "screenshot_${fileDateFormat.format(Date())}.png"
                        val success = saveBitmapToPictures(context, decodedBitmap, filename)
                        if (success) {
                            Toast.makeText(context, "Saved to Pictures: $filename", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Failed to save screenshot", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save to Pictures", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (!payload?.targetedSelection.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Target Coordinate: ${payload?.targetedSelection}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .clickable { showFullPreviewDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Captured Screenshot",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text = "Tap image to view full scale",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (showFullPreviewDialog) {
                Dialog(onDismissRequest = { showFullPreviewDialog = false }) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 600.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "Full Screenshot Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(
                                onClick = { showFullPreviewDialog = false },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Close")
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No screenshot data available in this capture.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CaptureAppTreeTab(payload: CapturePayload?) {
    val context = LocalContext.current
    val appTreeText = payload?.appTreeText
    val highlightedText = payload?.highlightedText

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Highlighted text if present
        if (!highlightedText.isNullOrBlank()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatQuote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Highlighted Selection",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Highlighted Text", highlightedText)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "Highlighted text copied", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Text(
                        text = highlightedText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // App Tree / Accessibility Node Structure
        if (!appTreeText.isNullOrBlank()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Accessibility Tree Structure",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("App Tree Text", appTreeText)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "App tree copied", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Tree", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .padding(top = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = appTreeText,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        } else if (highlightedText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No App Tree or Highlighted Text attached.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CaptureMediaTab(payload: CapturePayload?) {
    val context = LocalContext.current
    val mediaContext: MediaContext? = payload?.mediaContext
    val audioBase64: String? = payload?.audioBase64

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (mediaContext != null) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Media Playback",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (!mediaContext.platform.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = mediaContext.platform,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (!mediaContext.title.isNullOrBlank()) {
                        Text(
                            text = mediaContext.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!mediaContext.channelOrArtist.isNullOrBlank()) {
                        Text(
                            text = "By ${mediaContext.channelOrArtist}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!mediaContext.playbackState.isNullOrBlank()) {
                        MetaRow(label = "State", value = mediaContext.playbackState)
                    }

                    if (mediaContext.timestampMs != null && mediaContext.timestampMs > 0) {
                        val seconds = (mediaContext.timestampMs / 1000) % 60
                        val minutes = (mediaContext.timestampMs / (1000 * 60)) % 60
                        val hours = mediaContext.timestampMs / (1000 * 60 * 60)
                        val formattedTime = if (hours > 0) {
                            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
                        } else {
                            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                        }
                        MetaRow(label = "Track Position", value = formattedTime)
                    }

                    if (!mediaContext.canonicalUrl.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { openUrl(context, mediaContext.canonicalUrl) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Link", style = MaterialTheme.typography.labelSmall)
                            }

                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = ClipData.newPlainText("Media Canonical URL", mediaContext.canonicalUrl)
                                    clipboard?.setPrimaryClip(clip)
                                    Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Voice / Audio Note Section
        if (!audioBase64.isNullOrBlank()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Recorded Mic Audio Note",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Base64 Audio payload attached (${(audioBase64.length * 3 / 4) / 1024} KB)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (mediaContext == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No media metadata or audio recorded in this capture.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CaptureRawJsonTab(item: CaptureEntity) {
    val context = LocalContext.current
    val fileDateFormat = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Actions toolbar: Copy, Download, Share
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("Capture Payload JSON", item.rawJson)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(context, "JSON copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy", style = MaterialTheme.typography.labelSmall)
            }

            TextButton(
                onClick = {
                    val filename = "capture_${fileDateFormat.format(Date(item.timestamp))}.json"
                    val success = saveJsonToDownloads(context, filename, item.rawJson)
                    if (success) {
                        Toast.makeText(context, "Saved: $filename", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Download", style = MaterialTheme.typography.labelSmall)
            }

            TextButton(
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, item.rawJson)
                        type = "application/json"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Capture JSON"))
                }
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share", style = MaterialTheme.typography.labelSmall)
            }
        }

        // Monospace code block
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 360.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = item.rawJson,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// Helpers
private fun decodeBase64ToBitmap(base64Str: String?): Bitmap? {
    if (base64Str.isNullOrBlank()) return null
    return try {
        val cleanBase64 = if (base64Str.contains(",")) {
            base64Str.substringAfter(",")
        } else {
            base64Str
        }
        val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        Log.e("HistoryScreen", "Failed to decode base64 bitmap", e)
        null
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open URL: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun saveBitmapToPictures(context: Context, bitmap: Bitmap, filename: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Capture")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                true
            } else false
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val captureDir = File(dir, "Capture").apply { if (!exists()) mkdirs() }
            val file = File(captureDir, filename)
            file.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            true
        }
    } catch (e: Exception) {
        Log.e("HistoryScreen", "Failed to save screenshot", e)
        false
    }
}

private fun saveJsonToDownloads(context: Context, filename: String, jsonContent: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonContent.toByteArray(Charsets.UTF_8))
                }
                true
            } else {
                val fallbackFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), filename)
                fallbackFile.writeText(jsonContent, Charsets.UTF_8)
                true
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val file = File(downloadsDir, filename)
            file.writeText(jsonContent, Charsets.UTF_8)
            true
        }
    } catch (e: Exception) {
        Log.e("HistoryScreen", "Error saving JSON to Downloads", e)
        try {
            val fallbackFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), filename)
            fallbackFile.writeText(jsonContent, Charsets.UTF_8)
            true
        } catch (e2: Exception) {
            Log.e("HistoryScreen", "Fallback file write failed", e2)
            false
        }
    }
}
