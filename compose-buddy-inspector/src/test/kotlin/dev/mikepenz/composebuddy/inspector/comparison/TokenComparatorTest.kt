package dev.mikepenz.composebuddy.inspector.comparison

import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Size
import dev.mikepenz.composebuddy.inspector.model.FigmaExport
import dev.mikepenz.composebuddy.inspector.model.TokenValue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenComparatorTest {

    private val hierarchy = HierarchyNode(
        id = 0,
        name = "Root",
        bounds = Bounds(0.0, 0.0, 360.0, 640.0),
        size = Size(360.0, 640.0),
        semantics = mapOf(
            "backgroundColor" to "#6200EE",
            "foregroundColor" to "#FFFFFF",
        ),
        children = listOf(
            HierarchyNode(
                id = 1,
                name = "Text",
                bounds = Bounds(16.0, 16.0, 200.0, 40.0),
                size = Size(184.0, 24.0),
                semantics = mapOf(
                    "textColor" to "#000000",
                ),
            ),
        ),
    )

    @Test
    fun `matched tokens are correctly identified`() {
        val figma = FigmaExport(
            version = "1.0.0",
            tokens = mapOf(
                "backgroundColor" to TokenValue("color", "#6200EE"),
            ),
        )
        val report = TokenComparator.compare(hierarchy, figma)
        assertEquals(1, report.matched.size)
        assertEquals("backgroundColor", report.matched[0].tokenName)
    }

    @Test
    fun `mismatched tokens are correctly identified`() {
        val figma = FigmaExport(
            version = "1.0.0",
            tokens = mapOf(
                "backgroundColor" to TokenValue("color", "#FF0000"),
            ),
        )
        val report = TokenComparator.compare(hierarchy, figma)
        assertEquals(1, report.mismatched.size)
        assertEquals("#FF0000", report.mismatched[0].figmaValue)
        assertEquals("#6200EE", report.mismatched[0].composeValue)
        assertTrue(report.mismatched[0].deltaE!! > 0)
    }

    @Test
    fun `missing tokens in compose are reported`() {
        val figma = FigmaExport(
            version = "1.0.0",
            tokens = mapOf(
                "nonExistentToken" to TokenValue("spacing", "16dp"),
            ),
        )
        val report = TokenComparator.compare(hierarchy, figma)
        assertTrue(report.missingInCompose.contains("nonExistentToken"))
    }

    @Test
    fun `missing tokens in figma are reported`() {
        val figma = FigmaExport(
            version = "1.0.0",
            tokens = emptyMap(),
        )
        val report = TokenComparator.compare(hierarchy, figma)
        assertTrue(report.missingInFigma.isNotEmpty())
    }

    @Test
    fun `summary percentages are correct`() {
        val figma = FigmaExport(
            version = "1.0.0",
            tokens = mapOf(
                "backgroundColor" to TokenValue("color", "#6200EE"),
                "foregroundColor" to TokenValue("color", "#000000"), // mismatch
            ),
        )
        val report = TokenComparator.compare(hierarchy, figma)
        assertEquals(report.summary.matchCount + report.summary.mismatchCount + report.missingInCompose.size + report.missingInFigma.size, report.summary.totalTokens)
    }
}
