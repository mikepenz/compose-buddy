package dev.mikepenz.composebuddy.renderer

import dev.mikepenz.composebuddy.core.model.Preview
import dev.mikepenz.composebuddy.core.model.RenderConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceConfigMapperEdgeCaseTest {

    private val defaultPreview = Preview(fullyQualifiedName = "com.example.Test")

    @Test
    fun `malformed device spec without equals is handled gracefully`() {
        val config = RenderConfiguration(device = "spec:width1080px,height=1920px,dpi=420")
        val result = DeviceConfigMapper.map(defaultPreview, config)
        // Malformed "width1080px" has no '=', should be skipped → width falls back to null → default device
        assertEquals(1080, result.screenWidthPx) // falls back to default
    }

    @Test
    fun `completely invalid device spec falls back to default`() {
        val config = RenderConfiguration(device = "garbage")
        val result = DeviceConfigMapper.map(defaultPreview, config)
        assertEquals(1080, result.screenWidthPx)
        assertEquals(1920, result.screenHeightPx)
        assertEquals(420, result.densityDpi)
    }

    @Test
    fun `empty device spec uses default`() {
        val config = RenderConfiguration(device = "")
        val result = DeviceConfigMapper.map(defaultPreview, config)
        assertEquals(1080, result.screenWidthPx)
        assertEquals(1920, result.screenHeightPx)
    }

    @Test
    fun `device spec with missing dpi uses default 420`() {
        val config = RenderConfiguration(device = "spec:width=720px,height=1280px")
        val result = DeviceConfigMapper.map(defaultPreview, config)
        assertEquals(720, result.screenWidthPx)
        assertEquals(1280, result.screenHeightPx)
        assertEquals(420, result.densityDpi)
    }

    @Test
    fun `density override takes precedence over device dpi`() {
        val config = RenderConfiguration(device = "id:pixel_5", densityDpi = 320)
        val result = DeviceConfigMapper.map(defaultPreview, config)
        assertEquals(320, result.densityDpi)
    }

    @Test
    fun `widthDp and heightDp override device dimensions`() {
        val config = RenderConfiguration(device = "id:pixel_5", widthDp = 200, heightDp = 400)
        val result = DeviceConfigMapper.map(defaultPreview, config)
        // 200dp * 440dpi / 160 = 550px (pixel_5 has 440 dpi)
        assertEquals(550, result.screenWidthPx)
        assertEquals(1100, result.screenHeightPx)
    }
}
