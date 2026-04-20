package dev.mikepenz.composebuddy.inspector.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class PanelLayoutState(
    leftWidth: Dp = DefaultLeftWidth,
    rightWidth: Dp = DefaultRightWidth,
    leftUserVisible: Boolean = true,
    rightUserVisible: Boolean = true,
) {
    var leftWidth by mutableStateOf(leftWidth)
    var rightWidth by mutableStateOf(rightWidth)
    var leftUserVisible by mutableStateOf(leftUserVisible)
    var rightUserVisible by mutableStateOf(rightUserVisible)

    companion object {
        val DefaultLeftWidth = 260.dp
        val DefaultRightWidth = 280.dp
        val LeftMin = 180.dp
        val LeftMax = 480.dp
        val RightMin = 220.dp
        val RightMax = 520.dp
        val CenterMin = 320.dp
    }
}

@Composable
fun rememberPanelLayoutState(): PanelLayoutState = remember { PanelLayoutState() }
