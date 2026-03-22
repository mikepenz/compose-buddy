package dev.mikepenz.composebuddy.inspector.analysis

import dev.mikepenz.composebuddy.core.model.Bounds
import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.core.model.Size
import dev.mikepenz.composebuddy.inspector.model.AiFinding
import dev.mikepenz.composebuddy.inspector.model.Severity
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AnalysisProviderTest {

    private val mockProvider = object : AnalysisProvider {
        override suspend fun analyze(
            hierarchy: HierarchyNode,
            figmaTokens: Map<String, dev.mikepenz.composebuddy.inspector.model.TokenValue>?,
        ): List<AiFinding> {
            val findings = mutableListOf<AiFinding>()
            collectFindings(hierarchy, findings)
            return findings
        }

        private fun collectFindings(node: HierarchyNode, findings: MutableList<AiFinding>) {
            node.semantics?.forEach { (key, value) ->
                if (key == "backgroundColor" && value.startsWith("#")) {
                    findings.add(
                        AiFinding(
                            id = "finding-${node.id}",
                            severity = Severity.WARNING,
                            nodeId = node.id,
                            nodeName = node.name,
                            category = "hardcoded-color",
                            message = "Hardcoded color $value used instead of a semantic token",
                            suggestion = "Use MaterialTheme.colorScheme.primary instead",
                        ),
                    )
                }
            }
            node.children.forEach { collectFindings(it, findings) }
        }
    }

    @Test
    fun `mock provider detects hardcoded colors`() = runBlocking {
        val hierarchy = HierarchyNode(
            id = 0,
            name = "Box",
            bounds = Bounds(0.0, 0.0, 100.0, 100.0),
            size = Size(100.0, 100.0),
            semantics = mapOf("backgroundColor" to "#FF0000"),
        )

        val findings = mockProvider.analyze(hierarchy)
        assertEquals(1, findings.size)
        assertEquals(Severity.WARNING, findings[0].severity)
        assertEquals("hardcoded-color", findings[0].category)
    }

    @Test
    fun `mock provider returns empty for clean hierarchy`() = runBlocking {
        val hierarchy = HierarchyNode(
            id = 0,
            name = "Box",
            bounds = Bounds(0.0, 0.0, 100.0, 100.0),
            size = Size(100.0, 100.0),
            semantics = mapOf("text" to "Hello"),
        )

        val findings = mockProvider.analyze(hierarchy)
        assertEquals(0, findings.size)
    }
}
