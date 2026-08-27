package com.capture.engine.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.capture.engine.CaptureApplication
import com.capture.engine.overlay.OverlayManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val app = context.applicationContext as? CaptureApplication ?: return
            val settings = app.settingsRepository

            if (settings.isOverlayEnabled) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
                    OverlayManager.getInstance(context.applicationContext).showToolbar()
                }
            }
        }
    }
}
