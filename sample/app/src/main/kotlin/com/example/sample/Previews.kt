package com.example.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp

// ============================================================
// 1. Simple Box — minimal preview, tests basic rendering
// ============================================================

@Composable
fun SimpleBox() {
    Box(
        modifier = Modifier
            .size(200.dp)
            .background(Color(0xFF1A73E8)),
    )
}

@Preview
@Composable
fun SimpleBoxPreview() {
    SimpleBox()
}

// ============================================================
// 2. Column with padding — tests hierarchy/bounds extraction
// ============================================================

@Composable
fun PaddedColumn() {
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

@Preview(widthDp = 360, heightDp = 640, showBackground = true)
@Composable
fun PaddedColumnPreview() {
    PaddedColumn()
}

// ============================================================
// 3. Card with text — tests composable nesting and semantics
// ============================================================

@Composable
fun SampleCard(title: String, subtitle: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
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
fun SampleCardPreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        SampleCard(title = "Hello World", subtitle = "This is a sample card")
    }
}

// ============================================================
// 4. Dark mode preview — tests uiMode override
// ============================================================

@Preview(
    name = "Dark Mode Card",
    showBackground = true,
    backgroundColor = 0xFF121212,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun DarkModeCardPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        SampleCard(title = "Dark Mode", subtitle = "Testing dark theme rendering")
    }
}

// ============================================================
// 5. Different device sizes — tests device spec handling
// ============================================================

@Preview(name = "Phone", device = "id:pixel_5", showBackground = true)
@Composable
fun PhonePreview() {
    SampleApp()
}

@Preview(name = "Small", widthDp = 200, heightDp = 300, showBackground = true)
@Composable
fun SmallPreview() {
    SimpleBox()
}

// ============================================================
// 6. Clickable without contentDescription — tests a11y checker
// ============================================================

@Composable
fun AccessibilityBadButton() {
    // This clickable Box has NO contentDescription — should trigger a11y finding
    Box(
        modifier = Modifier
            .size(36.dp) // Also too small for touch target (< 48dp)
            .background(Color.Red)
            .clickable { },
    )
}

@Preview
@Composable
fun AccessibilityBadButtonPreview() {
    AccessibilityBadButton()
}

// ============================================================
// 7. Accessible button — should pass a11y checks
// ============================================================

@Composable
fun AccessibilityGoodButton() {
    Button(
        onClick = { },
        modifier = Modifier.semantics { contentDescription = "Submit form" },
    ) {
        Text("Submit")
    }
}

@Preview(showBackground = true)
@Composable
fun AccessibilityGoodButtonPreview() {
    MaterialTheme {
        AccessibilityGoodButton()
    }
}

// ============================================================
// 8. Parameterized preview — tests @PreviewParameter support
// ============================================================

class GreetingProvider : PreviewParameterProvider<String> {
    override val values: Sequence<String> = sequenceOf(
        "Hello",
        "Bonjour",
        "Hola",
        "こんにちは",
    )
}

@Preview(showBackground = true)
@Composable
fun ParameterizedGreeting(@PreviewParameter(GreetingProvider::class) greeting: String) {
    Text(
        text = greeting,
        style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier.padding(16.dp),
    )
}

// ============================================================
// 9. Complex layout — tests deep hierarchy
// ============================================================

@Composable
fun ComplexLayout() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Title", style = MaterialTheme.typography.headlineSmall)
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
                            .background(
                                Color(0xFF1A73E8),
                                RoundedCornerShape(20.dp),
                            ),
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

@Preview(widthDp = 360, heightDp = 640, showBackground = true)
@Composable
fun ComplexLayoutPreview() {
    MaterialTheme {
        ComplexLayout()
    }
}

// ============================================================
// 10. Locale preview — tests locale override
// ============================================================

@Preview(locale = "ja", showBackground = true)
@Composable
fun JapanesePreview() {
    MaterialTheme {
        Text(
            text = "日本語テスト",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp),
        )
    }
}

// ============================================================
// 11. Multi-preview annotations — tests @PreviewLightDark etc.
// ============================================================

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
annotation class PreviewLightDark

@PreviewLightDark
@Composable
fun MultiPreviewCard() {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            SampleCard(
                title = if (isDark) "Dark Theme" else "Light Theme",
                subtitle = "Multi-preview variant (uiMode based)",
            )
        }
    }
}

// ============================================================
// Top-level app composable
// ============================================================

@Composable
fun SampleApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SampleCard(title = "Compose Buddy", subtitle = "Preview rendering sample")
                Spacer(modifier = Modifier.height(16.dp))
                AccessibilityGoodButton()
            }
        }
    }
}
