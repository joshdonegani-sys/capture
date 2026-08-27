package com.capture.engine.services

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.capture.engine.CaptureApplication
import com.capture.engine.overlay.OverlayManager

@RequiresApi(Build.VERSION_CODES.N)
class CaptureTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val app = application as CaptureApplication
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
        tile.updateTile()
    }
}
