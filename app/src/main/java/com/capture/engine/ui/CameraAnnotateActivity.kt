package com.capture.engine.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.capture.engine.CaptureApplication
import com.capture.engine.overlay.OverlayManager
import com.capture.engine.ui.theme.CaptureEngineTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class DrawStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidthRatio: Float
)

class CameraAnnotateActivity : ComponentActivity() {

    private var currentPhotoFile: File? = null
    private var currentPhotoUri: Uri? = null

    private var capturedBitmap by mutableStateOf<Bitmap?>(null)
    private var isProcessing by mutableStateOf(false)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to capture photos", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoFile != null) {
            loadAndProcessBitmap(currentPhotoFile!!)
        } else {
            if (capturedBitmap == null) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            checkPermissionAndLaunch()
        }

        setContent {
            CaptureEngineTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    val bitmap = capturedBitmap
                    if (bitmap != null) {
                        AnnotationScreen(
                            bitmap = bitmap,
                            isProcessing = isProcessing,
                            onRetake = { checkPermissionAndLaunch() },
                            onCancel = { finish() },
                            onConfirm = { strokes ->
                                confirmAndAttach(bitmap, strokes)
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        try {
            val cameraDir = File(cacheDir, "camera_captures").apply { mkdirs() }
            val file = File(cameraDir, "capture_${System.currentTimeMillis()}.jpg")
            currentPhotoFile = file
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            currentPhotoUri = uri

            // Explicitly grant write URI permissions to matching camera packages
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val resInfoList = packageManager.queryIntentActivities(
                cameraIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resInfoList) {
                val pkgName = resolveInfo.activityInfo.packageName
                grantUriPermission(
                    pkgName,
                    uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch camera", e)
            Toast.makeText(this, "Failed to launch camera: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadAndProcessBitmap(file: File) {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

        val maxDim = 2048
        var inSampleSize = 1
        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight
        if (origH > maxDim || origW > maxDim) {
            val halfH = origH / 2
            val halfW = origW / 2
            while (halfH / inSampleSize >= maxDim && halfW / inSampleSize >= maxDim) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val rawBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return

        val matrix = Matrix()
        try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EXIF orientation", e)
        }

        val finalBitmap = if (!matrix.isIdentity) {
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true).also {
                if (it != rawBitmap) rawBitmap.recycle()
            }
        } else {
            rawBitmap
        }

        capturedBitmap = finalBitmap
    }

    private fun confirmAndAttach(originalBitmap: Bitmap, strokes: List<DrawStroke>) {
        isProcessing = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val flattened = if (strokes.isNotEmpty()) {
                    val resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
                    val canvas = android.graphics.Canvas(resultBitmap)
                    val bWidth = resultBitmap.width.toFloat()
                    val bHeight = resultBitmap.height.toFloat()

                    for (stroke in strokes) {
                        val paint = android.graphics.Paint().apply {
                            color = stroke.color.toArgb()
                            strokeWidth = (stroke.strokeWidthRatio * bWidth).coerceAtLeast(3f)
                            style = android.graphics.Paint.Style.STROKE
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            strokeJoin = android.graphics.Paint.Join.ROUND
                            isAntiAlias = true
                        }

                        val path = android.graphics.Path()
                        if (stroke.points.isNotEmpty()) {
                            val first = stroke.points[0]
                            path.moveTo(first.x * bWidth, first.y * bHeight)
                            for (i in 1 until stroke.points.size) {
                                val p0 = stroke.points[i - 1]
                                val p1 = stroke.points[i]
                                val midX = (p0.x + p1.x) / 2f * bWidth
                                val midY = (p0.y + p1.y) / 2f * bHeight
                                path.quadTo(p0.x * bWidth, p0.y * bHeight, midX, midY)
                            }
                            if (stroke.points.size == 1) {
                                path.lineTo(first.x * bWidth + 0.1f, first.y * bHeight + 0.1f)
                            }
                        }
                        canvas.drawPath(path, paint)
                    }
                    resultBitmap
                } else {
                    originalBitmap
                }

                val out = ByteArrayOutputStream()
                flattened.compress(Bitmap.CompressFormat.JPEG, 85, out)
                val base64Image = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

                currentPhotoFile?.delete()

                withContext(Dispatchers.Main) {
                    val overlayManager = OverlayManager.getInstance(this@CameraAnnotateActivity)
                    val settings = CaptureApplication.instance.settingsRepository
                    val lastUsed = settings.lastUsedInputType
                    val activeTab = if (lastUsed == "camera") "text_snippet" else lastUsed

                    if (overlayManager.canDrawOverlays()) {
                        overlayManager.openInputBottomSheet(
                            inputType = activeTab,
                            showInputTypeTabs = true,
                            attachmentBase64 = base64Image,
                            attachmentMime = "image/jpeg"
                        )
                    } else {
                        overlayManager.triggerCapture(
                            inputType = "camera",
                            inputContent = null,
                            attachmentBase64 = base64Image,
                            attachmentMime = "image/jpeg",
                            source = "camera_capture"
                        )
                    }
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error finalizing annotation image", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CameraAnnotateActivity, "Failed to attach photo", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    companion object {
        private const val TAG = "CameraAnnotateActivity"
    }
}

@Composable
fun AnnotationScreen(
    bitmap: Bitmap,
    isProcessing: Boolean,
    onRetake: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: (List<DrawStroke>) -> Unit
) {
    val strokes = remember { mutableStateListOf<DrawStroke>() }
    var currentStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val paletteColors = listOf(
        Color(0xFFFF1744),
        Color(0xFFFFD600),
        Color(0xFF00E676),
        Color(0xFF2979FF),
        Color(0xFFFF6D00),
        Color(0xFFFF4081),
        Color.White,
        Color.Black
    )

    var selectedColor by remember { mutableStateOf(paletteColors[0]) }

    val strokeWidthOptions = listOf(
        0.008f to "Fine",
        0.016f to "Medium",
        0.028f to "Thick"
    )
    var selectedStrokeWidthRatio by remember { mutableFloatStateOf(strokeWidthOptions[1].first) }

    val composeImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // --- TOP TOOLBAR ---
        Surface(
            color = Color(0xFF1E1E1E),
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onRetake) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retake",
                            tint = Color.White
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (strokes.isNotEmpty()) {
                                strokes.removeAt(strokes.lastIndex)
                            }
                        },
                        enabled = strokes.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (strokes.isNotEmpty()) Color.White else Color.Gray
                        )
                    }

                    IconButton(
                        onClick = { strokes.clear() },
                        enabled = strokes.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear All",
                            tint = if (strokes.isNotEmpty()) Color.White else Color.Gray
                        )
                    }

                    Button(
                        onClick = { onConfirm(strokes.toList()) },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Attach", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- DRAWING CANVAS AREA ---
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()

            val bmpWidth = bitmap.width.toFloat()
            val bmpHeight = bitmap.height.toFloat()

            val scale = minOf(containerWidth / bmpWidth, containerHeight / bmpHeight)
            val drawWidth = bmpWidth * scale
            val drawHeight = bmpHeight * scale
            val left = (containerWidth - drawWidth) / 2f
            val top = (containerHeight - drawHeight) / 2f

            val photoRect = Rect(left, top, left + drawWidth, top + drawHeight)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(photoRect, selectedColor, selectedStrokeWidthRatio) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (photoRect.contains(offset)) {
                                    val normX = ((offset.x - photoRect.left) / photoRect.width).coerceIn(0f, 1f)
                                    val normY = ((offset.y - photoRect.top) / photoRect.height).coerceIn(0f, 1f)
                                    currentStrokePoints = listOf(Offset(normX, normY))
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val pos = change.position
                                val normX = ((pos.x - photoRect.left) / photoRect.width).coerceIn(0f, 1f)
                                val normY = ((pos.y - photoRect.top) / photoRect.height).coerceIn(0f, 1f)
                                currentStrokePoints = currentStrokePoints + Offset(normX, normY)
                            },
                            onDragEnd = {
                                if (currentStrokePoints.isNotEmpty()) {
                                    strokes.add(
                                        DrawStroke(
                                            points = currentStrokePoints,
                                            color = selectedColor,
                                            strokeWidthRatio = selectedStrokeWidthRatio
                                        )
                                    )
                                    currentStrokePoints = emptyList()
                                }
                            },
                            onDragCancel = {
                                currentStrokePoints = emptyList()
                            }
                        )
                    }
            ) {
                drawImage(
                    image = composeImageBitmap,
                    dstOffset = IntOffset(photoRect.left.toInt(), photoRect.top.toInt()),
                    dstSize = IntSize(photoRect.width.toInt(), photoRect.height.toInt())
                )

                fun renderStroke(stroke: DrawStroke) {
                    if (stroke.points.isEmpty()) return
                    val path = Path()
                    val first = stroke.points[0]
                    path.moveTo(
                        photoRect.left + first.x * photoRect.width,
                        photoRect.top + first.y * photoRect.height
                    )
                    for (i in 1 until stroke.points.size) {
                        val p0 = stroke.points[i - 1]
                        val p1 = stroke.points[i]
                        val x0 = photoRect.left + p0.x * photoRect.width
                        val y0 = photoRect.top + p0.y * photoRect.height
                        val x1 = photoRect.left + p1.x * photoRect.width
                        val y1 = photoRect.top + p1.y * photoRect.height
                        path.quadraticTo(x0, y0, (x0 + x1) / 2f, (y0 + y1) / 2f)
                    }
                    if (stroke.points.size == 1) {
                        path.lineTo(
                            photoRect.left + first.x * photoRect.width + 0.1f,
                            photoRect.top + first.y * photoRect.height + 0.1f
                        )
                    }
                    val strokePx = (stroke.strokeWidthRatio * photoRect.width).coerceAtLeast(3f)
                    drawPath(
                        path = path,
                        color = stroke.color,
                        style = Stroke(
                            width = strokePx,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                for (s in strokes) {
                    renderStroke(s)
                }

                if (currentStrokePoints.isNotEmpty()) {
                    renderStroke(
                        DrawStroke(
                            points = currentStrokePoints,
                            color = selectedColor,
                            strokeWidthRatio = selectedStrokeWidthRatio
                        )
                    )
                }
            }
        }

        // --- BOTTOM PALETTE & BRUSH CONTROLS ---
        Surface(
            color = Color(0xFF1E1E1E),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Thickness",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.LightGray
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        strokeWidthOptions.forEach { (ratio, label) ->
                            val isSelected = selectedStrokeWidthRatio == ratio
                            val dotSize = when (label) {
                                "Fine" -> 6.dp
                                "Medium" -> 12.dp
                                else -> 18.dp
                            }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable { selectedStrokeWidthRatio = ratio },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(dotSize)
                                        .clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.LightGray)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    paletteColors.forEach { color ->
                        val isSelected = selectedColor == color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color(0x66FFFFFF),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (color == Color.White || color == Color(0xFFFFD600)) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
