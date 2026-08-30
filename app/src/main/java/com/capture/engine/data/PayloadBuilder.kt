package com.capture.engine.data

import com.capture.engine.model.ActiveContextSources
import com.capture.engine.model.CapturePayload
import com.capture.engine.model.MediaContext
import com.capture.engine.model.ShareContext
import com.google.gson.Gson
import com.google.gson.GsonBuilder

object PayloadBuilder {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .serializeNulls()
        .create()

    private val urlRegex = "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)".toRegex()

    fun build(
        type: String,
        source: String,
        packageName: String?,
        appName: String? = null,
        inputContent: String? = null,
        activeContextSources: ActiveContextSources,
        mediaContext: MediaContext? = null,
        shareContext: ShareContext? = null,
        highlightedText: String? = null,
        targetedSelection: String? = null,
        appTreeText: String? = null,
        screenshotBase64: String? = null,
        audioBase64: String? = null,
        attachmentBase64: String? = null,
        attachmentMime: String? = null
    ): CapturePayload {
        val cleanedShareContext = shareContext?.let { sc ->
            val text = sc.text?.ifBlank { null }
            val title = sc.title?.ifBlank { null }
            val mimeType = sc.mimeType?.ifBlank { null }
            var url = sc.url?.ifBlank { null }

            if (url == null && text != null) {
                val match = urlRegex.find(text)
                if (match != null) {
                    url = match.value
                }
            }

            if (text == null && url == null && title == null && mimeType == null) {
                null
            } else {
                ShareContext(
                    text = text,
                    url = url,
                    title = title,
                    mimeType = mimeType
                )
            }
        }

        return CapturePayload(
            type = type,
            timestamp = System.currentTimeMillis(),
            source = source,
            packageName = packageName,
            appName = appName,
            inputContent = inputContent?.ifBlank { null },
            activeContextSources = activeContextSources,
            mediaContext = if (activeContextSources.media) mediaContext else null,
            shareContext = cleanedShareContext,
            highlightedText = if (activeContextSources.highlightedText) highlightedText?.ifBlank { null } else null,
            targetedSelection = targetedSelection,
            appTreeText = if (activeContextSources.appTree) appTreeText?.ifBlank { null } else null,
            screenshotBase64 = if (activeContextSources.screenGrab) screenshotBase64 else null,
            audioBase64 = audioBase64,
            attachmentBase64 = attachmentBase64,
            attachmentMime = attachmentMime
        )
    }

    fun toJson(payload: CapturePayload): String {
        return gson.toJson(payload)
    }
}
