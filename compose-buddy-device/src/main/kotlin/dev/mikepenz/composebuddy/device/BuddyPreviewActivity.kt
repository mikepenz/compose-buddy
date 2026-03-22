package dev.mikepenz.composebuddy.device

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import dev.mikepenz.composebuddy.device.capture.ColorSampler
import dev.mikepenz.composebuddy.device.capture.ScreenshotCapture
import dev.mikepenz.composebuddy.device.capture.SemanticsCapture
import dev.mikepenz.composebuddy.device.capture.SlotTableCapture
import dev.mikepenz.composebuddy.device.model.BuddyCommand
import dev.mikepenz.composebuddy.device.model.BuddyFrame
import dev.mikepenz.composebuddy.device.model.BuddyFrameTiming
import dev.mikepenz.composebuddy.device.model.PreviewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "BuddyPreviewActivity"

/**
 * Activity that hosts a single @Preview composable specified via intent extra "preview".
 * Captures screenshots and semantics on demand when a `rerender` command arrives,
 * and sends the initial frame after the first layout.
 */
class BuddyPreviewActivity : ComponentActivity() {

    private var server: BuddyWebSocketServer? = null
    private var currentPreviewFqn: String? = null
    private val registry: BuddyPreviewRegistry = BuddyPreviewRegistry.load()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var frameCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fqn = intent.getStringExtra("preview") ?: run {
            Log.e(TAG, "No 'preview' intent extra — finishing")
            finish()
            return
        }
        val port = intent.getIntExtra("port", 7890)
        currentPreviewFqn = fqn
        Log.i(TAG, "Starting preview: $fqn on port $port")

        val srv = BuddyWebSocketServer(port = port, onCommand = { cmd -> handleCommand(cmd) })
        srv.start()
        BuddyWebSocketServer.instance = srv
        server = srv

