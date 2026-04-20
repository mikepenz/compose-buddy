package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor

/**
 * Thin vertical splitter between panels. Dragging horizontally invokes [onDelta]
 * with a dp offset (positive = right). Renders a 1 dp visual line inside a 6 dp
 * hit area for easier grabbing.
 */
@Composable
fun PanelDragHandle(
    modifier: Modifier = Modifier,
    onDelta: (Dp) -> Unit,
) {
    val tokens = InspectorTokens.current
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val active = hovered || dragged

    val draggable = rememberDraggableState { deltaPx ->
        val deltaDp = with(density) { deltaPx.toDp() }
        onDelta(deltaDp)
    }

    val resizeCursor = remember { PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)) }

    Box(
        modifier = modifier
            .width(6.dp)
            .fillMaxHeight()
            .pointerHoverIcon(resizeCursor)
            .hoverable(interaction)
            .draggable(
                state = draggable,
                orientation = Orientation.Horizontal,
                interactionSource = interaction,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(if (active) tokens.accent else tokens.line1),
        )
    }
}
