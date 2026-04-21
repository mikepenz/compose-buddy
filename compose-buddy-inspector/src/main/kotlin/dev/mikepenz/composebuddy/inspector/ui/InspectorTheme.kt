package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

// ─────────────────────────────────────────────────────────────────────────────
// Design tokens — direct mapping of the CSS custom properties in
// /tmp/compose-buddy-handoff/compose-buddy/project/Agent Buddy.html (`:root` and
// `[data-theme="dark"]`). Names mirror the CSS variables on purpose so a
// designer can grep one file and find the corresponding Compose token.
// ─────────────────────────────────────────────────────────────────────────────

@Immutable
data class InspectorPalette(
    // Background scale
    val bgDesktop: Color,
    val bgWindow: Color,
    val bgChrome: Color,
    val bgPanel: Color,
    val bgPanelMuted: Color,
    val bgCanvas: Color,
    val bgPreview: Color,
    val bgHover: Color,
    val bgActive: Color,
    val bgSelected: Color,
    val bgSelectedStrong: Color,
    // Foreground / ink scale
    val fg1: Color,
    val fg2: Color,
    val fg3: Color,
    val fg4: Color,
    val fgInverse: Color,
    // Hairlines
    val line1: Color,
    val line2: Color,
    val line3: Color,
    // Accents
    val accent: Color,
    val accentSoft: Color,
    val accentFg: Color,
    val warn: Color,
    // Selection / measurement guides
    val ruleX: Color,
    val ruleY: Color,
    val ruleLabel: Color,
    val selStroke: Color,
    val selFill: Color,
    // Type-glyph colors (S/L/T/E badges in the component tree)
    val glyphScreen: Color,
    val glyphScreenBg: Color,
    val glyphLayout: Color,
    val glyphLayoutBg: Color,
    val glyphText: Color,
    val glyphTextBg: Color,
    val glyphElement: Color,
    val glyphElementBg: Color,
    val isDark: Boolean,
)

// `:root` (light theme) — copied 1:1 from Agent Buddy.html
private val InspectorPaletteLight = InspectorPalette(
    bgDesktop = Color(0xFFE8E6E1),
    bgWindow = Color(0xFFFAFAF8),
    bgChrome = Color(0xFFF4F3EF),
    bgPanel = Color(0xFFFFFFFF),
    bgPanelMuted = Color(0xFFF6F5F2),
    bgCanvas = Color(0xFFEEEDE8),
    bgPreview = Color(0xFFFFFFFF),
    bgHover = Color(0x0A000000),       // rgba(0,0,0,0.04)
    bgActive = Color(0x0F000000),      // rgba(0,0,0,0.06)
    bgSelected = Color(0xFFEEF3FF),
    bgSelectedStrong = Color(0xFF1E6BFF),
    fg1 = Color(0xFF17171A),
    fg2 = Color(0xFF44444B),
    fg3 = Color(0xFF8A8A93),
    fg4 = Color(0xFFB5B5BC),
    fgInverse = Color(0xFFFFFFFF),
    line1 = Color(0x0F000000),         // rgba(0,0,0,0.06)
    line2 = Color(0x1A000000),         // rgba(0,0,0,0.10)
    line3 = Color(0x24000000),         // rgba(0,0,0,0.14)
    accent = Color(0xFF1E6BFF),
    accentSoft = Color(0xFFE7EFFF),
    accentFg = Color(0xFFFFFFFF),
    warn = Color(0xFFE8A100),
    ruleX = Color(0xFFD2B3E8),
    ruleY = Color(0xFFD2B3E8),
    ruleLabel = Color(0xFF9A5FB8),
    selStroke = Color(0xFF1E6BFF),
    selFill = Color(0x1A1E6BFF),       // rgba(30,107,255,0.10)
    glyphScreen = Color(0xFF7C3AED),
    glyphScreenBg = Color(0x1F7C3AED), // rgba(124,58,237,0.12)
    glyphLayout = Color(0xFF0EA5A0),
    glyphLayoutBg = Color(0x1F0EA5A0), // rgba(14,165,160,0.12)
    glyphText = Color(0xFFE87800),
    glyphTextBg = Color(0x24E87800),   // rgba(232,120,0,0.14)
    glyphElement = Color(0xFF6B7280),
    glyphElementBg = Color(0x246B7280),
    isDark = false,
)

// `[data-theme="dark"]` block — copied 1:1.
private val InspectorPaletteDark = InspectorPalette(
    bgDesktop = Color(0xFF1A1A1C),
    bgWindow = Color(0xFF1C1C1F),
    bgChrome = Color(0xFF242428),
    bgPanel = Color(0xFF1C1C1F),
    bgPanelMuted = Color(0xFF202024),
    bgCanvas = Color(0xFF131315),
    bgPreview = Color(0xFFF4F3EF),
    bgHover = Color(0x0DFFFFFF),       // rgba(255,255,255,0.05)
    bgActive = Color(0x14FFFFFF),      // rgba(255,255,255,0.08)
    bgSelected = Color(0xFF18274A),
    bgSelectedStrong = Color(0xFF4B8CFF),
    fg1 = Color(0xFFF4F4F6),
    fg2 = Color(0xFFB5B5BC),
    fg3 = Color(0xFF8A8A93),
    fg4 = Color(0xFF55555B),
    fgInverse = Color(0xFF17171A),
    line1 = Color(0x0FFFFFFF),
    line2 = Color(0x1AFFFFFF),
    line3 = Color(0x29FFFFFF),         // rgba(255,255,255,0.16)
    accent = Color(0xFF4B8CFF),
    accentSoft = Color(0xFF1C2F5E),
    accentFg = Color(0xFFFFFFFF),
    warn = Color(0xFFE8A100),
    ruleX = Color(0xFFD2B3E8),
    ruleY = Color(0xFFD2B3E8),
    ruleLabel = Color(0xFF9A5FB8),
    selStroke = Color(0xFF4B8CFF),
    selFill = Color(0x1A4B8CFF),
    glyphScreen = Color(0xFF7C3AED),
    glyphScreenBg = Color(0x1F7C3AED),
    glyphLayout = Color(0xFF0EA5A0),
    glyphLayoutBg = Color(0x1F0EA5A0),
    glyphText = Color(0xFFE87800),
    glyphTextBg = Color(0x24E87800),
    glyphElement = Color(0xFF6B7280),
    glyphElementBg = Color(0x246B7280),
    isDark = true,
)