        showPreview(fqn, buildPreviewConfig())
    }

    private fun buildPreviewConfig(): PreviewConfig {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return PreviewConfig(
            widthDp = resources.configuration.screenWidthDp,
            heightDp = resources.configuration.screenHeightDp,
            densityDpi = resources.displayMetrics.densityDpi,
            darkMode = nightMode == Configuration.UI_MODE_NIGHT_YES,
        )
    }

    private fun showPreview(fqn: String, config: PreviewConfig) {
        val entry = registry.previews[fqn] ?: run {
            Log.e(TAG, "Preview '$fqn' not found in registry. Available: ${registry.previews.keys}")
            finish()
            return
        }
        setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                entry.composable(config)
            }
        }

        // Capture after layout completes
        window.decorView.post {
            window.decorView.postDelayed({ captureAndBroadcast() }, 300)
        }
    }

    /**
     * Capture the current screen and broadcast as a BuddyFrame via WebSocket.
     */
    private fun captureAndBroadcast() {
        val fqn = currentPreviewFqn ?: return
        val rootView = window.decorView.rootView

        if (rootView.width == 0 || rootView.height == 0) {
            Log.w(TAG, "View not laid out yet, skipping capture")
            return
        }

        scope.launch {
            try {
                Log.d(TAG, "Capturing frame $frameCounter (${rootView.width}x${rootView.height})")
                val imageBase64 = ScreenshotCapture.captureBase64(window, rootView)

                val contentViewGroup = findViewById<android.view.ViewGroup>(android.R.id.content)
                val androidComposeView = contentViewGroup?.let { findAndroidComposeView(it) }

                val semanticsOwner = try {
                    if (androidComposeView != null) {
                        val owner = try {
                            androidComposeView::class.java.getDeclaredMethod("getSemanticsOwner")
                                .invoke(androidComposeView)
                        } catch (_: NoSuchMethodException) {
                            // Fallback: check ViewRootForTest interface
                            androidComposeView::class.java.interfaces
                                .firstOrNull { it.simpleName.contains("ViewRootForTest") }
                                ?.getDeclaredMethod("getSemanticsOwner")?.invoke(androidComposeView)
                        }
                        owner as? androidx.compose.ui.semantics.SemanticsOwner
                    } else {
                        Log.d(TAG, "AndroidComposeView not found in view tree")
                        null
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "getSemanticsOwner not available: ${e.message}")
                    null
                }

                val semanticsTree = if (semanticsOwner != null) {
                    SemanticsCapture.captureTree(semanticsOwner)
                } else {
                    SemanticsCapture.buildNode(
                        id = 0, name = "Root", role = null,
                        left = 0, top = 0, right = rootView.width, bottom = rootView.height,
                        contentDescription = null, testTag = null,
                        mergedSemantics = emptyMap(), actions = emptyList(),
                        bgColor = null, fgColor = null, children = emptyList(),
                    )
                }

                // Enrich with color sampling
                val bitmapBytes = Base64.decode(imageBase64, Base64.NO_WRAP)
                val bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.size)
                val enrichedSemantics = if (bitmap != null) {
                    ColorSampler.enrich(semanticsTree, bitmap)
                } else {
                    semanticsTree
                }

                // Enrich with slot table properties (typography, colors, shapes, source locations)
                val finalSemantics = if (androidComposeView != null) {
                    SlotTableCapture.enrichSemantics(androidComposeView, enrichedSemantics, resources.displayMetrics.densityDpi)
                } else {
                    enrichedSemantics
                }

                val frame = BuddyFrame.RenderFrame(
                    frameId = frameCounter++,
                    timestamp = System.currentTimeMillis(),
                    preview = fqn,
                    image = imageBase64,
                    semantics = finalSemantics,
                    slotTable = emptyList(),
                    recompositions = emptyMap(),
                    frameTiming = BuddyFrameTiming(
                        vsyncTimestamp = System.currentTimeMillis(),
                        compositionMs = 0.0, layoutMs = 0.0, drawMs = 0.0,
                    ),
                    densityDpi = resources.displayMetrics.densityDpi,
                )
                Log.i(TAG, "Broadcasting frame ${frame.frameId} (image: ${imageBase64.length} chars)")
                server?.broadcast(frame) ?: Log.w(TAG, "Server is null, cannot broadcast")
            } catch (e: Exception) {
                Log.e(TAG, "Frame capture failed", e)
            }
        }
    }

    private fun handleCommand(cmd: BuddyCommand) {
        runOnUiThread {
            when (cmd) {
                is BuddyCommand.Rerender -> {
                    Log.i(TAG, "Rerender command received")
                    captureAndBroadcast()
                }
                is BuddyCommand.Navigate -> {
                    Log.i(TAG, "Navigate command: ${cmd.preview}")
                    currentPreviewFqn = cmd.preview
                    showPreview(cmd.preview, buildPreviewConfig())
                }
                is BuddyCommand.SetConfig -> {
                    val current = buildPreviewConfig()
                    val updated = current.copy(
                        darkMode = cmd.darkMode ?: current.darkMode,
                        fontScale = cmd.fontScale ?: current.fontScale,
                        locale = cmd.locale ?: current.locale,
                    )
                    showPreview(currentPreviewFqn ?: return@runOnUiThread, updated)
                }
                is BuddyCommand.Tap -> {
                    val downTime = SystemClock.uptimeMillis()
                    val event = MotionEvent.obtain(
                        downTime, downTime,
                        MotionEvent.ACTION_DOWN, cmd.x.toFloat(), cmd.y.toFloat(), 0,
                    )
                    window.decorView.dispatchTouchEvent(event)
                    event.recycle()
                }
                is BuddyCommand.RequestPreviewList -> {
                    val previews = registry.previews.keys.toList()
                    Log.i(TAG, "Sending preview list (${previews.size} entries)")
                    server?.broadcast(BuddyFrame.PreviewListFrame(previews))
                }
            }
        }
    }

    /** Recursively find the AndroidComposeView inside a view hierarchy. */
    private fun findAndroidComposeView(vg: android.view.ViewGroup): android.view.View? {
        for (i in 0 until vg.childCount) {
            val child = vg.getChildAt(i)
            if (child::class.java.simpleName.contains("AndroidComposeView")) return child
            if (child is android.view.ViewGroup) {
                findAndroidComposeView(child)?.let { return it }
            }
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        BuddyWebSocketServer.instance = null
    }
}
