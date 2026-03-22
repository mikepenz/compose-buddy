package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mikepenz.composebuddy.inspector.model.Frame
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Check if two frames have identical render output (same image dimensions and hierarchy structure).
 */
private fun framesEqual(a: Frame, b: Frame): Boolean {
    val ra = a.renderResult
    val rb = b.renderResult
    if (ra.imageWidth != rb.imageWidth || ra.imageHeight != rb.imageHeight) return false
    if (ra.imagePath != rb.imagePath && ra.imageBase64 != rb.imageBase64) {
        // Compare hierarchy as a proxy for content equality
        return ra.hierarchy == rb.hierarchy
    }
    return true
}

@Composable
fun Timeline(
    frames: List<Frame>,
    currentFrameIndex: Int,
    onFrameSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pre-compute which frames are equal to their predecessor
    val equalToPrevious = remember(frames) {
        frames.mapIndexed { index, frame ->
            if (index > 0) framesEqual(frames[index - 1], frame) else false
        }
    }

    val selectedBg = MaterialTheme.colorScheme.primaryContainer
    val selectedBorder = MaterialTheme.colorScheme.primary
    val unchangedBg = MaterialTheme.colorScheme.secondaryContainer
    val unchangedBorder = MaterialTheme.colorScheme.secondary
    val defaultBg = MaterialTheme.colorScheme.surfaceVariant
    val defaultBorder = MaterialTheme.colorScheme.outline
    val unchangedTextColor = MaterialTheme.colorScheme.secondary
    val subtleTextColor = MaterialTheme.colorScheme.outline

    Column(modifier = modifier.padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Timeline (${frames.size} frames)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            val unchangedCount = equalToPrevious.count { it }
            if (unchangedCount > 0) {
                Text(
                    "  ($unchangedCount unchanged)",
                    fontSize = 10.sp,
                    color = subtleTextColor,
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.height(64.dp),
        ) {
            itemsIndexed(frames) { index, frame ->
                val isSelected = index == currentFrameIndex
                val isUnchanged = equalToPrevious.getOrElse(index) { false }

                val bgColor = when {
                    isSelected -> selectedBg
                    isUnchanged -> unchangedBg
                    else -> defaultBg
                }
                val borderColor = when {
                    isSelected -> selectedBorder
                    isUnchanged -> unchangedBorder
                    else -> defaultBorder
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(74.dp)
                        .height(60.dp)
                        .background(bgColor)
                        .border(1.dp, borderColor)
                        .clickable { onFrameSelected(index) }
                        .padding(4.dp),
                ) {
                    Text(
                        "#${frame.id}",
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    Text(timeFormat.format(Date(frame.timestamp)), fontSize = 9.sp, color = subtleTextColor)
                    if (isUnchanged) {
                        Text("= unchanged", fontSize = 8.sp, color = unchangedTextColor)
                    }
                    frame.label?.let {
                        Text(it, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
