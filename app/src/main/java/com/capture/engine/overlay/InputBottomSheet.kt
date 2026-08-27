@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.capture.engine.overlay

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun InputBottomSheet(
    initialTab: String,
    prefilledText: String? = null,
    attachmentBase64: String? = null,
    attachmentMime: String? = null,
    onDismiss: () -> Unit,
    onSend: (type: String, textContent: String?, audioBase64: String?, attBase64: String?, attMime: String?) -> Unit
) {
    val context = LocalContext.current
    var selectedTabIndex by remember {
        mutableIntStateOf(
            when (initialTab) {
                "speech_to_text" -> 0
                "mic_audio" -> 1
                else -> 2 // text or clipboard_paste
            }
        )
    }

    var isFullscreen by remember { mutableStateOf(false) }
    var activeAttachment by remember { mutableStateOf(attachmentBase64) }
    var activeAttachmentMime by remember { mutableStateOf(attachmentMime) }

    // Text state
    var textInput by remember {
        mutableStateOf(
            prefilledText ?: if (initialTab == "clipboard_paste") {
                val clipManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipManager?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            } else ""
        )
    }

    // Voice transcription state
    var voiceText by remember { mutableStateOf("") }
    var isVoiceListening by remember { mutableStateOf(false) }

    // Audio recording state
    var isAudioRecording by remember { mutableStateOf(false) }
    var isAudioPaused by remember { mutableStateOf(false) }
    var recordingDurationSeconds by remember { mutableIntStateOf(0) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }

    // Speech Recognizer setup
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    // Timer for audio recording
    LaunchedEffect(isAudioRecording, isAudioPaused) {
        if (isAudioRecording && !isAudioPaused) {
            while (isActive) {
                delay(1000L)
                recordingDurationSeconds++
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
            } catch (e: Exception) {
                // Ignore cleanup error
            }
        }
    }

    fun startListening() {
        if (speechRecognizer == null) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isVoiceListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isVoiceListening = false }
            override fun onError(error: Int) { isVoiceListening = false }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val result = matches[0]
                    voiceText = if (voiceText.isBlank()) result else "$voiceText $result"
                }
                isVoiceListening = false
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val partial = matches[0]
                    if (voiceText.isBlank()) {
                        voiceText = partial
                    }
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        isVoiceListening = false
    }

    fun startAudioRecording() {
        try {
            val file = File(context.cacheDir, "capture_audio_${System.currentTimeMillis()}.m4a")
            audioFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            isAudioRecording = true
            isAudioPaused = false
            recordingDurationSeconds = 0
        } catch (e: Exception) {
            Log.e("InputBottomSheet", "Failed to start audio recording", e)
        }
    }

    fun stopAudioRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isAudioRecording = false
            isAudioPaused = false
        } catch (e: Exception) {
            Log.e("InputBottomSheet", "Failed to stop audio recording", e)
        }
    }

    // Auto-start recording if initial tab matches
    LaunchedEffect(initialTab) {
        if (initialTab == "speech_to_text") {
            startListening()
        } else if (initialTab == "mic_audio") {
            startAudioRecording()
        }
    }

    // Scrim / background dismissable area: clicking outside discards and dismisses
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Bottom Sheet Surface
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .then(if (isFullscreen) Modifier.fillMaxHeight(0.92f) else Modifier)
                .clickable(enabled = false) {}, // Prevent dismiss click inside sheet
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // M3 Single-Select Segmented Buttons with proper outlineVariant border
                val segmentedBorder = SegmentedButtonDefaults.borderStroke(
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                val segmentedColors = SegmentedButtonDefaults.colors(
                    activeBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = selectedTabIndex == 0,
                        onClick = {
                            selectedTabIndex = 0
                            if (isAudioRecording) stopAudioRecording()
                            if (!isVoiceListening) startListening()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        border = segmentedBorder,
                        colors = segmentedColors,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = { Text("Dictate") }
                    )
                    SegmentedButton(
                        selected = selectedTabIndex == 1,
                        onClick = {
                            selectedTabIndex = 1
                            if (isVoiceListening) stopListening()
                            if (!isAudioRecording) startAudioRecording()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        border = segmentedBorder,
                        colors = segmentedColors,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = { Text("Record") }
                    )
                    SegmentedButton(
                        selected = selectedTabIndex == 2,
                        onClick = {
                            selectedTabIndex = 2
                            stopListening()
                            stopAudioRecording()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        border = segmentedBorder,
                        colors = segmentedColors,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = { Text("Type") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Attachment Thumbnail Chip (if shared via Share Sheet)
                if (activeAttachment != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            tonalElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Attachment",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Attached Media (${activeAttachmentMime ?: "File"})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove Attachment",
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable {
                                            activeAttachment = null
                                            activeAttachmentMime = null
                                        },
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                // Tab Content
                when (selectedTabIndex) {
                    0 -> {
                        // Dictate (Native text transcription preview - Read-only with Recording/Paused indicator)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                // Status indicator header inside container (matches Record box)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isVoiceListening)
                                                    MaterialTheme.colorScheme.tertiary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                    )
                                    Text(
                                        text = if (isVoiceListening) "Listening" else "Paused",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isVoiceListening)
                                            MaterialTheme.colorScheme.tertiary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    if (voiceText.isBlank()) {
                                        Text(
                                            text = "Transcribed speech will appear here...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    } else {
                                        Text(
                                            text = voiceText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Bottom Action Bar: Icon-only Pause/Resume button + Send button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    if (isVoiceListening) stopListening() else startListening()
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isVoiceListening) Icons.Default.Pause else Icons.Default.Circle,
                                    contentDescription = if (isVoiceListening) "Pause" else "Resume"
                                )
                            }
                            Button(
                                onClick = {
                                    stopListening()
                                    onSend("speech_to_text", voiceText, null, activeAttachment, activeAttachmentMime)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send")
                            }
                        }
                    }
                    1 -> {
                        // Record (Audio Recording with Waveform, Timestamp, and Recording/Paused Indicator)
                        val minutes = recordingDurationSeconds / 60
                        val seconds = recordingDurationSeconds % 60
                        val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                // Status & Timestamp header inside container
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isAudioRecording && !isAudioPaused)
                                                        MaterialTheme.colorScheme.tertiary
                                                    else
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                        )
                                        Text(
                                            text = if (isAudioRecording && !isAudioPaused) "Recording" else "Paused",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAudioRecording && !isAudioPaused)
                                                MaterialTheme.colorScheme.tertiary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = formattedTime,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Centered Real-time Waveform Visualizer
                                AudioWaveformVisualizer(
                                    isRecording = isAudioRecording && !isAudioPaused,
                                    mediaRecorder = mediaRecorder,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .align(Alignment.Center)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Bottom Action Bar: Icon-only Pause/Resume button + Send button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    if (isAudioRecording && !isAudioPaused) {
                                        try {
                                            mediaRecorder?.pause()
                                            isAudioPaused = true
                                        } catch (e: Exception) {
                                            Log.e("InputBottomSheet", "Failed to pause audio recording", e)
                                        }
                                    } else {
                                        if (!isAudioRecording) {
                                            startAudioRecording()
                                        } else {
                                            try {
                                                mediaRecorder?.resume()
                                                isAudioPaused = false
                                            } catch (e: Exception) {
                                                Log.e("InputBottomSheet", "Failed to resume audio recording", e)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAudioRecording && !isAudioPaused) Icons.Default.Pause else Icons.Default.Circle,
                                    contentDescription = if (isAudioRecording && !isAudioPaused) "Pause" else "Resume"
                                )
                            }
                            Button(
                                onClick = {
                                    stopAudioRecording()
                                    val b64 = audioFile?.let { file ->
                                        if (file.exists()) {
                                            val bytes = FileInputStream(file).use { it.readBytes() }
                                            Base64.encodeToString(bytes, Base64.NO_WRAP)
                                        } else null
                                    }
                                    onSend("mic_audio", null, b64, activeAttachment, activeAttachmentMime)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send")
                            }
                        }
                    }
                    2 -> {
                        // Type (& Paste) with bottom-right Unfold More / Unfold Less icon
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isFullscreen) Modifier.fillMaxHeight(0.75f) else Modifier.height(130.dp))
                        ) {
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                modifier = Modifier.fillMaxSize(),
                                placeholder = { Text("Add instructions or prompt for agent...") },
                                shape = RoundedCornerShape(16.dp)
                            )

                            IconButton(
                                onClick = { isFullscreen = !isFullscreen },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 4.dp, bottom = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                    contentDescription = if (isFullscreen) "Unfold Less" else "Unfold More",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Bottom Action Bar: Send button
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    val type = if (initialTab == "clipboard_paste") "clipboard_paste" else "text_snippet"
                                    onSend(type, textInput, null, activeAttachment, activeAttachmentMime)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioWaveformVisualizer(
    isRecording: Boolean,
    mediaRecorder: MediaRecorder?,
    modifier: Modifier = Modifier
) {
    val barCount = 28
    val amplitudes = remember {
        mutableStateListOf<Float>().apply {
            repeat(barCount) { add(0.12f) }
        }
    }
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isRecording) {
        while (isActive) {
            if (isRecording) {
                phase += 0.28f
                val rawAmp = try {
                    mediaRecorder?.maxAmplitude ?: 0
                } catch (e: Exception) {
                    0
                }
                val normalizedAmp = (rawAmp.toFloat() / 20000f).coerceIn(0.08f, 1f)

                if (amplitudes.size >= barCount) {
                    amplitudes.removeAt(0)
                }

                // Blend real microphone amplitude with subtle animated harmonics so waveform is vibrant even in emulator
                val sim = 0.15f + 0.45f * abs(sin(phase.toDouble()).toFloat())
                val dynamicAmp = maxOf(normalizedAmp, sim)
                amplitudes.add(dynamicAmp)
            } else {
                // Settle smoothly to baseline when paused
                if (amplitudes.any { it > 0.1f }) {
                    for (i in amplitudes.indices) {
                        amplitudes[i] = maxOf(0.08f, amplitudes[i] * 0.82f)
                    }
                }
            }
            delay(65L)
        }
    }

    val activeBarColor = MaterialTheme.colorScheme.primary
    val pausedBarColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val maxHeight = size.height
        val spacing = 3.5.dp.toPx()
        val barWidth = ((totalWidth - ((barCount - 1) * spacing)) / barCount).coerceAtLeast(2.dp.toPx())

        for (i in 0 until minOf(barCount, amplitudes.size)) {
            val amp = amplitudes[i]
            val barHeight = (maxHeight * amp).coerceIn(4.dp.toPx(), maxHeight)
            val x = i * (barWidth + spacing)
            val y = (maxHeight - barHeight) / 2f

            drawRoundRect(
                color = if (isRecording) activeBarColor else pausedBarColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
