package com.capture.engine.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.capture.engine.CaptureApplication
import com.capture.engine.overlay.OverlayManager
import com.capture.engine.services.ScreenCaptureService
import com.capture.engine.ui.theme.CaptureEngineTheme

class MainActivity : ComponentActivity() {

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            Toast.makeText(this, "Screen capture service initialized", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screen capture permission declined", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestMediaProjectionPermission() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show floating toolbar if overlay permission granted and enabled
        val app = application as CaptureApplication
        if (app.settingsRepository.isOverlayEnabled) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                OverlayManager.getInstance(this).showToolbar()
            }
        }

        setContent {
            CaptureEngineTheme {
                var selectedNavIndex by remember { mutableIntStateOf(0) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = when (selectedNavIndex) {
                                        0 -> "Capture History"
                                        1 -> "Customisation"
                                        else -> "Settings"
                                    },
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedNavIndex == 0,
                                onClick = { selectedNavIndex = 0 },
                                icon = { Icon(Icons.Default.History, contentDescription = null) },
                                label = { Text("History") }
                            )
                            NavigationBarItem(
                                selected = selectedNavIndex == 1,
                                onClick = { selectedNavIndex = 1 },
                                icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                                label = { Text("Customisation") }
                            )
                            NavigationBarItem(
                                selected = selectedNavIndex == 2,
                                onClick = { selectedNavIndex = 2 },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("Settings") }
                            )
                        }
                    }
                ) { innerPadding ->
                    when (selectedNavIndex) {
                        0 -> HistoryScreen(modifier = Modifier.padding(innerPadding))
                        1 -> CustomisationScreen(modifier = Modifier.padding(innerPadding))
                        2 -> SettingsScreen(
                            onRequestMediaProjection = { requestMediaProjectionPermission() },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // If user granted overlay permission in Settings, start toolbar
        val app = application as CaptureApplication
        if (app.settingsRepository.isOverlayEnabled) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                OverlayManager.getInstance(this).showToolbar()
            }
        }
    }
}
