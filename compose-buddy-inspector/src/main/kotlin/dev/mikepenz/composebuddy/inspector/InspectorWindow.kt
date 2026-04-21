package dev.mikepenz.composebuddy.inspector

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.mikepenz.composebuddy.core.VERSION
import dev.mikepenz.composebuddy.inspector.ui.InspectorApp
import dev.mikepenz.composebuddy.inspector.ui.InspectorTheme
import io.github.kdroidfilter.nucleus.window.material.MaterialDecoratedWindow
import io.github.kdroidfilter.nucleus.window.material.MaterialTitleBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Launches the inspector as a Compose Desktop window with Nucleus decoration.
 */
fun launchInspector(
    session: InspectorSession,
    feed: InspectorFeed? = null,
    onRendererChanged: (RendererType) -> Unit = {},
) {
    val scope = CoroutineScope(Dispatchers.Default)
    feed?.let { f ->
        scope.launch {
            f.renders.collect { result ->
                session.addFrame(result)
            }
        }
    }

    application {
        var settings by remember { mutableStateOf(InspectorSettings()) }
        var showSpotlight by remember { mutableStateOf(false) }

        val systemDark = io.github.kdroidfilter.nucleus.darkmodedetector.isSystemInDarkMode()
        val isDark = when (settings.themeMode) {
            ThemeMode.AUTO -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
        LaunchedEffect(windowState) {
            androidx.compose.runtime.snapshotFlow { windowState.size }
                .collect { size ->
                    val minW = 360.dp
                    val minH = 240.dp
                    if (size.width < minW || size.height < minH) {
                        val w = if (size.width < minW) minW else size.width
                        val h = if (size.height < minH) minH else size.height
                        windowState.size = DpSize(w, h)
                    }
                }
        }

        InspectorTheme(darkTheme = isDark) {
            MaterialDecoratedWindow(
                onCloseRequest = ::exitApplication,
                title = "Compose Buddy Inspector v$VERSION",
                state = windowState,
                onPreviewKeyEvent = { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.K &&
                        (e.isMetaPressed || e.isCtrlPressed)
                    ) {
                        showSpotlight = !showSpotlight
                        true
                    } else false
                },
            ) {
                MaterialTitleBar {
                    Text(
                        "Compose Buddy Inspector — ${session.projectPath}",
                        fontSize = 13.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    )
                }
                InspectorApp(
                    session = session,
                    settings = settings,
                    onSettingsChanged = { settings = it },
                    onRendererChanged = onRendererChanged,
                    showSpotlight = showSpotlight,
                    onShowSpotlightChanged = { showSpotlight = it },
                )
            }
        }
    }
}
