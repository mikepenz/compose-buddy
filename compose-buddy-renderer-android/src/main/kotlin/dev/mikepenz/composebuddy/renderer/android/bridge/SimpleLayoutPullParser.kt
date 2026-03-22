package dev.mikepenz.composebuddy.renderer.android.bridge

import com.android.ide.common.rendering.api.ILayoutPullParser
import com.android.ide.common.rendering.api.ResourceNamespace
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Simple ILayoutPullParser wrapping an XML string.
 * Used to provide the root layout XML to the render session.
 */
class SimpleLayoutPullParser(xml: String) : ILayoutPullParser {

    private val parser: XmlPullParser = XmlPullParserFactory.newInstance().newPullParser().apply {
        setInput(StringReader(xml))
    }

    override fun getViewCookie(): Any? = null
    override fun getLayoutNamespace(): ResourceNamespace = ResourceNamespace.RES_AUTO

    // Delegate all XmlPullParser methods to the wrapped parser
    override fun setFeature(name: String?, state: Boolean) = parser.setFeature(name, state)
    override fun getFeature(name: String?): Boolean = parser.getFeature(name)
    override fun setProperty(name: String?, value: Any?) = parser.setProperty(name, value)
    override fun getProperty(name: String?): Any? = parser.getProperty(name)
    override fun setInput(reader: java.io.Reader?) = parser.setInput(reader)
    override fun setInput(inputStream: java.io.InputStream?, inputEncoding: String?) = parser.setInput(inputStream, inputEncoding)
    override fun getInputEncoding(): String? = parser.inputEncoding
    override fun defineEntityReplacementText(entityName: String?, replacementText: String?) = parser.defineEntityReplacementText(entityName, replacementText)
    override fun getNamespaceCount(depth: Int): Int = parser.getNamespaceCount(depth)
    override fun getNamespacePrefix(pos: Int): String? = parser.getNamespacePrefix(pos)
    override fun getNamespaceUri(pos: Int): String? = parser.getNamespaceUri(pos)
    override fun getNamespace(prefix: String?): String? = parser.getNamespace(prefix)
    override fun getDepth(): Int = parser.depth
    override fun getPositionDescription(): String? = parser.positionDescription
    override fun getLineNumber(): Int = parser.lineNumber
    override fun getColumnNumber(): Int = parser.columnNumber
    override fun isWhitespace(): Boolean = parser.isWhitespace
    override fun getText(): String? = parser.text
    override fun getTextCharacters(holderForStartAndLength: IntArray?): CharArray? = parser.getTextCharacters(holderForStartAndLength)
    override fun getNamespace(): String? = parser.namespace
    override fun getName(): String? = parser.name
    override fun getPrefix(): String? = parser.prefix
    override fun isEmptyElementTag(): Boolean = parser.isEmptyElementTag
    override fun getAttributeCount(): Int = parser.attributeCount
    override fun getAttributeNamespace(index: Int): String? = parser.getAttributeNamespace(index)
    override fun getAttributeName(index: Int): String? = parser.getAttributeName(index)
    override fun getAttributePrefix(index: Int): String? = parser.getAttributePrefix(index)
    override fun getAttributeType(index: Int): String? = parser.getAttributeType(index)
    override fun isAttributeDefault(index: Int): Boolean = parser.isAttributeDefault(index)
    override fun getAttributeValue(index: Int): String? = parser.getAttributeValue(index)
    override fun getAttributeValue(namespace: String?, name: String?): String? = parser.getAttributeValue(namespace, name)
    override fun getEventType(): Int = parser.eventType
    override fun next(): Int = parser.next()
    override fun nextToken(): Int = parser.nextToken()
    override fun require(type: Int, namespace: String?, name: String?) = parser.require(type, namespace, name)
    override fun nextText(): String? = parser.nextText()
    override fun nextTag(): Int = parser.nextTag()
}
