package com.example.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// ============================================================
// 1. Simple Box — minimal preview, tests basic rendering
// ============================================================

@Preview
@Composable
fun SimpleBoxPreview() {
    Box(
        modifier = Modifier
            .size(200.dp)
            .background(Color(0xFF1A73E8)),
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
                .background(Color(0xFF4CAF50)),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color(0xFFF44336)),
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
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Hello Desktop",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Material3 on Compose Desktop",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Desktop App", style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = { }) {
                    Text("⚙")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            repeat(3) { index ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF1A73E8), RoundedCornerShape(20.dp)),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Item ${index + 1}", style = MaterialTheme.typography.titleSmall)
                            Text("Description for item ${index + 1}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
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