internal val LocalInspectorPalette = staticCompositionLocalOf { InspectorPaletteDark }

object InspectorTokens {
    val current: InspectorPalette
        @Composable @ReadOnlyComposable get() = LocalInspectorPalette.current
}

// ─────────────────────────────────────────────────────────────────────────────
// Typography & sizing tokens — extracted from the HTML's hand-tuned font-size
// / padding / radius values. Centralized here so the panels stay consistent.
// ─────────────────────────────────────────────────────────────────────────────

object InspectorType {
    val sectionLabel: TextUnit = 11.sp     // uppercase 11px / weight 600 / 0.4 letterSpacing
    val panelTitle: TextUnit = 13.sp       // PanelHeader meta
    val panelMeta: TextUnit = 11.sp        // PanelHeader submeta (mono)
    val kvBody: TextUnit = 11.5.sp         // KV rows
    val kvBodySmall: TextUnit = 10.5.sp    // BoxModel + variant labels
    val toolbarText: TextUnit = 12.sp
    val treeRow: TextUnit = 12.sp
    val treeSize: TextUnit = 10.sp
    val titleBar: TextUnit = 12.sp
    val timelineFrame: TextUnit = 11.sp
    val timelineFrameMeta: TextUnit = 10.sp
    val badge: TextUnit = 10.sp
}

object InspectorRadius {
    val window: Dp = 12.dp
    val lg: Dp = 10.dp
    val md: Dp = 8.dp
    val sm: Dp = 6.dp
    val xs: Dp = 4.dp
    val tab: Dp = 7.dp
    val pillBadge: Dp = 3.dp
}

object InspectorSpacing {
    val titleBarHeight: Dp = 40.dp
    val toolbarHeight: Dp = 48.dp
    val timelineHeight: Dp = 72.dp
    val leftPanelWidth: Dp = 260.dp
    val rightPanelWidth: Dp = 280.dp
    val frameTileWidth: Dp = 72.dp
    val frameTileHeight: Dp = 44.dp
    val addFrameTileWidth: Dp = 44.dp
}

// ─────────────────────────────────────────────────────────────────────────────
// MaterialTheme schemes — derived from the inspector palette so existing
// MaterialTheme.colorScheme.* call sites continue to render in-spec.
// ─────────────────────────────────────────────────────────────────────────────

private fun lightSchemeFrom(p: InspectorPalette) = lightColorScheme(
    primary = p.accent,
    onPrimary = p.accentFg,
    primaryContainer = p.accentSoft,
    onPrimaryContainer = p.fg1,
    secondary = p.fg2,
    onSecondary = p.fgInverse,
    background = p.bgWindow,
    onBackground = p.fg1,
    surface = p.bgPanel,
    onSurface = p.fg1,
    surfaceVariant = p.bgPanelMuted,
    onSurfaceVariant = p.fg2,
    surfaceContainerLowest = p.bgPanel,
    surfaceContainerLow = p.bgPanelMuted,
    surfaceContainer = p.bgPanelMuted,
    surfaceContainerHigh = p.bgCanvas,
    surfaceContainerHighest = p.bgCanvas,
    outline = p.fg3,
    outlineVariant = p.line2,
)

private fun darkSchemeFrom(p: InspectorPalette) = darkColorScheme(
    primary = p.accent,
    onPrimary = p.accentFg,
    primaryContainer = p.accentSoft,
    onPrimaryContainer = p.fg1,
    secondary = p.fg2,
    onSecondary = p.fgInverse,
    background = p.bgWindow,
    onBackground = p.fg1,
    surface = p.bgPanel,
    onSurface = p.fg1,
    surfaceVariant = p.bgPanelMuted,
    onSurfaceVariant = p.fg2,
    surfaceContainerLowest = p.bgPanel,
    surfaceContainerLow = p.bgPanelMuted,
    surfaceContainer = p.bgPanelMuted,
    surfaceContainerHigh = p.bgCanvas,
    surfaceContainerHighest = p.bgCanvas,
    outline = p.fg3,
    outlineVariant = p.line2,
)

@Composable
fun InspectorTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) InspectorPaletteDark else InspectorPaletteLight
    val scheme = if (darkTheme) darkSchemeFrom(palette) else lightSchemeFrom(palette)
    CompositionLocalProvider(LocalInspectorPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            content = content,
        )
    }
}
