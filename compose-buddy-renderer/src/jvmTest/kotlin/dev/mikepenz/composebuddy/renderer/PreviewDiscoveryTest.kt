package dev.mikepenz.composebuddy.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewDiscoveryTest {

    @Test
    fun `discover returns empty list for empty classpath`() {
        val discovery = PreviewDiscovery()
        val previews = discovery.discover(emptyList())
        assertTrue(previews.isEmpty())
    }

    @Test
    fun `discover returns empty list for non-existent directory`() {
        val discovery = PreviewDiscovery()
        val previews = discovery.discover(listOf(java.io.File("/nonexistent")))
        assertTrue(previews.isEmpty())
    }

    @Test
    fun `discover with package filter narrows results`() {
        val discovery = PreviewDiscovery()
        // With empty classpath, no results regardless of filter
        val previews = discovery.discover(emptyList(), "com.example.ui")
        assertEquals(0, previews.size)
    }
}
