package dev.mikepenz.composebuddy.core.semantics

import kotlin.test.Test
import kotlin.test.assertEquals

class SemanticsKeyMapperTest {

    // --- Key mapping ---

    @Test
    fun `maps Text key`() {
        assertEquals("text", SemanticsKeyMapper.mapKey("Text"))
        assertEquals("text", SemanticsKeyMapper.mapKey("SemanticsPropertyKey:Text"))
        assertEquals("text", SemanticsKeyMapper.mapKey("accessibilityKey: Text"))
        assertEquals("text", SemanticsKeyMapper.mapKey("SomeProp: Text"))
        assertEquals("text", SemanticsKeyMapper.mapKey("SomeProp:Text"))
    }

    @Test
    fun `maps ContentDescription key`() {
        assertEquals("contentDescription", SemanticsKeyMapper.mapKey("ContentDescription"))
        assertEquals("contentDescription", SemanticsKeyMapper.mapKey("SemanticsPropertyKey:ContentDescription"))
    }

    @Test
    fun `maps TestTag key`() {
        assertEquals("testTag", SemanticsKeyMapper.mapKey("TestTag"))
    }

    @Test
    fun `maps Role key case-insensitively`() {
        assertEquals("role", SemanticsKeyMapper.mapKey("Role"))
        assertEquals("role", SemanticsKeyMapper.mapKey("role"))
        assertEquals("role", SemanticsKeyMapper.mapKey("ROLE"))
    }

    @Test
    fun `maps onClick variants`() {
        assertEquals("onClick", SemanticsKeyMapper.mapKey("OnClick"))
        assertEquals("onClick", SemanticsKeyMapper.mapKey("onClick"))
    }

    @Test
    fun `maps OnLongClick`() {
        assertEquals("onLongClick", SemanticsKeyMapper.mapKey("OnLongClick"))
    }

    @Test
    fun `maps accessibility properties`() {
        assertEquals("editableText", SemanticsKeyMapper.mapKey("EditableText"))
        assertEquals("stateDescription", SemanticsKeyMapper.mapKey("StateDescription"))
        assertEquals("toggleableState", SemanticsKeyMapper.mapKey("ToggleableState"))
        assertEquals("selected", SemanticsKeyMapper.mapKey("Selected"))
        assertEquals("heading", SemanticsKeyMapper.mapKey("Heading"))
        assertEquals("focused", SemanticsKeyMapper.mapKey("Focused"))
        assertEquals("disabled", SemanticsKeyMapper.mapKey("Disabled"))
    }

    @Test
    fun `maps scroll and collection properties`() {
        assertEquals("progressRange", SemanticsKeyMapper.mapKey("ProgressBarRangeInfo"))
        assertEquals("horizontalScroll", SemanticsKeyMapper.mapKey("HorizontalScrollAxisRange"))
        assertEquals("verticalScroll", SemanticsKeyMapper.mapKey("VerticalScrollAxisRange"))
        assertEquals("collectionInfo", SemanticsKeyMapper.mapKey("CollectionInfo"))
        assertEquals("collectionItemInfo", SemanticsKeyMapper.mapKey("CollectionItemInfo"))
    }

    @Test
    fun `unknown key uses lowercase-first fallback`() {
        assertEquals("customProperty", SemanticsKeyMapper.mapKey("CustomProperty"))
        assertEquals("myWidget", SemanticsKeyMapper.mapKey("MyWidget"))
    }

    @Test
    fun `strips SemanticsPropertyKey prefix`() {
        assertEquals("customProperty", SemanticsKeyMapper.mapKey("SemanticsPropertyKey:CustomProperty"))
    }

    // --- Value formatting ---

    @Test
    fun `formats null as empty string`() {
        assertEquals("", SemanticsKeyMapper.formatValue(null))
    }

    @Test
    fun `formats simple string`() {
        assertEquals("hello", SemanticsKeyMapper.formatValue("hello"))
    }

    @Test
    fun `formats list of strings`() {
        assertEquals("a, b, c", SemanticsKeyMapper.formatValue(listOf("a", "b", "c")))
    }

    @Test
    fun `formats function as true`() {
        val fn: () -> Unit = {}
        assertEquals("true", SemanticsKeyMapper.formatValue(fn))
    }

    @Test
    fun `extracts text from AnnotatedString toString`() {
        assertEquals("Hello", SemanticsKeyMapper.formatValue("AnnotatedString(text=Hello, annotations=[])"))
    }

    @Test
    fun `extracts text from legacy AnnotatedString format`() {
        assertEquals("Hello", SemanticsKeyMapper.formatValue("AnnotatedString(Hello, [])"))
    }

    @Test
    fun `formats AccessibilityAction as true`() {
        assertEquals("true", SemanticsKeyMapper.formatValue("AccessibilityAction(label=null, action=...)"))
    }

    @Test
    fun `formats null string as empty`() {
        assertEquals("", SemanticsKeyMapper.formatValue("null"))
    }

    @Test
    fun `formats empty list string as empty`() {
        assertEquals("", SemanticsKeyMapper.formatValue("[]"))
    }
}
