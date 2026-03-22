package dev.mikepenz.composebuddy.renderer

import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceConfigMapperTest {

    private val defaultPreview = Preview(fullyQualifiedName = "com.example.Test")

    @Test
    fun `default config uses default device dimensions`() {
        val config = RenderConfiguration()
        val result = DeviceConfigMapper.map(defaultPreview, config)
        assertEquals(1080, result.screenWidthPx)
        assertEquals(1920, result.screenHeightPx)
        assertEquals(420, result.densityDpi)
    }

    @Test
    fun `custom widthDp overrides screen width`() {
        val config = RenderConfiguration(widthDp = 360)
        val result = DeviceConfigMapper.map(defaultPreview, config)
        // 360dp * 420dpi / 160 = 945px
        assertEquals(945, result.screenWidthPx)
    }

    @Test
    fun `known device id maps correctly`() {
        val config = RenderConfiguration(device = "id:pixel_5")
        val result = DeviceConfigMapper.map(defaultPreview, config)
        assertEquals(1080, result.screenWidthPx)
        assertEquals(2340, result.screenHeightPx)
        assertEquals(440, result.densityDpi)
    }

    @Test
    fun `device spec string parses correctly`() {
        val config = RenderConfiguration(device = "spec:width=1440px,height=2560px,dpi=560")
        val result = DeviceConfigMapper.map(defaultPreview, config)
        assertEquals(1440, result.screenWidthPx)
        assertEquals(2560, result.screenHeightPx)
        assertEquals(560, result.densityDpi)
    }

    @Test
    fun `unknown device falls back to defaults`() {
        val config = RenderConfiguration(device = "id:unknown_device")
        val result = DeviceConfigMapper.map(defaultPreview, config)
        assertEquals(1080, result.screenWidthPx)
        assertEquals(1920, result.screenHeightPx)
    }

    @Test
    fun `font scale is passed through`() {
        val config = RenderConfiguration(fontScale = 2.0f)
        val result = DeviceConfigMapper.map(defaultPreview, config)
        assertEquals(2.0f, result.fontScale)
    }

    @Test
    fun `locale is passed through`() {
        val config = RenderConfiguration(locale = "ja")
        val result = DeviceConfigMapper.map(defaultPreview, config)
        assertEquals("ja", result.locale)
    }

    @Test
    fun `ui mode is passed through`() {
        val config = RenderConfiguration(uiMode = 0x21)
        val result = DeviceConfigMapper.map(defaultPreview, config)
        assertEquals(0x21, result.uiMode)
    }

    @Test
    fun `background config is passed through`() {
        val config = RenderConfiguration(showBackground = true, backgroundColor = 0xFFFF0000)
        val result = DeviceConfigMapper.map(defaultPreview, config)
        assertEquals(true, result.showBackground)
        assertEquals(0xFFFF0000, result.backgroundColor)
    }
}
