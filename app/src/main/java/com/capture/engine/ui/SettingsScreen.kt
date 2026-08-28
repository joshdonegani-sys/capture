package com.capture.engine.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon as AndroidIcon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.capture.engine.R
import com.capture.engine.services.CaptureTileService
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.capture.engine.CaptureApplication
import com.capture.engine.network.WebhookClient
import com.capture.engine.network.WebhookWorker
import com.capture.engine.overlay.OverlayManager
import com.capture.engine.services.CaptureAccessibilityService
import com.capture.engine.services.MediaMetadataListener
import com.capture.engine.services.ScreenCaptureService
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onRequestMediaProjection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = CaptureApplication.instance
    val settings = app.settingsRepository
    val scope = rememberCoroutineScope()

    var webhookUrl by remember { mutableStateOf(settings.webhookUrl) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var retryMinutes by remember { mutableStateOf(settings.retryIntervalMinutes.toString()) }
    var isFullTree by remember { mutableStateOf(settings.isFullAppTreeEnabled) }
    var isOverlayEnabled by remember { mutableStateOf(settings.isOverlayEnabled) }

    // Test webhook status state
    var testStatusText by remember { mutableStateOf<String?>(null) }
    var isTestingWebhook by remember { mutableStateOf(false) }

    // Permission states
    val isAccessibilityActive = CaptureAccessibilityService.isRunning
    val isMediaProjectionActive by ScreenCaptureService.isRunningFlow.collectAsState()
    val isNotificationListenerActive = MediaMetadataListener.isConnected
    val isOverlayPermissionActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else true

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Section: Permissions
        Text(
            text = "System Permissions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Overlay Permission
                PermissionItem(
                    title = "Floating Overlay Window",
                    subtitle = "Allows the floating HUD to appear over apps",
                    isGranted = isOverlayPermissionActive,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Accessibility Service
                PermissionItem(
                    title = "Accessibility Service",
                    subtitle = "Reads active UI tree and focused elements",
                    isGranted = isAccessibilityActive,
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // MediaProjection Screen Capture
                PermissionItem(
                    title = "Screen Capture (MediaProjection)",
                    subtitle = "Takes raw pixel screenshots and cropped regions",
                    isGranted = isMediaProjectionActive,
                    onClick = {
                        onRequestMediaProjection()
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Notification / Media Listener
                PermissionItem(
                    title = "Media Session Listener",
                    subtitle = "Extracts podcast and music playback info",
                    isGranted = isNotificationListenerActive,
                    onClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        context.startActivity(intent)
                    }
                )
            }
        }

        // Section: Webhook Configuration
        Text(
            text = "Webhook Configuration",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = webhookUrl,
                    onValueChange = {
                        webhookUrl = it
                        settings.webhookUrl = it
                    },
                    label = { Text("Webhook URL") },
                    placeholder = { Text("https://example.com/api/capture") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        settings.apiKey = it
                    },
                    label = { Text("API Key / Bearer Token (Optional)") },
                    placeholder = { Text("sk-...") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = retryMinutes,
                        onValueChange = {
                            retryMinutes = it
                            val parsed = it.toIntOrNull() ?: 5
                            settings.retryIntervalMinutes = parsed
                            WebhookWorker.schedulePeriodicRetry(context, parsed)
                        },
                        label = { Text("Retry Interval (min)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            isTestingWebhook = true
                            testStatusText = "Testing..."
                            scope.launch {
                                val client = WebhookClient()
                                val testJson = """{"event": "ping", "test": true, "timestamp": ${System.currentTimeMillis()}}"""
                                val result = client.sendPayload(
                                    url = webhookUrl,
                                    jsonPayload = testJson,
                                    apiKey = apiKey.ifBlank { null }
                                )
                                isTestingWebhook = false
                                testStatusText = if (result.isSuccess) {
                                    "Connected! (HTTP ${result.statusCode}, ${result.responseTimeMs}ms)"
                                } else {
                                    "Failed: ${result.errorMessage}"
                                }
                            }
                        },
                        enabled = !isTestingWebhook && webhookUrl.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test")
                    }
                }

                if (testStatusText != null) {
                    Text(
                        text = testStatusText ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (testStatusText?.startsWith("Connected") == true) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        }

        // Section: Customisation & Advanced
        Text(
            text = "Customisation",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Overlay Enabled Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Floating Overlay", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("Show draggable HUD over other apps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isOverlayEnabled,
                        onCheckedChange = {
                            isOverlayEnabled = it
                            settings.isOverlayEnabled = it
                            if (it) {
                                OverlayManager.getInstance(context).showToolbar()
                            } else {
                                OverlayManager.getInstance(context).hideAll()
                            }
                            CaptureTileService.requestTileUpdate(context)
                        }
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val statusBarManager = context.getSystemService(StatusBarManager::class.java)
                    OutlinedButton(
                        onClick = {
                            try {
                                statusBarManager?.requestAddTileService(
                                    ComponentName(context, CaptureTileService::class.java),
                                    context.getString(R.string.qs_tile_label),
                                    AndroidIcon.createWithResource(context, R.drawable.ic_qs_hud),
                                    context.mainExecutor
                                ) { /* result handled by OS */ }
                            } catch (e: Exception) {
                                // Fallback
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add to Quick Settings Shade")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Full Tree vs Filtered Tree Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Full Raw Accessibility Tree", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            text = if (isFullTree) "Dumping all structural layout nodes" else "Filtered for visible text & descriptions (Recommended)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isFullTree,
                        onCheckedChange = {
                            isFullTree = it
                            settings.isFullAppTreeEnabled = it
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isGranted) {
            FilledTonalButton(
                onClick = onClick,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Active")
            }
        } else {
            OutlinedButton(
                onClick = onClick,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Grant")
            }
        }
    }
}
