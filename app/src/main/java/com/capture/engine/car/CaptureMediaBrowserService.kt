package com.capture.engine.car

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import com.capture.engine.CaptureApplication
import com.capture.engine.R
import com.capture.engine.data.PayloadBuilder
import com.capture.engine.model.ActiveContextSources
import com.capture.engine.model.CaptureEntity
import com.capture.engine.model.DeliveryStatus
import com.capture.engine.network.WebhookClient
import com.capture.engine.services.AudioRecordService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Universal MediaBrowserService that integrates with Android Auto's native media framework.
 * This guarantees launcher visibility on vehicles without requiring Google Play Store distribution.
 */
class CaptureMediaBrowserService : MediaBrowserServiceCompat() {

    companion object {
        private const val TAG = "CaptureMediaBrowser"
        private const val MEDIA_ROOT_ID = "capture_root"
        private const val MEDIA_ID_RECORD = "action_record_voice_note"
        private const val MEDIA_ID_RECENT = "category_recent_notes"
        private const val ACTION_RECORD_TOGGLE = "com.capture.engine.ACTION_RECORD_TOGGLE"

        private const val CHANNEL_ID = "capture_media_channel"
        private const val NOTIFICATION_ID = 3001
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var mediaSession: MediaSessionCompat

    private var mediaRecorder: MediaRecorder? = null
    private var activeAudioFile: File? = null
    private var isRecording = false
    private var recordingStartTimeMs = 0L
    private var recordingTimerJob: Job? = null

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false

    private fun getResourceUri(resourceId: Int): Uri {
        val res = applicationContext.resources
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(res.getResourcePackageName(resourceId))
            .appendPath(res.getResourceTypeName(resourceId))
            .appendPath(res.getResourceEntryName(resourceId))
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        mediaSession = MediaSessionCompat(applicationContext, TAG).apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(sessionCallback)
            isActive = true
        }
        sessionToken = mediaSession.sessionToken

        updatePlaybackState(isRecording = false, isPlaying = false)
        updateMetadata("Capture Engine", "Ready to record voice notes")
    }

