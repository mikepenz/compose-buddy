package dev.mikepenz.composebuddy.device.capture

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.PixelCopy
import android.view.View
import android.view.Window
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ScreenshotCapture {

    /**
     * Capture the window content as a base64-encoded PNG string.
     * Uses PixelCopy on API 26+; falls back to View.drawToBitmap on older APIs.
     */
    suspend fun captureBase64(window: Window, rootView: View): String {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            captureViaPixelCopy(window, rootView)
        } else {
            captureViaDrawToBitmap(rootView)
        }
        return bitmapToBase64(bitmap)
    }

    @Suppress("DEPRECATION")
    private suspend fun captureViaPixelCopy(window: Window, view: View): Bitmap =
        suspendCancellableCoroutine { cont ->
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val handler = Handler(Looper.getMainLooper())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PixelCopy.request(window, bitmap, { result ->
                    if (result == PixelCopy.SUCCESS) {
                        cont.resume(bitmap)
                    } else {
                        cont.resumeWithException(RuntimeException("PixelCopy failed: $result"))
                    }
                }, handler)
            }
        }

    @Suppress("DEPRECATION")
    private fun captureViaDrawToBitmap(view: View): Bitmap {
        view.isDrawingCacheEnabled = true
        view.buildDrawingCache()
        val cache = view.drawingCache
        val copy = Bitmap.createBitmap(cache)
        view.isDrawingCacheEnabled = false
        return copy
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
