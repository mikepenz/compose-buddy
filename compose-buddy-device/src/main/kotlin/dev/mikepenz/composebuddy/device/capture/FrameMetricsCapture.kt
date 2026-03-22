package dev.mikepenz.composebuddy.device.capture

import android.os.Build
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.RequiresApi
import dev.mikepenz.composebuddy.device.model.BuddyFrameTiming
import kotlinx.coroutines.channels.Channel
import android.os.Handler
import android.os.Looper

class FrameMetricsCapture(private val window: Window) {

    private val timings = Channel<BuddyFrameTiming>(capacity = Channel.CONFLATED)

    @RequiresApi(Build.VERSION_CODES.N)
    fun start() {
        val handler = Handler(Looper.getMainLooper())
        window.addOnFrameMetricsAvailableListener({ _, frameMetrics, _ ->
            val timing = BuddyFrameTiming(
                vsyncTimestamp = if (Build.VERSION.SDK_INT >= 31)
                    frameMetrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP) / 1_000_000
                else System.currentTimeMillis(),
                compositionMs = frameMetrics.getMetric(FrameMetrics.ANIMATION_DURATION) / 1_000_000.0,
                layoutMs = frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) / 1_000_000.0,
                drawMs = frameMetrics.getMetric(FrameMetrics.DRAW_DURATION) / 1_000_000.0,
            )
            timings.trySend(timing)
        }, handler)
    }

    suspend fun awaitNextFrame(): BuddyFrameTiming = timings.receive()

    fun stop() {
        timings.close()
    }
}
