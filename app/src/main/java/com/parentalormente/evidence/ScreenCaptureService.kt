package com.parentalormente.evidence

import android.app.Activity
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.parentalormente.ParentalApp
import com.parentalormente.R
import java.io.File
import java.io.FileOutputStream

/**
 * Foreground service for capturing screenshots as evidence.
 *
 * Requires one-time user consent via MediaProjection API.
 * Captures the screen when triggered (on CRITICAL/HIGH incidents)
 * and stores the screenshot in the evidence directory.
 *
 * This is the parent's tool — installed on the child's device with
 * the child's knowledge. Not spyware. Protection.
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCapture"
        private const val NOTIFICATION_ID = 1002
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_INCIDENT_ID = "incident_id"

        private var mediaProjection: MediaProjection? = null

        /**
         * Initialize with MediaProjection consent result.
         * Call this once after the user approves screen capture.
         */
        fun initProjection(context: Context, resultCode: Int, data: Intent) {
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mgr.getMediaProjection(resultCode, data)
            Log.i(TAG, "MediaProjection initialized")
        }

        fun hasProjection(): Boolean = mediaProjection != null

        /**
         * Capture a screenshot and save it as evidence.
         */
        fun capture(context: Context, incidentId: Long) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_INCIDENT_ID, incidentId)
            }
            context.startForegroundService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        val incidentId = intent?.getLongExtra(EXTRA_INCIDENT_ID, -1) ?: -1
        if (incidentId == -1L || mediaProjection == null) {
            Log.w(TAG, "No projection or incident ID, stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        captureScreen(incidentId)
        return START_NOT_STICKY
    }

    private fun captureScreen(incidentId: Long) {
        val projection = mediaProjection ?: return

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        val virtualDisplay: VirtualDisplay = projection.createVirtualDisplay(
            "POM_Evidence",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null, null
        )

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener

            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)

                // Crop to actual screen size (remove padding)
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                if (cropped != bitmap) bitmap.recycle()

                saveScreenshot(cropped, incidentId)
                cropped.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Screenshot capture failed", e)
            } finally {
                image.close()
                virtualDisplay.release()
                imageReader.close()
                stopSelf()
            }
        }, null)
    }

    private fun saveScreenshot(bitmap: Bitmap, incidentId: Long) {
        val evidenceDir = File(filesDir, "evidence/screenshots").also { it.mkdirs() }
        val file = File(evidenceDir, "screenshot_${incidentId}_${System.currentTimeMillis()}.png")

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        Log.i(TAG, "Screenshot saved: ${file.name} (${file.length() / 1024}KB)")
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, ParentalApp.CHANNEL_MONITOR)
            .setContentTitle("Capturing evidence")
            .setSmallIcon(R.drawable.ic_shield)
            .setSilent(true)
            .build()
    }
}
