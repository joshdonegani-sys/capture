package com.capture.engine

import com.capture.engine.data.PayloadBuilder
import com.capture.engine.model.ActiveContextSources
import com.capture.engine.model.MediaContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadBuilderTest {

    @Test
    fun `build respects active context toggles and filters fields`() {
        val sources = ActiveContextSources(
            screenGrab = false,
            appTree = true,
            highlightedText = false,
            media = false
        )

        val payload = PayloadBuilder.build(
            type = "speech_to_text",
            source = "floating_overlay",
            packageName = "com.android.chrome",
            inputContent = "Search for latest AI papers",
            activeContextSources = sources,
            mediaContext = MediaContext(title = "Podcast Episode"),
            highlightedText = "Some text",
            targetedSelection = null,
            appTreeText = "  • [TextView] text=\"Search\"",
            screenshotBase64 = "iVBORw0KGgoAAAANSUhEUgAA...",
            audioBase64 = null
        )

        assertEquals("speech_to_text", payload.type)
        assertEquals("floating_overlay", payload.source)
        assertEquals("com.android.chrome", payload.packageName)
        assertEquals("Search for latest AI papers", payload.inputContent)

        // screenGrab is false -> screenshotBase64 must be null
        assertNull(payload.screenshotBase64)

        // highlightedText toggle is false -> must be null
        assertNull(payload.highlightedText)

        // media toggle is false -> must be null
        assertNull(payload.mediaContext)

        // appTree toggle is true -> must be populated
        assertNotNull(payload.appTreeText)
        assertTrue(payload.appTreeText!!.contains("TextView"))
    }

    @Test
    fun `build includes all fields when all context toggles are true`() {
        val sources = ActiveContextSources(
            screenGrab = true,
            appTree = true,
            highlightedText = true,
            media = true
        )

        val payload = PayloadBuilder.build(
            type = "mic_audio",
            source = "floating_overlay",
            packageName = "com.google.android.youtube",
            inputContent = null,
            activeContextSources = sources,
            mediaContext = MediaContext(
                platform = "com.google.android.youtube",
                title = "Live Stream",
                playbackState = "playing"
            ),
            highlightedText = "Video Description",
            targetedSelection = "100,200,500,600",
            appTreeText = "  • [ViewGroup]",
            screenshotBase64 = "base64image==",
            audioBase64 = "base64audio=="
        )

        assertEquals("mic_audio", payload.type)
        assertNotNull(payload.screenshotBase64)
        assertNotNull(payload.appTreeText)
        assertNotNull(payload.highlightedText)
        assertNotNull(payload.mediaContext)
        assertEquals("100,200,500,600", payload.targetedSelection)
        assertEquals("base64audio==", payload.audioBase64)

        // Verify JSON serialization
        val json = PayloadBuilder.toJson(payload)
        assertTrue(json.contains("\"type\": \"mic_audio\""))
        assertTrue(json.contains("\"screenshot_base64\": \"base64image==\""))
        assertTrue(json.contains("\"playback_state\": \"playing\""))
    }

    @Test
    fun `build converts blank strings to null when enabled`() {
        val sources = ActiveContextSources(
            screenGrab = true,
            appTree = true,
            highlightedText = true,
            media = true
        )

        val payload = PayloadBuilder.build(
            type = "text_snippet",
            source = "process_text",
            packageName = "com.example.app",
            inputContent = "   ",
            activeContextSources = sources,
            mediaContext = null,
            highlightedText = "   ",
            targetedSelection = null,
            appTreeText = "   \n  \t  ",
            screenshotBase64 = null
        )

        assertNull(payload.inputContent)
        assertNull(payload.highlightedText)
        assertNull(payload.appTreeText)
        assertNull(payload.screenshotBase64)
        assertNull(payload.mediaContext)
    }

    @Test
    fun `build ignores all context fields when all active sources are false`() {
        val sources = ActiveContextSources(
            screenGrab = false,
            appTree = false,
            highlightedText = false,
            media = false
        )

        val payload = PayloadBuilder.build(
            type = "clipboard_paste",
            source = "floating_overlay",
            packageName = "com.example.app",
            inputContent = "Pasted text",
            activeContextSources = sources,
            mediaContext = MediaContext(title = "Should be excluded"),
            highlightedText = "Should be excluded",
            targetedSelection = "10,20,30,40",
            appTreeText = "Should be excluded",
            screenshotBase64 = "Should be excluded"
        )

        assertEquals("clipboard_paste", payload.type)
        assertEquals("Pasted text", payload.inputContent)
        assertNull(payload.screenshotBase64)
        assertNull(payload.appTreeText)
        assertNull(payload.highlightedText)
        assertNull(payload.mediaContext)
        // Targeted selection is preserved
        assertEquals("10,20,30,40", payload.targetedSelection)
    }

    @Test
    fun `build isolates share_context from input_content and serializes properly`() {
        val sources = ActiveContextSources(
            screenGrab = false,
            appTree = false,
            highlightedText = false,
            media = false
        )

        val shareContext = com.capture.engine.model.ShareContext(
            text = "Full article content about quantum computing breakthroughs.",
            url = "https://example.com/quantum",
            title = "Quantum Milestone",
            mimeType = "text/plain"
        )

        val payload = PayloadBuilder.build(
            type = "share_target",
            source = "system_share_sheet",
            packageName = "com.android.chrome",
            appName = "Chrome",
            inputContent = "Summarize the 3 key takeaways",
            activeContextSources = sources,
            shareContext = shareContext
        )

        assertEquals("share_target", payload.type)
        assertEquals("system_share_sheet", payload.source)
        assertEquals("Summarize the 3 key takeaways", payload.inputContent)
        assertNotNull(payload.shareContext)
        assertEquals("Full article content about quantum computing breakthroughs.", payload.shareContext?.text)
        assertEquals("https://example.com/quantum", payload.shareContext?.url)
        assertEquals("Quantum Milestone", payload.shareContext?.title)

        val json = PayloadBuilder.toJson(payload)
        assertTrue(json.contains("\"share_context\""))
        assertTrue(json.contains("\"input_content\": \"Summarize the 3 key takeaways\""))
        assertTrue(json.contains("\"title\": \"Quantum Milestone\""))
    }

    @Test
    fun `build auto-extracts url into share_context when url present in text`() {
        val sources = ActiveContextSources()

        val shareContext = com.capture.engine.model.ShareContext(
            text = "Interesting read here: https://deepmind.google/research for AI agents",
            title = "Deepmind Research"
        )

        val payload = PayloadBuilder.build(
            type = "text_snippet",
            source = "floating_overlay",
            packageName = "com.example.app",
            activeContextSources = sources,
            shareContext = shareContext
        )

        assertNotNull(payload.shareContext)
        assertEquals("https://deepmind.google/research", payload.shareContext?.url)
        assertEquals("Deepmind Research", payload.shareContext?.title)
    }

    @Test
    fun `build converts blank share_context to null`() {
        val sources = ActiveContextSources()

        val shareContext = com.capture.engine.model.ShareContext(
            text = "   ",
            url = "",
            title = "  ",
            mimeType = null
        )

        val payload = PayloadBuilder.build(
            type = "text_snippet",
            source = "floating_overlay",
            packageName = "com.example.app",
            activeContextSources = sources,
            shareContext = shareContext
        )

        assertNull(payload.shareContext)
    }
}
