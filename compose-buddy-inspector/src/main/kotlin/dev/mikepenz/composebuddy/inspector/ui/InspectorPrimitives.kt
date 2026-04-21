package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text

// ─────────────────────────────────────────────────────────────────────────────
// PanelHeader — uppercase 11sp 0.4 letterSpacing label, optional 13sp meta and
// monospace 11sp submeta. Mirrors the JS PanelHeader in Agent Buddy.html.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PanelHeader(
    title: String,
    meta: String? = null,
    submeta: String? = null,
    modifier: Modifier = Modifier,
    rightSlot: @Composable (() -> Unit)? = null,
) {
    val tokens = InspectorTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SectionLabel(text = title)
            if (meta != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = meta,
                    fontSize = InspectorType.panelTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.fg1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (submeta != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = submeta,
                    fontSize = InspectorType.panelMeta,
                    fontFamily = FontFamily.Monospace,
                    color = tokens.fg3,
                )
            }
        }
        if (rightSlot != null) rightSlot()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val tokens = InspectorTokens.current
    Text(
        text = text.uppercase(),
        modifier = modifier,
        fontSize = InspectorType.sectionLabel,
        fontWeight = FontWeight.SemiBold,
        color = tokens.fg3,
        letterSpacing = 0.4.sp,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Section — uppercase header with bottom hairline, content padded 12/16.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tokens = InspectorTokens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SectionLabel(text = title)
        Spacer(Modifier.height(8.dp))
        content()
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(tokens.line1))
}

// ─────────────────────────────────────────────────────────────────────────────
// KV — left "key" in fg-3, right "value" right-aligned (mono optional).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun KV(
    k: String,
    v: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
) {
    val tokens = InspectorTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = k,
            color = tokens.fg3,
            fontSize = InspectorType.kvBody,
        )
        Text(
            text = v,
            color = tokens.fg1,
            fontSize = InspectorType.kvBody,
            fontWeight = FontWeight.Medium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Swatch — 22×14 rounded rect with hairline border + inset highlight.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun Swatch(color: Color, modifier: Modifier = Modifier) {
    val tokens = InspectorTokens.current
    Box(
        modifier = modifier
            .size(width = 22.dp, height = 14.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
            .border(0.5.dp, tokens.line2, RoundedCornerShape(3.dp)),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Segmented control — pill row with active button having raised surface.
// ─────────────────────────────────────────────────────────────────────────────

data class SegmentedOption<T>(val value: T, val label: String, val icon: ImageVector? = null)

@Composable
fun <T> Segmented(
    value: T,
    options: List<SegmentedOption<T>>,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = InspectorTokens.current
    Row(
        modifier = modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(InspectorRadius.tab))
            .background(tokens.bgPanelMuted)
            .border(1.dp, tokens.line1, RoundedCornerShape(InspectorRadius.tab))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { opt ->
            val active = opt.value == value
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .then(
                        if (active) Modifier
                            .background(tokens.bgPanel)
                            .border(0.5.dp, tokens.line3, RoundedCornerShape(5.dp))
                        else Modifier,
                    )
                    .clickable { onChange(opt.value) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (opt.icon != null) {
                    Icon(
                        imageVector = opt.icon,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (active) tokens.fg1 else tokens.fg2,
                    )
                }
                Text(
                    text = opt.label,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (active) tokens.fg1 else tokens.fg2,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Toggle — 34×20 pill switch with 16dp white knob, accent bg when on.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun Toggle(
    value: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = InspectorTokens.current
    Box(
        modifier = modifier
            .size(width = 34.dp, height = 20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (value) tokens.accent else tokens.line3)
            .clickable { onChange(!value) }
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .align(if (value) Alignment.CenterEnd else Alignment.CenterStart)
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Radio — 14dp circle with accent border + inner dot when selected.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun Radio(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = InspectorTokens.current
    Box(
        modifier = modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(tokens.bgPanel)
            .border(
                width = 1.5.dp,
                color = if (checked) tokens.accent else tokens.line3,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(tokens.accent),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TypeGlyph — 16×16 rounded badge with letter (S Screen / L Layout / T Text /
// E Element). Background color and letter color come from the palette.
// ─────────────────────────────────────────────────────────────────────────────

enum class GlyphKind { Screen, Layout, Text, Element }

fun glyphKindFor(name: String): GlyphKind = when {
    name.equals("Screen", ignoreCase = true) -> GlyphKind.Screen
    name.equals("Text", ignoreCase = true) -> GlyphKind.Text
    name.endsWith("Layout", ignoreCase = true) ||
        name.equals("Column", ignoreCase = true) ||
        name.equals("Row", ignoreCase = true) ||
        name.equals("Box", ignoreCase = true) ||
        name.equals("Card", ignoreCase = true) -> GlyphKind.Layout
    else -> GlyphKind.Element
}

@Composable
fun TypeGlyph(kind: GlyphKind, modifier: Modifier = Modifier) {
    val tokens = InspectorTokens.current
    val (label, fg, bg) = when (kind) {
        GlyphKind.Screen -> Triple("S", tokens.glyphScreen, tokens.glyphScreenBg)
        GlyphKind.Layout -> Triple("L", tokens.glyphLayout, tokens.glyphLayoutBg)
        GlyphKind.Text -> Triple("T", tokens.glyphText, tokens.glyphTextBg)
        GlyphKind.Element -> Triple("E", tokens.glyphElement, tokens.glyphElementBg)
    }
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(RoundedCornerShape(InspectorRadius.xs))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            // Tight glyph styling: line-height equal to font-size + LineHeightStyle Center/Both
            // so the letter sits in the optical middle of the 16dp badge rather than baseline-aligned.
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// KbdBadge — small bordered monospace label like "⌘K" / "esc".
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun KbdBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    val tokens = InspectorTokens.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(InspectorRadius.pillBadge))
            .border(0.5.dp, tokens.line2, RoundedCornerShape(InspectorRadius.pillBadge))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            fontSize = InspectorType.badge,
            color = tokens.fg3,
            fontFamily = FontFamily.Monospace,
        )
    }
}
