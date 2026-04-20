package com.example.sample.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.sample.theme.BuddyColors
import com.example.sample.theme.BuddyShapes

@Composable
fun ListItemRow(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    avatarColor: Color = BuddyColors.Blue,
) {
    Row(
        modifier = modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(avatarColor, BuddyShapes.avatar),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ListItemRowPreview_Default() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            ListItemRow(title = "Item", description = "Short description")
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 360)
@Composable
private fun ListItemRowPreview_Dark() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                ListItemRow(title = "Item", description = "Dark theme description")
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 280)
@Composable
private fun ListItemRowPreview_LongDescription() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            ListItemRow(
                title = "Item with a long title",
                description = "A long description that needs to wrap across multiple lines when the row is narrow",
            )
        }
    }
}
