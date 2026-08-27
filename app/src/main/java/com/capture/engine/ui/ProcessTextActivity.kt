package com.capture.engine.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.capture.engine.overlay.OverlayManager
import kotlinx.coroutines.launch

class ProcessTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()

        if (!selectedText.isNullOrBlank()) {
            val overlayManager = OverlayManager.getInstance(this)
            if (overlayManager.canDrawOverlays()) {
                // Open bottom sheet allowing the user to add extra context or send
                overlayManager.openInputBottomSheet(
                    inputType = "text",
                    prefilledText = selectedText
                )
            } else {
                // Trigger capture directly in background
                lifecycleScope.launch {
                    overlayManager.triggerCapture(
                        inputType = "text_snippet",
                        source = "process_text",
                        prefilledHighlightedText = selectedText
                    )
                    finish()
                }
                return
            }
        }

        finish()
    }
}
