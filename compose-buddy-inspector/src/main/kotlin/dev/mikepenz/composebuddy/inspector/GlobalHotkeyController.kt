package dev.mikepenz.composebuddy.inspector

import co.touchlab.kermit.Logger
import io.github.kdroidfilter.nucleus.globalhotkey.GlobalHotKeyManager
import io.github.kdroidfilter.nucleus.globalhotkey.HotKeyModifier
import io.github.kdroidfilter.nucleus.globalhotkey.plus
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

/**
 * Thin wrapper around Nucleus' [GlobalHotKeyManager] that owns the inspector's
 * command-palette hotkey (⌘K on macOS, Ctrl+K on Windows/Linux). Mirrors the
 * GlobalHotkeyController used in the agent-buddy app so the two stacks share
 * the same registration semantics.
 *
 * Returns `false` from [tryRegister] when the native subsystem isn't available
 * — the caller should then fall back to the in-window key handler exposed by
 * `Window(onKeyEvent = …)` or `Modifier.onPreviewKeyEvent`.
 *
 * The Nucleus callback fires on a native thread, so we marshal back to the AWT
 * event dispatch thread before touching Compose state.
 */
class GlobalHotkeyController {
    private val log = Logger.withTag("InspectorHotkey")

    @Volatile private var handle: Long = -1L
    @Volatile private var initialized: Boolean = false

    fun tryRegister(onTrigger: () -> Unit): Boolean {
        if (!GlobalHotKeyManager.isAvailable) {
            log.i { "Global hotkeys not available; falling back to in-window handler" }
            return false
        }
        if (!initialized) {
            if (!GlobalHotKeyManager.initialize()) {
                log.w { "GlobalHotKeyManager.initialize() failed: ${GlobalHotKeyManager.lastError}" }
                return false
            }
            initialized = true
        }
        val isMac = System.getProperty("os.name").lowercase().contains("mac")
        val modifiers: Int = if (isMac) 0 + HotKeyModifier.META else 0 + HotKeyModifier.CONTROL
        val id = GlobalHotKeyManager.register(
            keyCode = KeyEvent.VK_K,
            modifiers = modifiers,
        ) { _, _ ->
            SwingUtilities.invokeLater { onTrigger() }
        }
        if (id < 0L) {
            log.w { "Failed to register hotkey: ${GlobalHotKeyManager.lastError}" }
            return false
        }
        handle = id
        log.i { "Registered ${if (isMac) "Cmd+K" else "Ctrl+K"} (handle=$id)" }
        return true
    }

    fun shutdown() {
        if (handle >= 0L) {
            GlobalHotKeyManager.unregister(handle); handle = -1L
        }
        if (initialized) {
            GlobalHotKeyManager.shutdown(); initialized = false
        }
    }
}
