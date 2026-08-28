package com.capture.engine.data

import com.capture.engine.model.ActiveContextSources
import com.capture.engine.model.CapturePayload
import com.capture.engine.model.MediaContext
import com.google.gson.Gson
import com.google.gson.GsonBuilder

object PayloadBuilder {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .serializeNulls()
        .create()

    fun build(
        type: String,
        source: String,
        packageName: String?,
        inputContent: String? = null,
        activeContextSources: ActiveContextSources,
        mediaContext: MediaContext? = null,
        highlightedText: String? = null,
        targetedSelection: String? = null,
        appTreeText: String? = null,
        screenshotBase64: String? = null,
        audioBase64: String? = null,
        attachmentBase64: String? = null,
        attachmentMime: String? = null
    ): CapturePayload {
        return CapturePayload(
            type = type,
            timestamp = System.currentTimeMillis(),
            source = source,
            packageName = packageName,
            inputContent = inputContent?.ifBlank { null },
            activeContextSources = activeContextSources,
            mediaContext = if (activeContextSources.media) mediaContext else null,
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
