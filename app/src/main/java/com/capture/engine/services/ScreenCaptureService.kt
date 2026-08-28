package com.capture.engine.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.capture.engine.CaptureApplication
import com.capture.engine.R
import com.capture.engine.ui.MainActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

class ScreenCaptureService : Service() {

    private val binder = LocalBinder()
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth = 1080
    private var screenHeight = 1920
    private var screenDensity = 420

    inner class LocalBinder : Binder() {
        fun getService(): ScreenCaptureService = this@ScreenCaptureService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        instance = this
        updateScreenMetrics()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        // If service is started without a valid token and no active projection exists, stop immediately
        if ((resultCode == 0 || resultData == null) && mediaProjection == null) {
            Log.w(TAG, "ScreenCaptureService started without valid MediaProjection token, stopping self")
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground for ScreenCaptureService", e)
            stopSelf()
            return START_NOT_STICKY
        }

        if (resultCode != 0 && resultData != null && mediaProjection == null) {
            try {
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val projection = projectionManager.getMediaProjection(resultCode, resultData)
                if (projection != null) {
                    // Android 14+ (API 34/35) REQUIRES registerCallback before createVirtualDisplay
                    projection.registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() {
                            super.onStop()
                            Log.d(TAG, "MediaProjection stopped by system")
                            cleanup()
                            stopSelf()
                        }
                    }, Handler(Looper.getMainLooper()))

                    mediaProjection = projection
                    _isRunningFlow.value = true
                    setupVirtualDisplay()
                    Log.d(TAG, "MediaProjection initialized successfully")
                } else {
                    Log.e(TAG, "Failed to obtain MediaProjection from projectionManager")
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing MediaProjection", e)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun updateScreenMetrics() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            screenWidth = metrics.bounds.width()
            screenHeight = metrics.bounds.height()
            screenDensity = resources.displayMetrics.densityDpi
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            screenWidth = dm.widthPixels
            screenHeight = dm.heightPixels
            screenDensity = dm.densityDpi
        }
    }

    @SuppressLint("WrongConstant")
    private fun setupVirtualDisplay() {
        try {
            updateScreenMetrics()
            imageReader?.close()
            virtualDisplay?.release()

            imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
            )

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "CaptureEngine-VD",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up VirtualDisplay", e)
        }
    }

    suspend fun captureScreenBase64(cropRect: Rect? = null): String? = withContext(Dispatchers.IO) {
        if (mediaProjection == null || imageReader == null) {
            Log.w(TAG, "MediaProjection or ImageReader not initialized")
            return@withContext null
        }

        val deferredImage = CompletableDeferred<Bitmap?>()

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * screenWidth

                    val bitmap = Bitmap.createBitmap(
                        screenWidth + rowPadding / pixelStride,
                        screenHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    val cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                    if (cleanBitmap != bitmap) {
                        bitmap.recycle()
                    }

                    // Handle Point Mode crop
                    val finalBitmap = if (cropRect != null && !cropRect.isEmpty) {
                        val safeLeft = cropRect.left.coerceIn(0, screenWidth - 1)
                        val safeTop = cropRect.top.coerceIn(0, screenHeight - 1)
                        val safeWidth = cropRect.width().coerceIn(1, screenWidth - safeLeft)
                        val safeHeight = cropRect.height().coerceIn(1, screenHeight - safeTop)

                        val cropped = Bitmap.createBitmap(cleanBitmap, safeLeft, safeTop, safeWidth, safeHeight)
                        if (cropped != cleanBitmap) {
                            cleanBitmap.recycle()
                        }
                        cropped
                    } else {
                        cleanBitmap
                    }

                    if (!deferredImage.isCompleted) {
                        deferredImage.complete(finalBitmap)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error acquiring screenshot bitmap", e)
                    if (!deferredImage.isCompleted) {
                        deferredImage.complete(null)
                    }
                } finally {
                    image.close()
                    reader.setOnImageAvailableListener(null, null)
                }
            }
        }, null)

        val bitmap = withTimeoutOrNull(2500) { deferredImage.await() }
        if (bitmap == null) {
            imageReader?.setOnImageAvailableListener(null, null)
            return@withContext null
        }

        try {
            val outputStream = ByteArrayOutputStream()
            // Per spec: 80% JPEG quality
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytes = outputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } finally {
            bitmap.recycle()
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CaptureApplication.SCREEN_CAPTURE_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.screen_capture_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun cleanup() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing virtualDisplay", e)
        }
        try {
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing imageReader", e)
        }
        try {
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping mediaProjection", e)
        }
        _isRunningFlow.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
        if (instance == this) {
            instance = null
        }
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        const val NOTIFICATION_ID = 2001
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow

        var instance: ScreenCaptureService? = null
            private set

        val isReady: Boolean
            get() = instance?.mediaProjection != null
    }
}
