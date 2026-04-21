package com.example.desktop.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SectionHeader(
    title: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionIcon: String = "⚙",
    actionContentDescription: String = "Settings",
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        IconButton(
            onClick = onAction,
            modifier = Modifier.semantics { contentDescription = actionContentDescription },
        ) {
            Text(actionIcon)
        }
    }
}

@Preview
@Composable
internal fun SectionHeaderPreview_Default() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        SectionHeader(title = "Desktop App", onAction = {}, modifier = Modifier.padding(16.dp))
    }
}

@Preview
@Composable
internal fun SectionHeaderPreview_Dark() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            SectionHeader(title = "Desktop App", onAction = {}, modifier = Modifier.padding(16.dp))
        }
    }
}
