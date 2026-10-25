package com.mrfilefixit.core.office.xml

/** Minimal streaming XML writer with correct escaping. Names are written as given (prefix:local). */
class XmlWriter {
    private val sb = StringBuilder(INITIAL_CAPACITY).append(DECLARATION)
    private val open = ArrayDeque<String>()
    private var tagOpen = false

    fun start(name: String, vararg attrs: Pair<String, Any?>): XmlWriter {
        closePendingTag()
        sb.append('<').append(name)
        writeAttributes(attrs)
        open.addLast(name)
        tagOpen = true
        return this
    }

    /** An element with no children: `<name a="1"/>`. */
    fun empty(name: String, vararg attrs: Pair<String, Any?>): XmlWriter {
        closePendingTag()
        sb.append('<').append(name)
        writeAttributes(attrs)
        sb.append("/>")
        return this
    }

    fun text(value: String): XmlWriter {
        closePendingTag()
        escape(value, attribute = false)
        return this
    }

    /** `<name attrs>text</name>`. */
    fun element(name: String, text: String, vararg attrs: Pair<String, Any?>): XmlWriter {
        start(name, *attrs)
        text(text)
        return end()
    }

    /** Writes pre-built, trusted markup (constant templates only, never user text). */
    fun raw(markup: String): XmlWriter {
        closePendingTag()
        sb.append(markup)
        return this
    }

    fun end(): XmlWriter {
        val name = open.removeLast()
        if (tagOpen) {
            sb.append("/>")
            tagOpen = false
        } else {
            sb.append("</").append(name).append('>')
        }
        return this
    }

    inline fun within(name: String, vararg attrs: Pair<String, Any?>, body: XmlWriter.() -> Unit): XmlWriter {
        start(name, *attrs)
        body()
        return end()
    }

    override fun toString(): String {
        check(open.isEmpty()) { "Unclosed elements: $open" }
        return sb.toString()
    }

    fun toBytes(): ByteArray = toString().toByteArray(Charsets.UTF_8)

    private fun writeAttributes(attrs: Array<out Pair<String, Any?>>) {
        for ((key, value) in attrs) {
            if (value == null) continue
            sb.append(' ').append(key).append("=\"")
            escape(value.toString(), attribute = true)
            sb.append('"')
        }
    }

    private fun closePendingTag() {
        if (tagOpen) {
            sb.append('>')
            tagOpen = false
        }
    }

    private fun escape(value: String, attribute: Boolean) {
        for (c in value) {
            when {
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                c == '&' -> sb.append("&amp;")
                c == '"' && attribute -> sb.append("&quot;")
                c == '\n' && attribute -> sb.append("&#10;")
                // XML 1.0 forbids most control characters, even escaped: drop them.
                c < ' ' && c != '\n' && c != '\t' && c != '\r' -> Unit
                c == '￾' || c == '￿' -> Unit
                else -> sb.append(c)
            }
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 4096
        const val DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    }
}
