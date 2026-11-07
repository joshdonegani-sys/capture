package com.capture.engine.car

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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

/**
 * Screen providing instant voice note capture for Android Auto.
 */
class CaptureCarScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    companion object {
        private const val TAG = "CaptureCarScreen"
    }

    private enum class State {
        IDLE,
        RECORDING,
        SAVING,
        SAVED,
        PERMISSION_REQUIRED
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null

    private var currentState: State = State.IDLE
    private var mediaRecorder: MediaRecorder? = null
    private var activeAudioFile: File? = null
    private var recordingStartTimeMs: Long = 0L
    private var elapsedSeconds: Int = 0
    private var lastRecordedDurationSeconds: Int = 0

    init {
        lifecycle.addObserver(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        scope.cancel()
        cleanupRecorder()
        super.onDestroy(owner)
    }

    override fun onGetTemplate(): Template {
        try {
            val micIcon = CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_mic)
            ).build()
            val stopIcon = CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_stop)
            ).build()
            val checkIcon = CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_check)
            ).build()

            return when (currentState) {
                State.IDLE -> {
                    val recordAction = Action.Builder()
                        .setTitle(carContext.getString(R.string.car_record_button))
                        .setIcon(micIcon)
                        .setOnClickListener {
                            handleRecordClicked()
                        }
                        .build()

                    MessageTemplate.Builder(carContext.getString(R.string.car_ready_desc))
                        .setTitle(carContext.getString(R.string.car_ready_title))
                        .setHeaderAction(Action.APP_ICON)
                        .setIcon(micIcon)
                        .addAction(recordAction)
                        .build()
                }

                State.RECORDING -> {
                    val minutes = elapsedSeconds / 60
                    val seconds = elapsedSeconds % 60
                    val timeString = String.format("%02d:%02d", minutes, seconds)

                    val stopAction = Action.Builder()
                        .setTitle(carContext.getString(R.string.car_stop_button))
                        .setIcon(stopIcon)
                        .setOnClickListener {
                            stopRecordingAndSave()
                        }
                        .build()

                    MessageTemplate.Builder("Listening to car microphone…\nDuration: $timeString")
                        .setTitle(carContext.getString(R.string.car_recording_title))
                        .setHeaderAction(Action.APP_ICON)
                        .setIcon(micIcon)
                        .addAction(stopAction)
                        .build()
                }

                State.SAVING -> {
                    MessageTemplate.Builder("Processing audio note and syncing…")
                        .setTitle("Saving Voice Note")
                        .setHeaderAction(Action.APP_ICON)
                        .setLoading(true)
                        .build()
                }

                State.SAVED -> {
                    val recordAnotherAction = Action.Builder()
                        .setTitle("Record Another")
                        .setIcon(micIcon)
                        .setOnClickListener {
                            handleRecordClicked()
                        }
                        .build()

                    val doneAction = Action.Builder()
                        .setTitle("Done")
                        .setOnClickListener {
                            currentState = State.IDLE
                            invalidate()
                        }
                        .build()

                    MessageTemplate.Builder(
                        "Captured note (${lastRecordedDurationSeconds}s).\nSaved to Capture Engine history & webhook."
                    )
                        .setTitle(carContext.getString(R.string.car_saved_title))
                        .setHeaderAction(Action.APP_ICON)
                        .setIcon(checkIcon)
                        .addAction(recordAnotherAction)
                        .addAction(doneAction)
                        .build()
                }

                State.PERMISSION_REQUIRED -> {
                    val grantAction = Action.Builder()
                        .setTitle("Grant Permission")
                        .setIcon(micIcon)
                        .setOnClickListener {
                            requestMicrophonePermission()
                        }
                        .build()

                    MessageTemplate.Builder(
                        "Microphone permission is required on your phone to capture voice notes."
                    )
                        .setTitle("Permission Needed")
                        .setHeaderAction(Action.APP_ICON)
                        .setIcon(micIcon)
                        .addAction(grantAction)
                        .build()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in onGetTemplate", t)
            return MessageTemplate.Builder("Tap below to record a voice note")
                .setTitle("Capture Engine")
                .setHeaderAction(Action.APP_ICON)
                .addAction(
                    Action.Builder()
                        .setTitle("Record")
                        .setOnClickListener { handleRecordClicked() }
                        .build()
                )
                .build()
        }
    }

    private fun handleRecordClicked() {
        val hasMic = ContextCompat.checkSelfPermission(
            carContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            requestMicrophonePermission()
            return
        }

        startRecording()
    }

    private fun requestMicrophonePermission() {
        carContext.requestPermissions(
            listOf(Manifest.permission.RECORD_AUDIO)
        ) { granted, rejected ->
            if (granted.contains(Manifest.permission.RECORD_AUDIO)) {
                CarToast.makeText(carContext, "Microphone access granted", CarToast.LENGTH_SHORT).show()
                startRecording()
            } else {
                currentState = State.PERMISSION_REQUIRED
                CarToast.makeText(carContext, "Microphone access is required", CarToast.LENGTH_LONG).show()
                invalidate()
            }
        }
    }

    private fun startRecording() {
        try {
            AudioRecordService.start(carContext)
            val audioFile = File(carContext.cacheDir, "car_audio_${System.currentTimeMillis()}.m4a")
            activeAudioFile = audioFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(carContext)
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
            recordingStartTimeMs = System.currentTimeMillis()
            elapsedSeconds = 0
            currentState = State.RECORDING

            CarToast.makeText(carContext, "Recording started", CarToast.LENGTH_SHORT).show()
            invalidate()

            timerJob?.cancel()
            timerJob = scope.launch {
                while (isActive && currentState == State.RECORDING) {
                    delay(1000L)
                    elapsedSeconds = ((System.currentTimeMillis() - recordingStartTimeMs) / 1000L).toInt()
                    invalidate()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start car audio recording", e)
            cleanupRecorder()
            CarToast.makeText(carContext, "Failed to start recording: ${e.localizedMessage}", CarToast.LENGTH_LONG).show()
            currentState = State.IDLE
            invalidate()
        }
    }

    private fun stopRecordingAndSave() {
        timerJob?.cancel()
        timerJob = null

        val finalDurationSec = ((System.currentTimeMillis() - recordingStartTimeMs) / 1000L).toInt().coerceAtLeast(1)
        lastRecordedDurationSeconds = finalDurationSec

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaRecorder", e)
        } finally {
            cleanupRecorder()
        }

        currentState = State.SAVING
        invalidate()

        val capturedFile = activeAudioFile
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val app = carContext.applicationContext as? CaptureApplication
                    val b64 = capturedFile?.let { file ->
                        if (file.exists() && file.length() > 0) {
                            val bytes = FileInputStream(file).use { it.readBytes() }
                            Base64.encodeToString(bytes, Base64.NO_WRAP)
                        } else null
                    }

                    if (app != null && b64 != null) {
                        val payload = PayloadBuilder.build(
                            type = "mic_audio",
                            source = "android_auto",
                            packageName = "com.capture.engine",
                            appName = "Capture Engine (Car)",
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
                        val payloadFile = File(capturesDir, "capture_car_${System.currentTimeMillis()}.json")
                        payloadFile.writeText(rawJson, Charsets.UTF_8)

                        val entity = CaptureEntity(
                            type = "mic_audio",
                            source = "android_auto",
                            packageName = "com.capture.engine",
                            appName = "Android Auto",
                            inputPreview = "[Car Voice Note (${finalDurationSec}s)]",
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
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving car capture payload", e)
                }
            }

            currentState = State.SAVED
            CarToast.makeText(carContext, "Note captured & saved!", CarToast.LENGTH_SHORT).show()
            invalidate()
        }
    }

    private fun cleanupRecorder() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaRecorder", e)
        }
        mediaRecorder = null
        AudioRecordService.stop(carContext)
    }
}
