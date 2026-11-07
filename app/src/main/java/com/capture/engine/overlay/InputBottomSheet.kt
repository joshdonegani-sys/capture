@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.capture.engine.overlay

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.MicOff
import androidx.core.content.ContextCompat
import com.capture.engine.CaptureApplication
import com.capture.engine.services.AudioRecordService
import com.capture.engine.ui.MainActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun InputBottomSheet(
    initialTab: String,
    showInputTypeTabs: Boolean = true,
    hasActiveSelection: Boolean = false,
    prefilledText: String? = null,
    sharedText: String? = null,
    sharedTitle: String? = null,
    attachmentBase64: String? = null,
    attachmentMime: String? = null,
    onDismiss: () -> Unit,
    onSend: (
        type: String,
        textContent: String?,
        audioBase64: String?,
        attBase64: String?,
        attMime: String?,
        sharedText: String?,
        sharedTitle: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val settings = remember { CaptureApplication.instance.settingsRepository }
    var selectedTabIndex by remember {
        mutableIntStateOf(
            when (initialTab) {
                "speech_to_text" -> 0
                "mic_audio" -> 1
                else -> 2 // text or clipboard_paste
            }
        )
    }

    LaunchedEffect(initialTab) {
        if (initialTab != "camera") {
            settings.lastUsedInputType = initialTab
        }
    }

    var isFullscreen by remember { mutableStateOf(false) }
    var activeSharedText by remember(sharedText) { mutableStateOf(sharedText) }
    var activeSharedTitle by remember(sharedTitle) { mutableStateOf(sharedTitle) }
    var activeAttachment by remember(attachmentBase64) { mutableStateOf(attachmentBase64) }
    var activeAttachmentMime by remember(attachmentMime) { mutableStateOf(attachmentMime) }

    // Text state
    val textFocusRequester = remember { FocusRequester() }
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

    // Microphone permission check state
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Refresh permission status on launch
    LaunchedEffect(Unit) {
        hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Speech Recognizer setup
    val speechRecognizer = remember {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            } else {
                Log.w("InputBottomSheet", "Speech recognition not available")
                null
            }
        } catch (e: Exception) {
            Log.e("InputBottomSheet", "Failed to init SpeechRecognizer", e)
            null
        }
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
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                // Ignore cleanup error
            }
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
            } catch (e: Exception) {
                // Ignore cleanup error
            }
            AudioRecordService.stop(context)
        }
    }

    fun startListening() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            Log.e("InputBottomSheet", "SpeechRecognizer is null")
            return
        }
        hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPermission) {
            Log.w("InputBottomSheet", "RECORD_AUDIO not granted")
            return
        }

        try {
            AudioRecordService.start(context)
            recognizer.cancel()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { isVoiceListening = true }
                override fun onBeginningOfSpeech() { isVoiceListening = true }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isVoiceListening = false
                    AudioRecordService.stop(context)
                }
                override fun onError(error: Int) {
                    Log.e("InputBottomSheet", "SpeechRecognizer error: $error")
                    isVoiceListening = false
                    AudioRecordService.stop(context)
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val result = matches[0]
                        voiceText = if (voiceText.isBlank()) result else "$voiceText $result"
                    }
                    isVoiceListening = false
                    AudioRecordService.stop(context)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val partial = matches[0]
                        voiceText = partial
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            recognizer.startListening(intent)
            isVoiceListening = true
        } catch (e: Exception) {
            Log.e("InputBottomSheet", "Failed to start listening", e)
            isVoiceListening = false
            AudioRecordService.stop(context)
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e("InputBottomSheet", "Failed to stop listening", e)
        }
        isVoiceListening = false
        AudioRecordService.stop(context)
    }

    fun startAudioRecording() {
        hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPermission) {
            Log.w("InputBottomSheet", "startAudioRecording: RECORD_AUDIO not granted")
            return
        }

        try {
            AudioRecordService.start(context)
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
            AudioRecordService.stop(context)
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
        } finally {
            AudioRecordService.stop(context)
        }
    }

    // Auto-start recording or focus if initial tab matches
    LaunchedEffect(initialTab, hasMicPermission) {
        if (hasMicPermission) {
            if (initialTab == "speech_to_text") {
                startListening()
            } else if (initialTab == "mic_audio") {
                startAudioRecording()
            }
        }
        if (initialTab == "text_snippet" || initialTab == "text") {
            delay(150L)
            try {
                textFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Focus request safely ignored if view detached
            }
        }
    }

    // Configuration & Screen Measurements
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightDp = configuration.screenHeightDp.dp
    val screenHeightPx = with(density) { screenHeightDp.toPx() }
    val coroutineScope = rememberCoroutineScope()

    // Dynamic inset and layout calculations
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeBottomDp = with(density) { imeBottomPx.toDp() }
    val statusBarTopPx = WindowInsets.statusBars.getTop(density)
    val statusBarTopDp = with(density) { statusBarTopPx.toDp() }

    val topSafetyMarginDp = statusBarTopDp.coerceAtLeast(36.dp)
    val horizontalPadding = 24.dp
    val nonContentHeightDp = horizontalPadding +
            (if (showInputTypeTabs) 56.dp else 0.dp) +
            (if (activeSharedText != null && activeAttachment != null) 108.dp else if (activeSharedText != null || activeAttachment != null) 54.dp else 0.dp) +
            16.dp + 48.dp + 16.dp

    val availableContentHeightDp = (screenHeightDp - imeBottomDp - topSafetyMarginDp - nonContentHeightDp).coerceAtLeast(90.dp)

    // Dimensions for Short and Tall heights
    val shortContentHeightPx = with(density) { 130.dp.coerceAtMost(availableContentHeightDp).toPx() }
    val tallContentHeightPx = with(density) { minOf(screenHeightDp * 0.52f, availableContentHeightDp).toPx() }

    // Dynamic content height animatable: tracks touch drags between short and tall
    val contentHeightPxAnim = remember { Animatable(shortContentHeightPx) }
    // Sheet translation Y animatable: used exclusively for entrance and exit (dismiss) off-screen
    val sheetTranslationY = remember { Animatable(screenHeightPx) }
    var isDismissing by remember { mutableStateOf(false) }

    // Synchronize content height when keyboard opens/closes while in fullscreen
    LaunchedEffect(tallContentHeightPx, isFullscreen) {
        if (isFullscreen && contentHeightPxAnim.value > tallContentHeightPx) {
            contentHeightPxAnim.animateTo(
                targetValue = tallContentHeightPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    // Current content height exposed to children composables, strictly bounded by available height
    val animatedContentHeightDp = with(density) { contentHeightPxAnim.value.toDp() }
    val effectiveContentHeightDp = animatedContentHeightDp.coerceIn(80.dp, availableContentHeightDp)

    // Start slide-up entrance animation immediately on launch: smooth FastOutSlowInEasing
    LaunchedEffect(Unit) {
        sheetTranslationY.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        )
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val composeView = LocalView.current

    // Dismiss with smooth slide-down animation
    fun dismissSheet(onComplete: () -> Unit = onDismiss) {
        if (isDismissing) return
        isDismissing = true
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(composeView.windowToken, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                composeView.windowInsetsController?.hide(android.view.WindowInsets.Type.ime())
            }
        } catch (e: Exception) {
            Log.e("InputBottomSheet", "Error hiding keyboard on dismiss", e)
        }
        coroutineScope.launch {
            sheetTranslationY.animateTo(
                targetValue = screenHeightPx,
                animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
            )
            onComplete()
        }
    }

    // Intercept back key: if in fullscreen, collapse to short version; otherwise dismiss
    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
            coroutineScope.launch {
                contentHeightPxAnim.animateTo(
                    targetValue = shortContentHeightPx,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        } else {
            dismissSheet()
        }
    }

    // Gesture detection on the drag handle / header:
    val thresholdPx = with(density) { 45.dp.toPx() }
    val dragGestureModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = {
                // Dragging started
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                coroutineScope.launch {
                    val currentTransY = sheetTranslationY.value
                    val currentHeight = contentHeightPxAnim.value

                    if (currentTransY > 0f) {
                        // Already translated downwards off-screen: continue translating
                        val nextTrans = (currentTransY + dragAmount).coerceAtLeast(0f)
                        sheetTranslationY.snapTo(nextTrans)
                    } else {
                        // Bottom edge stays stuck to the bottom of the screen (transY == 0f)!
                        // Dragging UP is negative dragAmount -> increases content height
                        val proposedHeight = currentHeight - dragAmount
                        if (proposedHeight > tallContentHeightPx) {
                            // Rubber band resistance past tall height
                            val excess = proposedHeight - tallContentHeightPx
                            contentHeightPxAnim.snapTo(tallContentHeightPx + (excess * 0.15f))
                        } else if (proposedHeight >= shortContentHeightPx) {
                            // Dragging between short and tall: bottom edge stays glued, height resizes!
                            contentHeightPxAnim.snapTo(proposedHeight)
                        } else {
                            // Dragged down past short height: pin height at short, and translate down off-screen!
                            contentHeightPxAnim.snapTo(shortContentHeightPx)
                            val excessDown = shortContentHeightPx - proposedHeight
                            sheetTranslationY.snapTo(excessDown)
                        }
                    }
                }
            },
            onDragEnd = {
                coroutineScope.launch {
                    val currentTransY = sheetTranslationY.value
                    val currentHeight = contentHeightPxAnim.value

                    if (currentTransY > thresholdPx * 1.2f) {
                        // Dragged down past short resting position into dismiss territory -> Dismiss!
                        dismissSheet()
                    } else if (currentTransY > 0f) {
                        // Released before dismiss threshold: spring back to short resting position
                        sheetTranslationY.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        contentHeightPxAnim.animateTo(
                            targetValue = shortContentHeightPx,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        isFullscreen = false
                    } else {
                        // Dragged between short and tall: snap to closest state
                        val midpoint = (shortContentHeightPx + tallContentHeightPx) * 0.5f
                        val targetHeight = if (currentHeight > midpoint) {
                            isFullscreen = true
                            tallContentHeightPx
                        } else {
                            isFullscreen = false
                            shortContentHeightPx
                        }

                        contentHeightPxAnim.animateTo(
                            targetValue = targetHeight,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        sheetTranslationY.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                }
            },
            onDragCancel = {
                coroutineScope.launch {
                    sheetTranslationY.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                    contentHeightPxAnim.animateTo(
                        targetValue = if (isFullscreen) tallContentHeightPx else shortContentHeightPx,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            }
        )
    }

    val scrimAlpha = if (hasActiveSelection) 0f else (0.45f * (1f - (sheetTranslationY.value / screenHeightPx))).coerceIn(0f, 0.45f)

    // Scrim / backdrop dismissable area: clicking outside discards and smoothly slides down
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
            .pointerInput(Unit) {
                detectTapGestures {
                    dismissSheet()
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Bottom Sheet Surface
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = sheetTranslationY.value.coerceAtLeast(0f)
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        // Consume all clicks inside sheet so they never dismiss
                    }
                }
                .imePadding(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 8.dp
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val localMaxContentHeightDp = (maxHeight - topSafetyMarginDp - nonContentHeightDp).coerceAtLeast(80.dp)
                val finalContentHeightDp = minOf(effectiveContentHeightDp, localMaxContentHeightDp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = horizontalPadding, end = horizontalPadding, top = 0.dp, bottom = 16.dp)
                ) {
                    // Drag handle area with vertical drag gestures (swipe up for full screen, swipe down for short/dismiss)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(horizontalPadding)
                            .then(dragGestureModifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }

                    // M3 Single-Select Segmented Buttons with proper outlineVariant border
                    if (showInputTypeTabs) {
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
                                settings.lastUsedInputType = "speech_to_text"
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
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
                                settings.lastUsedInputType = "mic_audio"
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
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
                                settings.lastUsedInputType = if (initialTab == "clipboard_paste") "clipboard_paste" else "text_snippet"
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
                }

                // Context / Preview Chips (Shared Text and Media Attachment)
                if (activeSharedText != null || activeAttachment != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (activeSharedText != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                tonalElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = "Shared Content",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (!activeSharedTitle.isNullOrBlank()) {
                                            Text(
                                                text = activeSharedTitle!!,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = activeSharedText!!.trim().replace(Regex("\\s+"), " "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = if (activeSharedTitle.isNullOrBlank()) 2 else 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove Shared Content",
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                activeSharedText = null
                                                activeSharedTitle = null
                                            },
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        if (activeAttachment != null) {
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
                }

                val requestPermissionAction = {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("REQUEST_AUDIO_PERMISSION", true)
                    }
                    context.startActivity(intent)
                }

                // Tab Content
                when (selectedTabIndex) {
                    0 -> {
                        // Dictate (Native text transcription preview - Read-only with Recording/Paused indicator)
                        if (!hasMicPermission) {
                            MicrophonePermissionCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(finalContentHeightDp),
                                onRequestPermission = requestPermissionAction
                            )
                        } else {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(finalContentHeightDp),
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
                                        if (isVoiceListening) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.tertiary)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Pause,
                                                contentDescription = "Paused",
                                                modifier = Modifier.size(10.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
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
                                    if (!hasMicPermission) {
                                        requestPermissionAction()
                                    } else {
                                        if (isVoiceListening) stopListening() else startListening()
                                    }
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
                                    dismissSheet {
                                        onSend("speech_to_text", voiceText.ifBlank { null }, null, activeAttachment, activeAttachmentMime, activeSharedText, activeSharedTitle)
                                    }
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

                        if (!hasMicPermission) {
                            MicrophonePermissionCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(finalContentHeightDp),
                                onRequestPermission = requestPermissionAction
                            )
                        } else {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(finalContentHeightDp),
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
                                            if (isAudioRecording && !isAudioPaused) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.tertiary)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Pause,
                                                    contentDescription = "Paused",
                                                    modifier = Modifier.size(10.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
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
                                    if (!hasMicPermission) {
                                        requestPermissionAction()
                                    } else {
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
                                    dismissSheet {
                                        onSend("mic_audio", null, b64, activeAttachment, activeAttachmentMime, activeSharedText, activeSharedTitle)
                                    }
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
                                .height(finalContentHeightDp)
                        ) {
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusRequester(textFocusRequester),
                                placeholder = { Text("Add instructions or prompt for agent...") },
                                shape = RoundedCornerShape(16.dp)
                            )

                            IconButton(
                                onClick = {
                                    val newFullscreen = !isFullscreen
                                    isFullscreen = newFullscreen
                                    coroutineScope.launch {
                                        contentHeightPxAnim.animateTo(
                                            targetValue = if (newFullscreen) tallContentHeightPx else shortContentHeightPx,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                },
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
                                    dismissSheet {
                                        onSend(type, textInput.ifBlank { null }, null, activeAttachment, activeAttachmentMime, activeSharedText, activeSharedTitle)
                                    }
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
}

@Composable
fun AudioWaveformVisualizer(
    isRecording: Boolean,
    mediaRecorder: MediaRecorder?,
    modifier: Modifier = Modifier
) {
    val currentMediaRecorder by rememberUpdatedState(mediaRecorder)
    val barCount = 28
    val amplitudes = remember {
        mutableStateListOf<Float>().apply {
            repeat(barCount) { add(0.06f) }
        }
    }

    LaunchedEffect(isRecording, currentMediaRecorder) {
        if (!isRecording) return@LaunchedEffect
        while (isActive) {
            val rawAmp = try {
                currentMediaRecorder?.maxAmplitude ?: 0
            } catch (e: Exception) {
                0
            }

            // Logarithmic / power-curve response with noise floor gating
            val normalizedAmp = if (rawAmp < 150) {
                0.06f
            } else {
                val ratio = ((rawAmp - 150).toFloat() / 26000f).coerceIn(0f, 1f)
                (0.06f + 0.94f * ratio.pow(0.42f)).coerceIn(0.06f, 1f)
            }

            if (amplitudes.size >= barCount) {
                amplitudes.removeAt(0)
            }
            amplitudes.add(normalizedAmp)
            delay(50L)
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

@Composable
private fun MicrophonePermissionCard(
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.MicOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Microphone Permission Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Allow microphone access to dictate or record audio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRequestPermission
            ) {
                Text("Grant Permission")
            }
        }
    }
}
