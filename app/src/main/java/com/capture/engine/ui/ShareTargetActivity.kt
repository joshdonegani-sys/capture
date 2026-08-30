package com.capture.engine.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import com.capture.engine.model.ShareContext
import com.capture.engine.overlay.OverlayManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareTargetActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == Intent.ACTION_SEND) {
            val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            val sharedTitle = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString()

            val streamUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            } ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri

            val resolvedMime = streamUri?.let { contentResolver.getType(it) } ?: intent.type

            lifecycleScope.launch {
                var attachmentBase64: String? = null
                if (streamUri != null) {
                    attachmentBase64 = withContext(Dispatchers.IO) {
                        try {
                            if (resolvedMime?.startsWith("image/", ignoreCase = true) == true) {
                                contentResolver.openInputStream(streamUri)?.use { input ->
                                    val buffer = input.readBytes()
                                    val options = BitmapFactory.Options().apply {
                                        inJustDecodeBounds = true
                                    }
                                    BitmapFactory.decodeByteArray(buffer, 0, buffer.size, options)

                                    var inSampleSize = 1
                                    val maxDim = 2048
                                    if (options.outHeight > maxDim || options.outWidth > maxDim) {
                                        val halfHeight = options.outHeight / 2
                                        val halfWidth = options.outWidth / 2
                                        while (halfHeight / inSampleSize >= maxDim && halfWidth / inSampleSize >= maxDim) {
                                            inSampleSize *= 2
                                        }
                                    }
                                    val decodeOptions = BitmapFactory.Options().apply {
                                        this.inSampleSize = inSampleSize
                                    }
                                    val bitmap = BitmapFactory.decodeByteArray(buffer, 0, buffer.size, decodeOptions)
                                    if (bitmap != null) {
                                        val out = ByteArrayOutputStream()
                                        val format = if (resolvedMime.contains("png", ignoreCase = true)) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                                        bitmap.compress(format, 85, out)
                                        bitmap.recycle()
                                        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                                    } else {
                                        Base64.encodeToString(buffer, Base64.NO_WRAP)
                                    }
                                }
                            } else {
                                contentResolver.openInputStream(streamUri)?.use { input ->
                                    val bytes = input.readBytes()
                                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                                }
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
                        inputType = "text",
                        showInputTypeTabs = true,
                        sharedText = sharedText,
                        sharedTitle = sharedTitle,
                        attachmentBase64 = attachmentBase64,
                        attachmentMime = resolvedMime
                    )
                } else {
                    // Send directly if overlay permission not active
                    val shareContext = if (!sharedText.isNullOrBlank() || !sharedTitle.isNullOrBlank()) {
                        ShareContext(
                            text = sharedText,
                            title = sharedTitle,
                            mimeType = resolvedMime
                        )
                    } else null

                    overlayManager.triggerCapture(
                        inputType = "share_target",
                        inputContent = null,
                        shareContext = shareContext,
                        attachmentBase64 = attachmentBase64,
                        attachmentMime = resolvedMime,
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
