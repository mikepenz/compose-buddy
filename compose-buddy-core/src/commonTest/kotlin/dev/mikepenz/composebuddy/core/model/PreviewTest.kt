package dev.mikepenz.composebuddy.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PreviewTest {

    @Test
    fun `valid preview with defaults`() {
        val preview = Preview(fullyQualifiedName = "com.example.MyPreview")
        assertEquals("com.example.MyPreview", preview.fullyQualifiedName)
        assertEquals(-1, preview.widthDp)
        assertEquals(-1, preview.heightDp)
        assertEquals(1f, preview.fontScale)
    }

    @Test
    fun `valid preview with custom dimensions`() {
        val preview = Preview(
            fullyQualifiedName = "com.example.MyPreview",
            widthDp = 360,
            heightDp = 640,
        )
        assertEquals(360, preview.widthDp)
        assertEquals(640, preview.heightDp)
    }

    @Test
    fun `blank fullyQualifiedName throws`() {
        assertFailsWith<IllegalArgumentException> {
            Preview(fullyQualifiedName = "")
        }
    }

    @Test
    fun `invalid widthDp throws`() {
        assertFailsWith<IllegalArgumentException> {
            Preview(fullyQualifiedName = "com.example.Test", widthDp = 0)
        }
    }

    @Test
    fun `negative widthDp throws`() {
        assertFailsWith<IllegalArgumentException> {
            Preview(fullyQualifiedName = "com.example.Test", widthDp = -2)
        }
    }

    @Test
    fun `invalid heightDp throws`() {
        assertFailsWith<IllegalArgumentException> {
            Preview(fullyQualifiedName = "com.example.Test", heightDp = 0)
        }
    }

    @Test
    fun `zero fontScale throws`() {
        assertFailsWith<IllegalArgumentException> {
            Preview(fullyQualifiedName = "com.example.Test", fontScale = 0f)
        }
    }

    @Test
    fun `negative fontScale throws`() {
        assertFailsWith<IllegalArgumentException> {
            Preview(fullyQualifiedName = "com.example.Test", fontScale = -1f)
        }
    }

    @Test
    fun `zero parameterProviderLimit throws`() {
        assertFailsWith<IllegalArgumentException> {
            Preview(fullyQualifiedName = "com.example.Test", parameterProviderLimit = 0)
        }
    }
}