    override fun onDestroy() {
        stopRecording(save = false)
        stopPlayback()
        serviceScope.cancel()
        mediaSession.release()
        super.onDestroy()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        val extras = Bundle().apply {
            putBoolean("android.media.browse.SEARCH_SUPPORTED", false)
            putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
        }
        return BrowserRoot(MEDIA_ROOT_ID, extras)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaItem>>
    ) {
        try {
            when (parentId) {
                MEDIA_ROOT_ID -> {
                    val items = mutableListOf<MediaItem>()

                    // 1. Prominent Action Item: Instant Record Voice Note
                    val recordDesc = MediaDescriptionCompat.Builder()
                        .setMediaId(MEDIA_ID_RECORD)
                        .setTitle("🎙️ Tap to Record Voice Note")
                        .setSubtitle(if (isRecording) "Recording in progress • Tap to stop" else "Instant voice note capture")
                        .setIconUri(getResourceUri(R.drawable.ic_mic))
                        .build()
                    items.add(MediaItem(recordDesc, MediaItem.FLAG_PLAYABLE))

                    // 2. Folder: Recent Voice Notes
                    val recentDesc = MediaDescriptionCompat.Builder()
                        .setMediaId(MEDIA_ID_RECENT)
                        .setTitle("📁 Recent Voice Notes")
                        .setSubtitle("Listen to previous captures")
                        .setIconUri(getResourceUri(R.drawable.ic_check))
                        .build()
                    items.add(MediaItem(recentDesc, MediaItem.FLAG_BROWSABLE))

                    result.sendResult(items)
                }

                MEDIA_ID_RECENT -> {
                    result.detach()
                    serviceScope.launch(Dispatchers.IO) {
                        val app = applicationContext as? CaptureApplication
                        val recentItems = mutableListOf<MediaItem>()
                        try {
                            val captures = app?.database?.captureDao()?.getByStatus(DeliveryStatus.SUCCESS) ?: emptyList()
                            val audioCaptures = captures.filter { it.type == "mic_audio" }.take(10)

                            val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                            for (capture in audioCaptures) {
                                val timeStr = dateFormat.format(Date(capture.timestamp))
                                val title = capture.inputPreview ?: "Voice Note"
                                val desc = MediaDescriptionCompat.Builder()
                                    .setMediaId("note_${capture.id}")
                                    .setTitle(title)
                                    .setSubtitle(timeStr)
                                    .setIconUri(getResourceUri(R.drawable.ic_mic))
                                    .build()
                                recentItems.add(MediaItem(desc, MediaItem.FLAG_PLAYABLE))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading recent captures", e)
                        }

                        if (recentItems.isEmpty()) {
                            val emptyDesc = MediaDescriptionCompat.Builder()
                                .setMediaId("empty_notice")
                                .setTitle("No recent voice notes")
                                .setSubtitle("Record a note using the button above")
                                .build()
                            recentItems.add(MediaItem(emptyDesc, MediaItem.FLAG_PLAYABLE))
                        }

                        result.sendResult(recentItems)
                    }
                }

                else -> {
                    result.sendResult(emptyList())
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in onLoadChildren", t)
            result.sendResult(emptyList())
        }
    }

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            if (isRecording) {
                stopRecording(save = true)
            } else {
                startRecording()
            }
        }

        override fun onPause() {
            if (isRecording) {
                stopRecording(save = true)
            } else if (isPlaying) {
                stopPlayback()
            }
        }

        override fun onStop() {
            if (isRecording) {
                stopRecording(save = true)
            }
            stopPlayback()
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            when {
                mediaId == MEDIA_ID_RECORD -> {
                    if (isRecording) stopRecording(save = true) else startRecording()
                }
                mediaId?.startsWith("note_") == true -> {
                    val id = mediaId.removePrefix("note_").toLongOrNull()
                    if (id != null) {
                        playCapturedNote(id)
                    }
                }
                else -> {
                    onPlay()
                }
            }
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            if (action == ACTION_RECORD_TOGGLE) {
                if (isRecording) stopRecording(save = true) else startRecording()
            }
        }
    }

    private fun startRecording() {
        stopPlayback()
        try {
            AudioRecordService.start(this)
            val audioFile = File(cacheDir, "car_media_${System.currentTimeMillis()}.m4a")
            activeAudioFile = audioFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(audioFile.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            isRecording = true
            recordingStartTimeMs = System.currentTimeMillis()

            startServiceForeground("Recording Voice Note…", "Listening to car microphone • Tap Pause to Save")
            updatePlaybackState(isRecording = true, isPlaying = false)

            recordingTimerJob?.cancel()
            recordingTimerJob = serviceScope.launch {
                while (isActive && isRecording) {
                    delay(1000L)
                    val elapsed = ((System.currentTimeMillis() - recordingStartTimeMs) / 1000L).toInt()
                    val minutes = elapsed / 60
                    val seconds = elapsed % 60
                    val timeStr = String.format("%02d:%02d", minutes, seconds)
                    updateMetadata("Recording Voice Note ($timeStr)", "Listening to car microphone • Tap Pause to Save")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cleanupRecorder()
            updatePlaybackState(isRecording = false, isPlaying = false)
        }
    }

    private fun stopRecording(save: Boolean) {
        recordingTimerJob?.cancel()
        recordingTimerJob = null

        val finalDurationSec = ((System.currentTimeMillis() - recordingStartTimeMs) / 1000L).toInt().coerceAtLeast(1)
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaRecorder", e)
        } finally {
            cleanupRecorder()
        }

        isRecording = false
        stopForeground(STOP_FOREGROUND_REMOVE)

        if (save && activeAudioFile != null) {
            val capturedFile = activeAudioFile
            serviceScope.launch(Dispatchers.IO) {
                savePayload(capturedFile, finalDurationSec)
            }
            updateMetadata("Voice Note Saved! (${finalDurationSec}s)", "Saved to Capture Engine • Tap Play to record another")
        } else {
            updateMetadata("Voice Note Cancelled", "Tap Play to record")
        }

        updatePlaybackState(isRecording = false, isPlaying = false)
    }

    private fun playCapturedNote(captureId: Long) {
        stopRecording(save = false)
        stopPlayback()

        serviceScope.launch(Dispatchers.IO) {
            val app = applicationContext as? CaptureApplication ?: return@launch
            val entity = app.database.captureDao().getById(captureId) ?: return@launch
            val rawJson = entity.getRawJson(applicationContext)

            val payload = try {
                com.google.gson.JsonParser.parseString(rawJson).asJsonObject
            } catch (e: Exception) {
                null
            }
            val b64 = payload?.get("audio_base64")?.asString
            if (!b64.isNullOrBlank()) {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val tempFile = File(cacheDir, "temp_playback_$captureId.m4a")
                tempFile.writeBytes(bytes)

                withContext(Dispatchers.Main) {
                    try {
                        val player = MediaPlayer()
                        player.setDataSource(tempFile.absolutePath)
                        player.prepare()
                        player.setOnCompletionListener {
                            stopPlayback()
                        }
                        player.start()
                        mediaPlayer = player
                        isPlaying = true

                        val title = entity.inputPreview ?: "Voice Note #$captureId"
                        startServiceForeground(title, "Playing captured note")
                        updateMetadata(title, "Playing through car audio")
                        updatePlaybackState(isRecording = false, isPlaying = true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to play audio note", e)
                        stopPlayback()
                    }
                }
            }
        }
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer", e)
        }
        mediaPlayer = null
        isPlaying = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        updatePlaybackState(isRecording = false, isPlaying = false)
    }

    private suspend fun savePayload(file: File?, durationSec: Int) {
        val app = applicationContext as? CaptureApplication ?: return
        val b64 = file?.let { f ->
            if (f.exists() && f.length() > 0) {
                val bytes = FileInputStream(f).use { it.readBytes() }
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else null
        } ?: return

        val payload = PayloadBuilder.build(
            type = "mic_audio",
            source = "android_auto_media",
            packageName = "com.capture.engine",
            appName = "Capture Engine (Android Auto)",
            inputContent = null,
            activeContextSources = ActiveContextSources(
                screenGrab = false,
                appTree = false,
                highlightedText = false,
                media = false
            ),
            audioBase64 = b64
        )

        val rawJson = PayloadBuilder.toJson(payload)
        val capturesDir = File(app.filesDir, "captures").apply { mkdirs() }
        val payloadFile = File(capturesDir, "capture_car_media_${System.currentTimeMillis()}.json")
        payloadFile.writeText(rawJson, Charsets.UTF_8)

        val entity = CaptureEntity(
            type = "mic_audio",
            source = "android_auto_media",
            packageName = "com.capture.engine",
            appName = "Android Auto",
            inputPreview = "[Car Voice Note (${durationSec}s)]",
            hasScreenGrab = false,
            hasAppTree = false,
            hasHighlightedText = false,
            hasMedia = false,
            hasShareContext = false,
            hasAttachment = false,
            payloadFilePath = payloadFile.absolutePath,
            status = DeliveryStatus.SENDING
        )

        val dao = app.database.captureDao()
        val insertId = dao.insert(entity)

        val settings = app.settingsRepository
        val client = WebhookClient()
        val result = client.sendPayload(
            url = settings.webhookUrl,
            jsonPayload = rawJson,
            apiKey = settings.apiKey.ifBlank { null }
        )

        val updated = entity.copy(
            id = insertId,
            status = if (result.isSuccess) DeliveryStatus.SUCCESS else DeliveryStatus.ERROR,
            responseCode = result.statusCode,
            responseTimeMs = result.responseTimeMs,
            errorMessage = result.errorMessage
        )
        dao.update(updated)

        notifyChildrenChanged(MEDIA_ID_RECENT)
    }

    private fun updatePlaybackState(isRecording: Boolean, isPlaying: Boolean) {
        val state = when {
            isRecording -> PlaybackStateCompat.STATE_PLAYING
            isPlaying -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_PAUSED
        }

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    ACTION_RECORD_TOGGLE,
                    if (isRecording) "Stop & Save" else "Record Voice Note",
                    if (isRecording) R.drawable.ic_stop else R.drawable.ic_mic
                ).build()
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)

        mediaSession.setPlaybackState(stateBuilder.build())
    }

    private fun updateMetadata(title: String, subtitle: String) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Capture Engine")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, subtitle)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
            .build()
        mediaSession.setMetadata(metadata)
    }

    private fun startServiceForeground(title: String, text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    if (isRecording) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    else ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
    }

    private fun cleanupRecorder() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaRecorder", e)
        }
        mediaRecorder = null
        AudioRecordService.stop(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Capture Engine Car Audio",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
