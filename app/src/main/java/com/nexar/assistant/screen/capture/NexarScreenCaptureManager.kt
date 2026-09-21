package com.nexar.assistant.screen.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.nexar.assistant.utils.NexarLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NexarScreenCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "NexarScreenCapture"
        private const val VIRTUAL_DISPLAY_NAME = "NexarCapture"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val projectionManager =
        context.getSystemService(MediaProjectionManager::class.java)

    fun start(resultCode: Int, data: Intent) {
        stop() // Clean up any existing capture

        try {
            val metrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val display = context.display
                display?.getRealMetrics(metrics) ?: run {
                    metrics.setTo(context.resources.displayMetrics)
                }
            } else {
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)
            }

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            mediaProjection?.registerCallback(projectionCallback, null)

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null, null
            )

            _isCapturing.value = true
            NexarLogger.d(TAG, "Screen capture started: ${width}x${height}")
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Failed to start screen capture", e)
            stop()
        }
    }

    fun captureFrame(): Bitmap? {
        if (!_isCapturing.value) return null
        return try {
            val image = imageReader?.acquireLatestImage() ?: return null
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Failed to capture frame", e)
            null
        }
    }

    fun stop() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
            mediaProjection = null
            _isCapturing.value = false
            NexarLogger.d(TAG, "Screen capture stopped")
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Error stopping screen capture", e)
        }
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            NexarLogger.d(TAG, "MediaProjection stopped by system")
            _isCapturing.value = false
            virtualDisplay?.release()
            virtualDisplay = null
        }
    }
}
