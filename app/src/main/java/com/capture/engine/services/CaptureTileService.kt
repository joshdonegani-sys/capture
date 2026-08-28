package com.capture.engine.services

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.capture.engine.CaptureApplication
import com.capture.engine.R
import com.capture.engine.overlay.OverlayManager

@RequiresApi(Build.VERSION_CODES.N)
class CaptureTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val app = application as? CaptureApplication ?: return
        val settings = app.settingsRepository
        val newState = !settings.isOverlayEnabled
        settings.isOverlayEnabled = newState

        if (newState) {
            OverlayManager.getInstance(applicationContext).showToolbar()
        } else {
            OverlayManager.getInstance(applicationContext).hideAll()
        }

        updateTileState()
    }

    private fun updateTileState() {
        val app = application as? CaptureApplication ?: return
        val isEnabled = app.settingsRepository.isOverlayEnabled
        val tile = qsTile ?: return

        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.qs_tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isEnabled) "Active" else "Off"
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_hud)
        tile.contentDescription = getString(R.string.qs_tile_label)
        tile.updateTile()
    }

    companion object {
        fun requestTileUpdate(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    requestListeningState(
                        context,
                        ComponentName(context, CaptureTileService::class.java)
                    )
                } catch (e: Exception) {
                    // Ignored if service is not currently added
                }
            }
        }
    }
}
