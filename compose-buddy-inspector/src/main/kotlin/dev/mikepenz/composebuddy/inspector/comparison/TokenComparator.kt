package dev.mikepenz.composebuddy.inspector.comparison

import dev.mikepenz.composebuddy.core.model.HierarchyNode
import dev.mikepenz.composebuddy.inspector.model.ComparisonReport
import dev.mikepenz.composebuddy.inspector.model.ComparisonSummary
import dev.mikepenz.composebuddy.inspector.model.FigmaExport
import dev.mikepenz.composebuddy.inspector.model.TokenMatch
import dev.mikepenz.composebuddy.inspector.model.TokenMismatch

/**
 * Compares Figma design tokens against Compose semantic tokens
 * extracted from the HierarchyNode tree.
 */
object TokenComparator {

    fun compare(hierarchy: HierarchyNode, figmaExport: FigmaExport): ComparisonReport {
        val composeTokens = extractComposeTokens(hierarchy)
        val figmaTokens = figmaExport.tokens

        val allTokenNames = (figmaTokens.keys + composeTokens.keys).distinct()
        val matched = mutableListOf<TokenMatch>()
        val mismatched = mutableListOf<TokenMismatch>()
        val missingInCompose = mutableListOf<String>()
        val missingInFigma = mutableListOf<String>()

        for (name in allTokenNames) {
            val figmaValue = figmaTokens[name]
            val composeValue = composeTokens[name]

            when {
                figmaValue == null -> missingInFigma.add(name)
                composeValue == null -> missingInCompose.add(name)
                figmaValue.value == composeValue -> matched.add(TokenMatch(name, figmaValue.value))
                else -> {
                    val deltaE = if (figmaValue.type == "color") {
                        try {
                            deltaE76(figmaValue.value, composeValue)
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                    mismatched.add(
                        TokenMismatch(
                            tokenName = name,
                            figmaValue = figmaValue.value,
                            composeValue = composeValue,
                            deltaE = deltaE,
                        ),
                    )
                }
            }
        }

        val total = allTokenNames.size
        return ComparisonReport(
            timestamp = System.currentTimeMillis(),
            matched = matched,
            mismatched = mismatched,
            missingInCompose = missingInCompose,
            missingInFigma = missingInFigma,
            summary = ComparisonSummary(
                totalTokens = total,
                matchCount = matched.size,
                mismatchCount = mismatched.size,
                matchPercentage = if (total > 0) matched.size.toDouble() / total * 100 else 0.0,
            ),
        )
    }

    /**
     * Walk the hierarchy tree and extract semantic properties that look like design tokens.
     * Returns a map of token-like key → value.
     */
    private fun extractComposeTokens(node: HierarchyNode): Map<String, String> {
        val tokens = mutableMapOf<String, String>()
        collectTokens(node, tokens)
        return tokens
    }

    private fun collectTokens(node: HierarchyNode, tokens: MutableMap<String, String>) {
        node.semantics?.forEach { (key, value) ->
            if (key.contains("Color", ignoreCase = true) ||
                key.contains("token", ignoreCase = true) ||
                key.contains("spacing", ignoreCase = true)
            ) {
                tokens[key] = value
            }
        }
        node.children.forEach { collectTokens(it, tokens) }
    }
}
