package com.example.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.desktop.components.ListItemRow
import com.example.desktop.components.SampleCard
import com.example.desktop.components.SectionHeader
import com.example.desktop.theme.BuddyColors

// ============================================================
// 1. Simple Box — minimal preview, tests basic rendering
// ============================================================

@Preview
@Composable
fun SimpleBoxPreview() {
    Box(
        modifier = Modifier
            .size(200.dp)
            .background(BuddyColors.Blue),
    )
}

// ============================================================
// 2. Column with padding — tests hierarchy/bounds extraction
// ============================================================

@Preview(widthDp = 360, heightDp = 640)
@Composable
fun PaddedColumnPreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(BuddyColors.Green),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(BuddyColors.Red),
        )
    }
}

// ============================================================
// 3. Material3 Card — tests Material3 on Desktop
// ============================================================

@Preview
@Composable
fun SampleCardPreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        SampleCard(title = "Hello Desktop", subtitle = "Material3 on Compose Desktop")
    }
}

// ============================================================
// 4. Dark mode preview — tests theme detection
// ============================================================

@Preview
@Composable
fun DarkModeCardPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Dark Mode", style = MaterialTheme.typography.titleMedium)
                    Text("Testing dark theme rendering", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ============================================================
// 5. Button with semantics — tests accessibility
// ============================================================

@Preview
@Composable
fun AccessibleButtonPreview() {
    MaterialTheme {
        Button(
            onClick = { },
            modifier = Modifier.semantics { contentDescription = "Submit form" },
        ) {
            Text("Submit")
        }
    }
}

// ============================================================
// 6. Complex layout — tests deep hierarchy
// ============================================================

@Preview(widthDp = 360, heightDp = 640)
@Composable
fun ComplexLayoutPreview() {
    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            SectionHeader(title = "Desktop App", onAction = { })
            Spacer(modifier = Modifier.height(16.dp))
            repeat(3) { index ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    ListItemRow(
                        title = "Item ${index + 1}",
                        description = "Description for item ${index + 1}",
                    )
                }
            }
        }
    }
}

// ============================================================
// 7. Light/Dark multi-preview
// ============================================================

@Preview(name = "Light")
@Preview(name = "Dark")
@Composable
fun ThemeVariantPreview() {
    val isDark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (isDark) "Dark Theme" else "Light Theme",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Compose Desktop multi-preview",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
