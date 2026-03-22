package dev.mikepenz.composebuddy.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class RenderConfigurationTest {

    private val defaultPreview = Preview(
        fullyQualifiedName = "com.example.MyPreview",
        widthDp = 360,
        heightDp = 640,
        locale = "en",
        fontScale = 1.5f,
        uiMode = 0x21,
        device = "id:pixel_5",
        showBackground = true,
        backgroundColor = 0xFFFFFFFF,
        apiLevel = 33,
    )

    @Test
    fun `resolve with no overrides returns preview values`() {
        val config = RenderConfiguration.resolve(defaultPreview)
        assertEquals(360, config.widthDp)
        assertEquals(640, config.heightDp)
        assertEquals("en", config.locale)
        assertEquals(1.5f, config.fontScale)
        assertEquals(0x21, config.uiMode)
        assertEquals("id:pixel_5", config.device)
        assertEquals(true, config.showBackground)
        assertEquals(0xFFFFFFFF, config.backgroundColor)
        assertEquals(33, config.apiLevel)
    }

    @Test
    fun `override width and height takes precedence`() {
        val overrides = RenderConfiguration(widthDp = 720, heightDp = 1280)
        val config = RenderConfiguration.resolve(defaultPreview, overrides)
        assertEquals(720, config.widthDp)
        assertEquals(1280, config.heightDp)
    }

    @Test
    fun `override locale takes precedence`() {
        val overrides = RenderConfiguration(locale = "ja")
        val config = RenderConfiguration.resolve(defaultPreview, overrides)
        assertEquals("ja", config.locale)
    }

    @Test
    fun `default override values do not replace preview values`() {
        val overrides = RenderConfiguration() // all defaults
        val config = RenderConfiguration.resolve(defaultPreview, overrides)
        assertEquals(360, config.widthDp)
        assertEquals("en", config.locale)
        assertEquals(1.5f, config.fontScale)
    }

    @Test
    fun `override fontScale takes precedence when not 1f`() {
        val overrides = RenderConfiguration(fontScale = 2.0f)
        val config = RenderConfiguration.resolve(defaultPreview, overrides)
        assertEquals(2.0f, config.fontScale)
    }

    @Test
    fun `resolve with minimal preview and no overrides`() {
        val minimal = Preview(fullyQualifiedName = "com.example.Minimal")
        val config = RenderConfiguration.resolve(minimal)
        assertEquals(-1, config.widthDp)
        assertEquals(-1, config.heightDp)
        assertEquals("", config.locale)
        assertEquals(1f, config.fontScale)
    }
}
