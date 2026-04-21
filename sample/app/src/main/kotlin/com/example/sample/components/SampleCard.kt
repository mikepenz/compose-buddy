package com.example.sample.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.sample.theme.BuddyShapes

@Composable
fun SampleCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = BuddyShapes.card,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
internal fun SampleCardPreview_Default() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        SampleCard(title = "Hello World", subtitle = "This is a sample card")
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
internal fun SampleCardPreview_Dark() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            SampleCard(title = "Dark Mode", subtitle = "Testing dark theme rendering")
        }
    }
}

@Preview(showBackground = true, widthDp = 280)
@Composable
internal fun SampleCardPreview_LongTitle() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        SampleCard(
            title = "A considerably longer title that should wrap across lines",
            subtitle = "Subtitle content appears below and wraps independently when it is long enough",
        )
    }
}
