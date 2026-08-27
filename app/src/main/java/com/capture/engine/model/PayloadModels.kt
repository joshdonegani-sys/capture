package com.capture.engine.model

import com.google.gson.annotations.SerializedName

/**
 * Main Capture Payload sent to the webhook
 */
data class CapturePayload(
    @SerializedName("type")
    val type: String, // "speech_to_text", "mic_audio", "clipboard_paste", "text_snippet", "share_target"

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @SerializedName("source")
    val source: String, // "floating_overlay", "system_share_sheet", "process_text"

    @SerializedName("package_name")
    val packageName: String? = null,

    @SerializedName("input_content")
    val inputContent: String? = null,

    @SerializedName("active_context_sources")
    val activeContextSources: ActiveContextSources,

    @SerializedName("media_context")
    val mediaContext: MediaContext? = null,

    @SerializedName("highlighted_text")
    val highlightedText: String? = null,

    @SerializedName("targeted_selection")
    val targetedSelection: String? = null,

    @SerializedName("app_tree_text")
    val appTreeText: String? = null,

    @SerializedName("screenshot_base64")
    val screenshotBase64: String? = null,

    @SerializedName("audio_base64")
    val audioBase64: String? = null,

    @SerializedName("attachment_base64")
    val attachmentBase64: String? = null,

    @SerializedName("attachment_mime")
    val attachmentMime: String? = null
)

data class ActiveContextSources(
    @SerializedName("screen_grab")
    val screenGrab: Boolean = false,

    @SerializedName("app_tree")
    val appTree: Boolean = false,

    @SerializedName("highlighted_text")
    val highlightedText: Boolean = false,

    @SerializedName("media")
    val media: Boolean = false
)

data class MediaContext(
    @SerializedName("platform")
    val platform: String? = null,

    @SerializedName("title")
    val title: String? = null,

    @SerializedName("channel_or_artist")
    val channelOrArtist: String? = null,

    @SerializedName("canonical_url")
    val canonicalUrl: String? = null,

    @SerializedName("timestamp_ms")
    val timestampMs: Long? = null,

    @SerializedName("playback_state")
    val playbackState: String? = null
)
