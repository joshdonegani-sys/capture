package com.capture.engine.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.capture.engine.overlay.OverlayManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareTargetActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == Intent.ACTION_SEND) {
            val type = intent.type
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val streamUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }

            lifecycleScope.launch {
                var attachmentBase64: String? = null
                if (streamUri != null) {
                    attachmentBase64 = withContext(Dispatchers.IO) {
                        try {
                            contentResolver.openInputStream(streamUri)?.use { input ->
                                val bytes = input.readBytes()
                                Base64.encodeToString(bytes, Base64.NO_WRAP)
                            }
                        } catch (e: Exception) {
                            Log.e("ShareTargetActivity", "Error reading shared media stream", e)
                            null
                        }
                    }
                }

                val overlayManager = OverlayManager.getInstance(this@ShareTargetActivity)
                if (overlayManager.canDrawOverlays()) {
                    overlayManager.openInputBottomSheet(
                        inputType = if (!sharedText.isNullOrBlank()) "text" else "speech_to_text",
                        prefilledText = sharedText,
                        attachmentBase64 = attachmentBase64,
                        attachmentMime = type
                    )
                } else {
                    // Send directly if overlay permission not active
                    overlayManager.triggerCapture(
                        inputType = "share_target",
                        inputContent = sharedText,
                        attachmentBase64 = attachmentBase64,
                        attachmentMime = type,
                        source = "system_share_sheet"
                    )
                }
                finish()
            }
            return
        }

        finish()
    }
}
