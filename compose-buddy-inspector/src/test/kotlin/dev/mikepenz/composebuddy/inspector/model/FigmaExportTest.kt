package dev.mikepenz.composebuddy.inspector.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FigmaExportTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `deserialize valid FigmaExport JSON`() {
        val input = """
        {
            "version": "1.0.0",
            "tokens": {
                "color.primary": {
                    "type": "color",
                    "value": "#6200EE",
                    "category": "brand"
                },
                "spacing.md": {
                    "type": "spacing",
                    "value": "16dp"
                }
            },
            "components": [
                {
                    "name": "LoginButton",
                    "bounds": [16.0, 400.0, 328.0, 48.0],
                    "appliedTokens": {
                        "backgroundColor": "color.primary"
                    },
                    "children": []
                }
            ]
        }
        """.trimIndent()

        val export = json.decodeFromString<FigmaExport>(input)
        assertEquals("1.0.0", export.version)
        assertEquals(2, export.tokens.size)
        assertEquals("#6200EE", export.tokens["color.primary"]?.value)
        assertEquals("color", export.tokens["color.primary"]?.type)
        assertEquals("brand", export.tokens["color.primary"]?.category)
        assertEquals("16dp", export.tokens["spacing.md"]?.value)
        assertEquals(1, export.components.size)
        assertEquals("LoginButton", export.components[0].name)
        assertEquals("color.primary", export.components[0].appliedTokens["backgroundColor"])
    }

    @Test
    fun `deserialize minimal FigmaExport`() {
        val input = """{"version": "1.0.0", "tokens": {}}"""
        val export = json.decodeFromString<FigmaExport>(input)
        assertEquals("1.0.0", export.version)
        assertTrue(export.tokens.isEmpty())
        assertTrue(export.components.isEmpty())
    }

    @Test
    fun `malformed JSON throws exception`() {
        assertThrows<Exception> {
            json.decodeFromString<FigmaExport>("not json")
        }
    }
}
