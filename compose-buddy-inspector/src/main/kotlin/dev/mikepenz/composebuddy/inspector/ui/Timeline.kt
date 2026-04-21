package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mikepenz.composebuddy.inspector.model.Frame
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun framesEqual(a: Frame, b: Frame): Boolean {
    val ra = a.renderResult
    val rb = b.renderResult
    if (ra.imageWidth != rb.imageWidth || ra.imageHeight != rb.imageHeight) return false
    if (ra.imagePath != rb.imagePath && ra.imageBase64 != rb.imageBase64) {
        return ra.hierarchy == rb.hierarchy
    }
    return true
}

private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
fun Timeline(
    frames: List<Frame>,
    currentFrameIndex: Int,
    onFrameSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = InspectorTokens.current
    val equalToPrevious = remember(frames) {
        frames.mapIndexed { index, frame ->
            if (index > 0) framesEqual(frames[index - 1], frame) else false
        }
    }

    Column(modifier = modifier.background(tokens.bgPanel)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(tokens.line1))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(InspectorSpacing.timelineHeight)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header label + frame count
            Column {
                SectionLabel(text = "Timeline")
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${frames.size} ${if (frames.size == 1) "frame" else "frames"}",
                    color = tokens.fg3,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            // Vertical hairline divider
            Box(modifier = Modifier.width(1.dp).height(36.dp).background(tokens.line1))
            // Frame strip
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(frames) { index, frame ->
                    FrameTile(
                        index = frame.id,
                        label = frame.label ?: timeFormatter.format(Date(frame.timestamp)),
                        active = index == currentFrameIndex,
                        unchanged = equalToPrevious.getOrElse(index) { false },
                        onClick = { onFrameSelected(index) },
                    )
                }
                item {
                    AddFrameTile()
                }
            }
        }
    }
}

@Composable
private fun FrameTile(
    index: Int,
    label: String,
    active: Boolean,
    unchanged: Boolean,
    onClick: () -> Unit,
) {
    val tokens = InspectorTokens.current
    val bg = if (active) tokens.accent else tokens.bgPanelMuted
    val fg = if (active) tokens.accentFg else tokens.fg2
    Box(
        modifier = Modifier
            .size(width = InspectorSpacing.frameTileWidth, height = InspectorSpacing.frameTileHeight)
            .clip(RoundedCornerShape(InspectorRadius.tab))
            .background(bg)
            .border(0.5.dp, tokens.line2, RoundedCornerShape(InspectorRadius.tab))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "#$index",
                color = fg,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = label,
                color = fg.copy(alpha = if (active) 0.85f else 0.8f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun AddFrameTile() {
    val tokens = InspectorTokens.current
    val borderColor = tokens.line3
    Box(
        modifier = Modifier
            .size(width = InspectorSpacing.addFrameTileWidth, height = InspectorSpacing.frameTileHeight)
            .clip(RoundedCornerShape(InspectorRadius.tab))
            .drawBehind {
                val r = androidx.compose.ui.unit.Dp(7f).toPx()
                val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx(), pathEffect = dash),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            color = tokens.fg3,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
