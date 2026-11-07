package com.capture.engine

import com.capture.engine.data.PayloadBuilder
import com.capture.engine.model.ActiveContextSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureCarAppTest {

    @Test
    fun testAndroidAutoPayloadBuildingWithAudio() {
        val dummyAudioB64 = "UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQAAAAA="
        val payload = PayloadBuilder.build(
            type = "mic_audio",
            source = "android_auto",
            packageName = "com.capture.engine",
            appName = "Capture Engine (Android Auto)",
            inputContent = null,
            activeContextSources = ActiveContextSources(
                screenGrab = false,
                appTree = false,
                highlightedText = false,
                media = false
            ),
            audioBase64 = dummyAudioB64
        )

        assertEquals("mic_audio", payload.type)
        assertEquals("android_auto", payload.source)
        assertEquals("com.capture.engine", payload.packageName)
        assertEquals("Capture Engine (Android Auto)", payload.appName)
        assertNull(payload.inputContent)
        assertEquals(dummyAudioB64, payload.audioBase64)
        assertNull(payload.screenshotBase64)
        assertNull(payload.appTreeText)
        assertNull(payload.highlightedText)
        assertNull(payload.mediaContext)

        val json = PayloadBuilder.toJson(payload)
        assertTrue("JSON must contain source android_auto", json.contains("\"source\": \"android_auto\""))
        assertTrue("JSON must contain mic_audio", json.contains("\"type\": \"mic_audio\""))
        assertTrue("JSON must contain audioBase64", json.contains(dummyAudioB64))
    }

    @Test
    fun testAndroidAutoPayloadWithMediaContext() {
        val payload = PayloadBuilder.build(
            type = "mic_audio",
            source = "android_auto",
            packageName = "com.capture.engine",
            appName = "Android Auto",
            activeContextSources = ActiveContextSources(
                screenGrab = false,
                appTree = false,
                highlightedText = false,
                media = true
            ),
            mediaContext = com.capture.engine.model.MediaContext(
                platform = "com.spotify.music",
                title = "Driving Playlist",
                playbackState = "playing"
            ),
            audioBase64 = "audioBytes"
        )

        assertEquals("android_auto", payload.source)
        assertNotNull(payload.mediaContext)
        assertEquals("com.spotify.music", payload.mediaContext?.platform)
        assertEquals("Driving Playlist", payload.mediaContext?.title)

        val json = PayloadBuilder.toJson(payload)
        assertTrue(json.contains("\"platform\": \"com.spotify.music\""))
        assertTrue(json.contains("\"title\": \"Driving Playlist\""))
    }
}
