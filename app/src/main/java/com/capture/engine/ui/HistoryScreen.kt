package com.capture.engine.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.capture.engine.CaptureApplication
import com.capture.engine.model.CaptureEntity
import com.capture.engine.model.CapturePayload
import com.capture.engine.model.DeliveryStatus
import com.capture.engine.model.MediaContext
import com.capture.engine.network.WebhookClient
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun shareJsonFile(context: Context, filename: String, jsonContent: String) {
    try {
        val shareDir = File(context.cacheDir, "shared_captures")
        if (!shareDir.exists()) {
            shareDir.mkdirs()
        }
        val file = File(shareDir, filename)
        file.writeText(jsonContent, Charsets.UTF_8)

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Capture JSON"))
    } catch (e: Exception) {
        Log.e("HistoryScreen", "Error sharing JSON file", e)
        Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun shareMultipleJsonFiles(context: Context, items: List<CaptureEntity>) {
    if (items.isEmpty()) return
    try {
        val shareDir = File(context.cacheDir, "shared_captures")
        if (!shareDir.exists()) {
            shareDir.mkdirs()
        }
        val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val uris = ArrayList<Uri>()
        items.forEach { item ->
            val filename = "capture_${fileDateFormat.format(Date(item.timestamp))}_${item.id}.json"
            val file = File(shareDir, filename)
            file.writeText(item.getRawJson(context), Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            uris.add(uri)
        }

        val sendIntent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uris[0])
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/json"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share ${items.size} Captures"))
    } catch (e: Exception) {
        Log.e("HistoryScreen", "Error sharing JSON files", e)
        Toast.makeText(context, "Error sharing files: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun saveMultipleJsonToDownloads(context: Context, items: List<CaptureEntity>): Boolean {
    if (items.isEmpty()) return false
    val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    var successCount = 0
    items.forEach { item ->
        val filename = "capture_${fileDateFormat.format(Date(item.timestamp))}_${item.id}.json"
        if (saveJsonToDownloads(context, filename, item.getRawJson(context))) {
            successCount++
        }
    }
    if (successCount == items.size) {
        Toast.makeText(context, "Saved $successCount capture${if (successCount > 1) "s" else ""} to Downloads", Toast.LENGTH_LONG).show()
    } else {
        Toast.makeText(context, "Saved $successCount of ${items.size} captures to Downloads", Toast.LENGTH_SHORT).show()
    }
    return successCount > 0
}

fun copyMultipleJsonToClipboard(context: Context, items: List<CaptureEntity>) {
    if (items.isEmpty()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val jsonContent = if (items.size == 1) {
        items.first().getRawJson(context)
    } else {
        val parsedList = items.map { item ->
            try {
                Gson().fromJson(item.getRawJson(context), Any::class.java)
            } catch (e: Exception) {
                item.getRawJson(context)
            }
        }
        GsonBuilder().setPrettyPrinting().create().toJson(parsedList)
    }
    val clip = ClipData.newPlainText("Capture Payload JSON", jsonContent)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(
        context,
        if (items.size == 1) "JSON copied to clipboard" else "${items.size} captures copied to clipboard",
        Toast.LENGTH_SHORT
    ).show()
}

fun getAppName(context: Context, packageName: String?): String? {
    if (packageName.isNullOrBlank()) return null
    return try {
        val pm = context.packageManager
        val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
        pm.getApplicationLabel(appInfo).toString()
    } catch (e: Exception) {
        null
    }
}

sealed class HistoryTab(val title: String, val icon: ImageVector) {
    data object Overview : HistoryTab("Overview", Icons.Default.Info)
    data object Input : HistoryTab("Input", Icons.Default.Keyboard)
    data object SharedContent : HistoryTab("Shared", Icons.Default.Description)
    data object Attachment : HistoryTab("Attachment", Icons.Default.AttachFile)
    data object Screenshot : HistoryTab("Screenshot", Icons.Default.Screenshot)
    data object AppTree : HistoryTab("App Tree", Icons.Default.AppShortcut)
    data object Media : HistoryTab("Media", Icons.Default.PlayCircle)
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

    // Selection mode state
    var isSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    BackHandler(enabled = isSelectMode) {
        isSelectMode = false
        selectedIds = emptySet()
    }

    // Filters
    var selectedTypeFilter by remember { mutableStateOf<String?>(null) }
    var filterScreenGrabOnly by remember { mutableStateOf(false) }
    var filterAppTreeOnly by remember { mutableStateOf(false) }
    var filterAttachmentOnly by remember { mutableStateOf(false) }
    var filterShareOnly by remember { mutableStateOf(false) }

    // BottomSheet inspection state for full-screen view
    var inspectingCaptureItem by remember { mutableStateOf<CaptureEntity?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filteredCaptures = captures.filter { capture ->
        (selectedTypeFilter == null || capture.type == selectedTypeFilter) &&
                (!filterScreenGrabOnly || capture.hasScreenGrab) &&
                (!filterAppTreeOnly || capture.hasAppTree) &&
                (!filterAttachmentOnly || capture.hasAttachment) &&
                (!filterShareOnly || capture.hasShareContext)
    }

    val selectedCaptures = remember(captures, selectedIds) {
        captures.filter { it.id in selectedIds }
    }

    val hasErrorsInSelection = remember(selectedCaptures) {
        selectedCaptures.any { it.status == DeliveryStatus.ERROR }
    }
    val hasSuccessInSelection = remember(selectedCaptures) {
        selectedCaptures.any { it.status == DeliveryStatus.SUCCESS }
    }

    val onBulkDownload: (List<CaptureEntity>) -> Unit = { items ->
        saveMultipleJsonToDownloads(context, items)
    }

    val onBulkCopy: (List<CaptureEntity>) -> Unit = { items ->
        copyMultipleJsonToClipboard(context, items)
    }

    val onBulkShare: (List<CaptureEntity>) -> Unit = { items ->
        shareMultipleJsonFiles(context, items)
    }

    val onBulkDelete: (List<CaptureEntity>) -> Unit = { items ->
        scope.launch(Dispatchers.IO) {
            items.forEach { item ->
                try {
                    val file = File(item.payloadFilePath)
                    if (file.exists()) file.delete()
                } catch (e: Exception) {}
                dao.deleteById(item.id)
            }
        }
        Toast.makeText(context, "${items.size} capture${if (items.size > 1) "s" else ""} deleted", Toast.LENGTH_SHORT).show()
        selectedIds = emptySet()
        isSelectMode = false
    }

    val onBulkRetryOrResend: (List<CaptureEntity>, DeliveryStatus?) -> Unit = { items, targetStatus ->
        val targets = if (targetStatus != null) items.filter { it.status == targetStatus } else items
        if (targets.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val client = WebhookClient()
                targets.forEach { item ->
                    dao.update(item.copy(status = DeliveryStatus.SENDING))
                }
                targets.forEach { item ->
                    val result = client.sendPayload(
                        url = settings.webhookUrl,
                        jsonPayload = item.getRawJson(context),
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
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Selection Mode Action Bar
        AnimatedVisibility(visible = isSelectMode) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = {
                            isSelectMode = false
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit selection")
                        }
                        Text(
                            text = "${selectedIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = {
                                if (selectedIds.size == filteredCaptures.size) {
                                    selectedIds = emptySet()
                                } else {
                                    selectedIds = filteredCaptures.map { it.id }.toSet()
                                }
                            }
                        ) {
                            Text(
                                text = if (selectedIds.size == filteredCaptures.size && filteredCaptures.isNotEmpty()) "Deselect All" else "Select All",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        if (selectedIds.isNotEmpty()) {
                            IconButton(onClick = { onBulkCopy(selectedCaptures) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy selected", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { onBulkShare(selectedCaptures) }) {
                                Icon(Icons.Default.Share, contentDescription = "Share selected", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { onBulkDownload(selectedCaptures) }) {
                                Icon(Icons.Default.Download, contentDescription = "Download selected", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { onBulkDelete(selectedCaptures) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete selected", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

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
                selected = selectedTypeFilter == null && !filterScreenGrabOnly && !filterAppTreeOnly && !filterAttachmentOnly && !filterShareOnly,
                onClick = {
                    selectedTypeFilter = null
                    filterScreenGrabOnly = false
                    filterAppTreeOnly = false
                    filterAttachmentOnly = false
                    filterShareOnly = false
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
                selected = filterShareOnly,
                onClick = { filterShareOnly = !filterShareOnly },
                label = { Text("Shared") }
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
            FilterChip(
                selected = filterAttachmentOnly,
                onClick = { filterAttachmentOnly = !filterAttachmentOnly },
                label = { Text("With Attachment") }
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
                    val isSelected = selectedIds.contains(item.id)
                    CaptureItemCard(
                        item = item,
                        isSelectMode = isSelectMode,
                        isSelected = isSelected,
                        selectedCount = selectedIds.size,
                        onToggleSelect = {
                            selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                        },
                        onEnterSelectMode = {
                            isSelectMode = true
                            selectedIds = setOf(item.id)
                        },
                        onOpenFullscreen = { inspectingCaptureItem = item },
                        onRetry = {
                            onBulkRetryOrResend(listOf(item), DeliveryStatus.ERROR)
                        },
                        onResend = {
                            onBulkRetryOrResend(listOf(item), DeliveryStatus.SUCCESS)
                        },
                        onDelete = {
                            onBulkDelete(listOf(item))
                        },
                        onBulkDownload = {
                            val targets = if (selectedCaptures.isNotEmpty()) selectedCaptures else listOf(item)
                            onBulkDownload(targets)
                        },
                        onBulkCopy = {
                            val targets = if (selectedCaptures.isNotEmpty()) selectedCaptures else listOf(item)
                            onBulkCopy(targets)
                        },
                        onBulkShare = {
                            val targets = if (selectedCaptures.isNotEmpty()) selectedCaptures else listOf(item)
                            onBulkShare(targets)
                        },
                        onBulkDelete = {
                            val targets = if (selectedCaptures.isNotEmpty()) selectedCaptures else listOf(item)
                            onBulkDelete(targets)
                        },
                        onBulkRetry = {
                            val targets = if (selectedCaptures.isNotEmpty()) selectedCaptures else listOf(item)
                            onBulkRetryOrResend(targets, DeliveryStatus.ERROR)
                        },
                        onBulkResend = {
                            val targets = if (selectedCaptures.isNotEmpty()) selectedCaptures else listOf(item)
                            onBulkRetryOrResend(targets, DeliveryStatus.SUCCESS)
                        },
                        hasErrorsInSelection = if (isSelectMode) hasErrorsInSelection else item.status == DeliveryStatus.ERROR,
                        hasSuccessInSelection = if (isSelectMode) hasSuccessInSelection else item.status == DeliveryStatus.SUCCESS
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
                                    jsonPayload = currentItem.getRawJson(context),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CaptureItemCard(
    item: CaptureEntity,
    isSelectMode: Boolean = false,
    isSelected: Boolean = false,
    selectedCount: Int = 0,
    onToggleSelect: () -> Unit = {},
    onEnterSelectMode: () -> Unit = {},
    onOpenFullscreen: () -> Unit,
    onRetry: () -> Unit,
    onResend: () -> Unit = onRetry,
    onDelete: () -> Unit,
    onBulkDownload: () -> Unit = {},
    onBulkCopy: () -> Unit = {},
    onBulkShare: () -> Unit = {},
    onBulkDelete: () -> Unit = {},
    onBulkRetry: () -> Unit = {},
    onBulkResend: () -> Unit = {},
    hasErrorsInSelection: Boolean = false,
    hasSuccessInSelection: Boolean = false
) {
    val context = LocalContext.current
    var showContextMenu by remember { mutableStateOf(false) }
    val fileDateFormat = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()) }
    val dateTimeFormat = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    val formattedDateTime = remember(item.timestamp) { dateTimeFormat.format(Date(item.timestamp)) }
    val resolvedAppName = item.appName ?: remember(item.packageName) { getAppName(context, item.packageName) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = {
                    if (isSelectMode) {
                        onToggleSelect()
                    } else {
                        onOpenFullscreen()
                    }
                },
                onLongClick = {
                    if (isSelectMode) {
                        if (!isSelected) {
                            onToggleSelect()
                        }
                    }
                    showContextMenu = true
                }
            ),
        shape = RoundedCornerShape(16.dp),
        border = if (isSelectMode && isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelectMode && isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header Row: Checkbox (if in select mode), Status Icon, App Title directly after icon, and formatted date + time (e.g. 28 Aug, HH:MM)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        when (item.status) {
                            DeliveryStatus.SUCCESS -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DeliveryStatus.ERROR -> {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DeliveryStatus.SENDING -> {
                                Icon(
                                    imageVector = Icons.Default.HourglassEmpty,
                                    contentDescription = "Sending",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        val displayAppName = resolvedAppName ?: item.packageName
                        if (!displayAppName.isNullOrBlank()) {
                            Text(
                                text = displayAppName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Text(
                        text = formattedDateTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Primary Input preview
                if (!item.inputPreview.isNullOrBlank()) {
                    Text(
                        text = item.inputPreview,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Summary chips (context icons matching floating toolbar) & Retry action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.hasShareContext) {
                            ContextIndicatorChip(icon = Icons.Default.Description, label = "Shared")
                        }
                        if (item.hasScreenGrab) {
                            ContextIndicatorChip(icon = Icons.Default.Screenshot, label = "Screenshot")
                        }
                        if (item.hasAppTree) {
                            ContextIndicatorChip(icon = Icons.Default.AppShortcut, label = "App Tree")
                        }
                        if (item.hasHighlightedText) {
                            ContextIndicatorChip(icon = Icons.AutoMirrored.Filled.ShortText, label = "Highlighted")
                        }
                        if (item.hasMedia) {
                            ContextIndicatorChip(icon = Icons.Default.PlayCircle, label = "Media")
                        }
                        if (item.hasAttachment) {
                            ContextIndicatorChip(icon = Icons.Default.AttachFile, label = "Attachment")
                        }
                    }

                    if (item.status == DeliveryStatus.ERROR && !isSelectMode) {
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

            // Long Press Context Menu
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false }
            ) {
                if (!isSelectMode) {
                    DropdownMenuItem(
                        text = { Text("Select") },
                        leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null) },
                        onClick = {
                            showContextMenu = false
                            onEnterSelectMode()
                        }
                    )
                    if (item.status == DeliveryStatus.ERROR) {
                        DropdownMenuItem(
                            text = { Text("Retry") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showContextMenu = false
                                onRetry()
                            }
                        )
                    } else if (item.status == DeliveryStatus.SUCCESS) {
                        DropdownMenuItem(
                            text = { Text("Resend") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                            onClick = {
                                showContextMenu = false
                                onResend()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Download") },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                        onClick = {
                            showContextMenu = false
                            val filename = "capture_${fileDateFormat.format(Date(item.timestamp))}.json"
                            val success = saveJsonToDownloads(context, filename, item.getRawJson(context))
                            if (success) {
                                Toast.makeText(context, "Saved to Downloads: $filename", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            showContextMenu = false
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("Capture Payload JSON", item.getRawJson(context))
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "JSON copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showContextMenu = false
                            val filename = "capture_${fileDateFormat.format(Date(item.timestamp))}.json"
                            shareJsonFile(context, filename, item.getRawJson(context))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showContextMenu = false
                            onDelete()
                        }
                    )
                } else {
                    val countLabel = if (selectedCount > 1) " ($selectedCount)" else ""

                    if (hasErrorsInSelection) {
                        DropdownMenuItem(
                            text = { Text("Retry failed$countLabel") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showContextMenu = false
                                onBulkRetry()
                            }
                        )
                    }
                    if (hasSuccessInSelection) {
                        DropdownMenuItem(
                            text = { Text("Resend$countLabel") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                            onClick = {
                                showContextMenu = false
                                onBulkResend()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Download$countLabel") },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                        onClick = {
                            showContextMenu = false
                            onBulkDownload()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy$countLabel") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            showContextMenu = false
                            onBulkCopy()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share$countLabel") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showContextMenu = false
                            onBulkShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete$countLabel", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showContextMenu = false
                            onBulkDelete()
                        }
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
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val payload = remember(item.payloadFilePath) {
        try {
            val json = item.getRawJson(context)
            Gson().fromJson(json, CapturePayload::class.java)
        } catch (e: Exception) {
            null
        }
    }

    val availableTabs = remember(item, payload) {
        buildList {
            add(HistoryTab.Overview)
            if (!payload?.inputContent.isNullOrBlank() || !item.inputPreview.isNullOrBlank() || !payload?.audioBase64.isNullOrBlank()) {
                add(HistoryTab.Input)
            }
            if (item.hasShareContext || payload?.shareContext != null) {
                add(HistoryTab.SharedContent)
            }
            if (!payload?.attachmentBase64.isNullOrBlank()) {
                add(HistoryTab.Attachment)
            }
            if (item.hasScreenGrab || !payload?.screenshotBase64.isNullOrBlank()) {
                add(HistoryTab.Screenshot)
            }
            if (item.hasAppTree || !payload?.appTreeText.isNullOrBlank() || !payload?.highlightedText.isNullOrBlank()) {
                add(HistoryTab.AppTree)
            }
            if (item.hasMedia || payload?.mediaContext != null) {
                add(HistoryTab.Media)
            }
            add(HistoryTab.RawJson)
        }
    }

    val currentTabIndex = selectedTabIndex.coerceIn(0, availableTabs.lastIndex)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Tab Row Header
        ScrollableTabRow(
            selectedTabIndex = currentTabIndex,
            modifier = Modifier.fillMaxWidth(),
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            when (availableTabs.getOrNull(currentTabIndex)) {
                is HistoryTab.Overview -> CaptureOverviewTab(item = item, payload = payload, onRetry = onRetry)
                is HistoryTab.Input -> CaptureInputTab(item = item, payload = payload)
                is HistoryTab.SharedContent -> CaptureSharedContentTab(payload = payload)
                is HistoryTab.Attachment -> CaptureAttachmentTab(item = item, payload = payload)
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

                val fullDateFormat = remember { SimpleDateFormat("EEEE, d MMMM yyyy · HH:mm:ss", Locale.getDefault()) }
                val formattedFullDate = remember(item.timestamp) { fullDateFormat.format(Date(item.timestamp)) }
                MetaRow(label = "Timestamp", value = formattedFullDate)
                MetaRow(label = "Capture Type", value = item.type)
                MetaRow(label = "Source", value = item.source)
                val resolvedAppName = payload?.appName ?: item.appName ?: remember(item.packageName) { getAppName(context, item.packageName) }
                if (!resolvedAppName.isNullOrBlank()) {
                    MetaRow(label = "Target App", value = resolvedAppName)
                }
                MetaRow(label = "Target Package", value = item.packageName ?: "None (System / Overlay)")

                // Active context tags
                val activeContexts = buildList {
                    if (item.hasShareContext || payload?.shareContext != null) add("Shared Content")
                    if (item.hasScreenGrab) add("Screenshot")
                    if (item.hasAppTree) add("App Tree")
                    if (item.hasHighlightedText) add("Highlighted Text")
                    if (item.hasMedia) add("Media")
                    if (!payload?.attachmentBase64.isNullOrBlank()) add("Attachment (${payload?.attachmentMime ?: "file"})")
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
fun CaptureInputTab(
    item: CaptureEntity,
    payload: CapturePayload?
) {
    val context = LocalContext.current
    val inputContent = payload?.inputContent ?: item.inputPreview
    val audioBase64 = payload?.audioBase64

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Text / Transcription Card
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
                            text = "Text / Transcription",
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

        // 2. Playable Recorded Audio Card
        if (!audioBase64.isNullOrBlank()) {
            AudioPlayerCard(audioBase64 = audioBase64, captureId = item.id)
        }

        if (inputContent.isNullOrBlank() && audioBase64.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No input text or recorded audio for this capture.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AudioPlayerCard(
    audioBase64: String,
    captureId: Long
) {
    val context = LocalContext.current
    val tempAudioFile = remember(audioBase64, captureId) {
        try {
            val clean = if (audioBase64.contains(",")) audioBase64.substringAfter(",") else audioBase64
            val decoded = Base64.decode(clean, Base64.DEFAULT)
            val file = java.io.File(context.cacheDir, "play_audio_${captureId}.m4a")
            file.writeBytes(decoded)
            file
        } catch (e: Exception) {
            Log.e("HistoryScreen", "Error saving temp audio file", e)
            null
        }
    }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableIntStateOf(0) }
    var totalDurationMs by remember { mutableIntStateOf(0) }

    DisposableEffect(tempAudioFile) {
        if (tempAudioFile != null && tempAudioFile.exists()) {
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(tempAudioFile.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        isPlaying = false
                        currentPosMs = 0
                    }
                }
                mediaPlayer = mp
                totalDurationMs = mp.duration.coerceAtLeast(0)
            } catch (e: Exception) {
                Log.e("HistoryScreen", "Error initializing MediaPlayer", e)
            }
        }
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (e: Exception) {
                // Ignore
            }
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    currentPosMs = mp.currentPosition
                }
            }
            delay(100L)
        }
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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

            // Audio Player Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalIconButton(
                    onClick = {
                        mediaPlayer?.let { mp ->
                            if (mp.isPlaying) {
                                mp.pause()
                                isPlaying = false
                            } else {
                                mp.start()
                                isPlaying = true
                            }
                        }
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Progress slider & time display
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Slider(
                        value = if (totalDurationMs > 0) currentPosMs.toFloat() else 0f,
                        onValueChange = { newPos ->
                            currentPosMs = newPos.toInt()
                            mediaPlayer?.seekTo(currentPosMs)
                        },
                        valueRange = 0f..(totalDurationMs.toFloat().coerceAtLeast(1f)),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatMs(currentPosMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatMs(totalDurationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Int): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
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
fun CaptureSharedContentTab(payload: CapturePayload?) {
    val context = LocalContext.current
    val shareContext = payload?.shareContext

    if (shareContext == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No shared content for this capture.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Shared Content Envelope",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (!shareContext.text.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Shared Text", shareContext.text)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "Shared text copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Text", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (!shareContext.title.isNullOrBlank()) {
                    MetaRow(label = "Title / Subject", value = shareContext.title!!)
                }

                if (!shareContext.url.isNullOrBlank()) {
                    MetaRow(label = "Extracted URL", value = shareContext.url!!)
                }

                if (!shareContext.mimeType.isNullOrBlank()) {
                    MetaRow(label = "MIME Type", value = shareContext.mimeType!!)
                }

                if (!shareContext.text.isNullOrBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Text(
                        text = "Shared Text Body",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = shareContext.text!!,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CaptureAttachmentTab(
    item: CaptureEntity,
    payload: CapturePayload?
) {
    val context = LocalContext.current
    val base64Str = payload?.attachmentBase64
    val mimeType = payload?.attachmentMime ?: "application/octet-stream"

    val attachmentBytes = remember(base64Str) {
        if (base64Str.isNullOrBlank()) null
        else {
            try {
                val clean = if (base64Str.contains(",")) base64Str.substringAfter(",") else base64Str
                Base64.decode(clean.trim(), Base64.DEFAULT)
            } catch (e: Exception) {
                Log.e("HistoryScreen", "Failed to decode attachment base64", e)
                null
            }
        }
    }

    if (attachmentBytes == null || base64Str.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No attachment data available for this capture.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val isImage = mimeType.startsWith("image/", ignoreCase = true)
    val isAudio = mimeType.startsWith("audio/", ignoreCase = true) || mimeType.contains("m4a", ignoreCase = true)
    val isText = mimeType.startsWith("text/", ignoreCase = true) || mimeType.contains("json", ignoreCase = true)
    val ext = getExtensionForMime(mimeType)
    val formattedSize = remember(attachmentBytes.size) {
        val bytes = attachmentBytes.size
        when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${String.format(Locale.getDefault(), "%.1f", bytes / 1024f)} KB"
            else -> "${String.format(Locale.getDefault(), "%.2f", bytes / (1024f * 1024f))} MB"
        }
    }

    val fileDateFormat = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()) }
    val defaultFilename = "attachment_${item.id}_${fileDateFormat.format(Date(item.timestamp))}.$ext"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Attachment Info Card
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val headerIcon = when {
                            isImage -> Icons.Default.Image
                            isAudio -> Icons.Default.Audiotrack
                            mimeType.contains("pdf") -> Icons.Default.Description
                            else -> Icons.Default.AttachFile
                        }
                        Icon(
                            imageVector = headerIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Attachment (${ext.uppercase()})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = formattedSize,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Text(
                    text = "MIME: $mimeType",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. IMAGE PREVIEW
        if (isImage) {
            val decodedBitmap = remember(attachmentBytes) {
                try {
                    BitmapFactory.decodeByteArray(attachmentBytes, 0, attachmentBytes.size)
                } catch (e: Exception) {
                    Log.e("HistoryScreen", "Error decoding attachment bitmap", e)
                    null
                }
            }

            if (decodedBitmap != null) {
                val imageBitmap = remember(decodedBitmap) { decodedBitmap.asImageBitmap() }
                var showFullPreviewDialog by remember { mutableStateOf(false) }

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

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                val success = saveBitmapToPictures(context, decodedBitmap, defaultFilename)
                                if (success) {
                                    Toast.makeText(context, "Saved to Pictures: $defaultFilename", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = {
                                shareAttachmentFile(context, defaultFilename, mimeType, attachmentBytes)
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(20.dp))
                        }
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
                        contentDescription = "Attachment Image Preview",
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
                                    contentDescription = "Full Attachment Preview",
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
                Text(
                    text = "Unable to render image preview from raw bytes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        // 2. AUDIO PLAYER
        else if (isAudio) {
            AudioPlayerCard(
                audioBase64 = base64Str,
                captureId = item.id
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val success = saveBytesToDownloads(context, defaultFilename, mimeType, attachmentBytes)
                        if (success) {
                            Toast.makeText(context, "Saved to Downloads: $defaultFilename", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Failed to save audio", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download Audio")
                }

                OutlinedButton(
                    onClick = {
                        shareAttachmentFile(context, defaultFilename, mimeType, attachmentBytes)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Audio")
                }
            }
        }
        // 3. TEXT PREVIEW
        else if (isText) {
            val textContent = remember(attachmentBytes) {
                try {
                    String(attachmentBytes, Charsets.UTF_8)
                } catch (e: Exception) {
                    null
                }
            }

            if (!textContent.isNullOrBlank()) {
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
                                text = "Text Preview",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Attachment Text", textContent))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Text(
                            text = textContent.take(5000) + if (textContent.length > 5000) "\n\n[... Truncated ...]" else "",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val success = saveBytesToDownloads(context, defaultFilename, mimeType, attachmentBytes)
                        if (success) {
                            Toast.makeText(context, "Saved to Downloads: $defaultFilename", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download File")
                }

                OutlinedButton(
                    onClick = {
                        shareAttachmentFile(context, defaultFilename, mimeType, attachmentBytes)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share / Open")
                }
            }
        }
        // 4. OTHER BINARY / DOCUMENT
        else {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = defaultFilename,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$formattedSize · $mimeType",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val success = saveBytesToDownloads(context, defaultFilename, mimeType, attachmentBytes)
                        if (success) {
                            Toast.makeText(context, "Saved to Downloads: $defaultFilename", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download File")
                }

                OutlinedButton(
                    onClick = {
                        shareAttachmentFile(context, defaultFilename, mimeType, attachmentBytes)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share / Open")
                }
            }
        }

        // Action: Copy Base64 String
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Raw Base64 Encoded Data",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${base64Str.length} characters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Attachment Base64", base64Str))
                        Toast.makeText(context, "Base64 copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
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
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No media metadata recorded in this capture.",
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
    val rawJson = remember(item.payloadFilePath) { item.getRawJson(context) }

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
                    val clip = ClipData.newPlainText("Capture Payload JSON", rawJson)
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
                    val success = saveJsonToDownloads(context, filename, rawJson)
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
                    val filename = "capture_${fileDateFormat.format(Date(item.timestamp))}.json"
                    shareJsonFile(context, filename, rawJson)
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
                    text = rawJson,
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

private fun saveBytesToDownloads(context: Context, filename: String, mimeType: String, bytes: ByteArray): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(bytes)
                }
                true
            } else {
                val fallbackFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), filename)
                fallbackFile.writeBytes(bytes)
                true
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val file = File(downloadsDir, filename)
            file.writeBytes(bytes)
            true
        }
    } catch (e: Exception) {
        Log.e("HistoryScreen", "Error saving bytes to Downloads", e)
        try {
            val fallbackFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), filename)
            fallbackFile.writeBytes(bytes)
            true
        } catch (e2: Exception) {
            Log.e("HistoryScreen", "Fallback file write failed", e2)
            false
        }
    }
}

private fun shareAttachmentFile(context: Context, filename: String, mimeType: String, bytes: ByteArray) {
    try {
        val shareDir = File(context.cacheDir, "shared_attachments")
        if (!shareDir.exists()) {
            shareDir.mkdirs()
        }
        val file = File(shareDir, filename)
        file.writeBytes(bytes)

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share attachment"))
    } catch (e: Exception) {
        Log.e("HistoryScreen", "Failed to share attachment", e)
        Toast.makeText(context, "Failed to share attachment: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun hasAttachment(rawJson: String?): Boolean {
    if (rawJson.isNullOrBlank()) return false
    val index = rawJson.indexOf("\"attachment_base64\"")
    if (index == -1) return false
    val colonIndex = rawJson.indexOf(':', index)
    if (colonIndex == -1) return false
    val rest = rawJson.substring(colonIndex + 1).trimStart()
    if (rest.startsWith("null")) return false
    if (rest.startsWith("\"\"")) return false
    return rest.startsWith("\"")
}

private fun getExtensionForMime(mimeType: String?): String {
    if (mimeType == null) return "bin"
    return when {
        mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
        mimeType.contains("png") -> "png"
        mimeType.contains("webp") -> "webp"
        mimeType.contains("gif") -> "gif"
        mimeType.contains("svg") -> "svg"
        mimeType.contains("mp4") || mimeType.contains("m4a") -> "m4a"
        mimeType.contains("mp3") || mimeType.contains("mpeg") -> "mp3"
        mimeType.contains("wav") -> "wav"
        mimeType.contains("ogg") -> "ogg"
        mimeType.contains("aac") -> "aac"
        mimeType.contains("pdf") -> "pdf"
        mimeType.contains("json") -> "json"
        mimeType.contains("html") -> "html"
        mimeType.contains("plain") || mimeType.contains("text") -> "txt"
        mimeType.contains("zip") -> "zip"
        mimeType.contains("csv") -> "csv"
        else -> "bin"
    }
}

private fun hasShareContext(rawJson: String?): Boolean {
    if (rawJson.isNullOrBlank()) return false
    val index = rawJson.indexOf("\"share_context\"")
    if (index == -1) return false
    val colonIndex = rawJson.indexOf(':', index)
    if (colonIndex == -1) return false
    val rest = rawJson.substring(colonIndex + 1).trimStart()
    if (rest.startsWith("null")) return false
    return rest.startsWith("{")
}

