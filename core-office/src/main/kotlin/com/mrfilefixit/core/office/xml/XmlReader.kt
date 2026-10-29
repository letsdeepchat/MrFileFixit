package com.mrfilefixit.core.office.xml

import com.mrfilefixit.core.office.OfficeFormatException
import com.mrfilefixit.core.office.OfficeLimitException

/**
 * A small, strict pull parser for the XML inside Office files.
 *
 * It resolves namespaces, decodes the five predefined entities and character references, and refuses
 * DOCTYPE declarations outright: Office XML never uses them, and refusing them rules out external
 * entities (XXE) and entity-expansion bombs without relying on a platform parser's settings. Nesting is capped
 * at [MAX_DEPTH]: real Office files stay far below it, and deeper input would overflow a worker thread's stack in
 * the recursive readers and make namespace lookups quadratic.
 */
class XmlReader(private val input: String) {
    enum class Event { START, END, TEXT, EOF }

    private var pos = 0
    private val nsStack = ArrayDeque<Map<String, String>>()
    private var pendingEnd = false
    private var attributes: List<Attribute> = emptyList()

    var event: Event = Event.EOF
        private set

    /** Local name of the current START or END element. */
    var localName: String = ""
        private set

    /** Namespace URI of the current START or END element ("" when none). */
    var namespace: String = ""
        private set

    /** Text of the current TEXT event, entities decoded. */
    var text: String = ""
        private set

    /** Element depth: 1 inside the root element. */
    var depth: Int = 0
        private set

    private class Attribute(val namespace: String, val localName: String, val value: String)

    fun next(): Event {
        if (pendingEnd) {
            pendingEnd = false
            nsStack.removeLast()
            depth--
            event = Event.END
            return event
        }
        while (pos < input.length) {
            if (input[pos] != '<') return readText()
            when {
                input.startsWith("<!--", pos) -> skipPast("-->")
                input.startsWith("<![CDATA[", pos) -> return readCdata()
                input.startsWith("<?", pos) -> skipPast("?>")
                input.startsWith("<!", pos) -> throw OfficeFormatException("DOCTYPE is not allowed")
                input.startsWith("</", pos) -> return readEndTag()
                else -> return readStartTag()
            }
        }
        event = Event.EOF
        return event
    }

    /** Attribute value by local name, in no namespace or any namespace when [anyNamespace]. */
    fun attr(name: String, anyNamespace: Boolean = true): String? =
        attributes.firstOrNull { it.localName == name && (anyNamespace || it.namespace.isEmpty()) }?.value

    fun attr(namespace: String, name: String): String? =
        attributes.firstOrNull { it.localName == name && it.namespace == namespace }?.value

    fun isStart(ns: String, name: String): Boolean = event == Event.START && localName == name && namespace == ns

    fun isEnd(ns: String, name: String): Boolean = event == Event.END && localName == name && namespace == ns

    /** Skips the current element and everything inside it. Call on a START event. */
    fun skipElement() {
        val target = depth - 1
        while (depth > target) {
            if (next() == Event.EOF) return
        }
    }

    /** Concatenated text inside the current element; leaves the reader on its END event. */
    fun readElementText(): String {
        val target = depth - 1
        val sb = StringBuilder()
        while (next() != Event.EOF) {
            if (event == Event.TEXT) sb.append(text)
            if (event == Event.END && depth == target) break
        }
        return sb.toString()
    }

    /**
     * Calls [onChild] for each direct child element of the element whose START was just read, then stops on
     * its END. [onChild] may read further, as long as it consumes the child it was given.
     */
    inline fun forEachChild(onChild: (XmlReader) -> Unit) {
        val parentDepth = depth
        while (next() != Event.EOF) {
            if (event == Event.END && depth < parentDepth) return
            if (event == Event.START && depth == parentDepth + 1) {
                onChild(this)
                if (event == Event.START && depth == parentDepth + 1) skipElement()
            }
        }
    }

    private fun readText(): Event {
        val end = input.indexOf('<', pos).let { if (it < 0) input.length else it }
        text = decodeEntities(input.substring(pos, end))
        pos = end
        event = Event.TEXT
        return event
    }

    private fun readCdata(): Event {
        val end = input.indexOf("]]>", pos)
        if (end < 0) throw OfficeFormatException("Unclosed CDATA")
        text = input.substring(pos + CDATA_OPEN, end)
        pos = end + CDATA_CLOSE
        event = Event.TEXT
        return event
    }

    private fun skipPast(marker: String) {
        val end = input.indexOf(marker, pos)
        if (end < 0) throw OfficeFormatException("Unclosed markup")
        pos = end + marker.length
    }

    private fun readEndTag(): Event {
        val end = input.indexOf('>', pos)
        if (end < 0) throw OfficeFormatException("Unclosed end tag")
        val qName = input.substring(pos + 2, end).trim()
        pos = end + 1
        resolveElementName(qName)
        nsStack.removeLastOrNull() ?: throw OfficeFormatException("Unbalanced end tag")
        depth--
        attributes = emptyList()
        event = Event.END
        return event
    }

    private fun readStartTag(): Event {
        pos++ // '<'
        val nameEnd = scanName()
        val qName = input.substring(pos, nameEnd)
        pos = nameEnd
        val raw = mutableListOf<Pair<String, String>>()
        var selfClosing = false
        while (true) {
            skipWhitespace()
            if (pos >= input.length) throw OfficeFormatException("Unclosed start tag")
            val c = input[pos]
            if (c == '>') {
                pos++
                break
            }
            if (c == '/' && input.startsWith("/>", pos)) {
                pos += 2
                selfClosing = true
                break
            }
            raw += readAttribute()
        }
        val declared = raw.filter { (name, _) -> name == "xmlns" || name.startsWith("xmlns:") }
            .associate { (name, value) -> (if (name == "xmlns") "" else name.substringAfter(':')) to value }
        if (depth >= MAX_DEPTH) throw OfficeLimitException("Elements nested too deeply")
        nsStack.addLast(declared)
        depth++
        resolveElementName(qName)
        attributes = raw.filterNot { (name, _) -> name == "xmlns" || name.startsWith("xmlns:") }.map { (name, value) ->
            val prefix = name.substringBefore(':', "")
            Attribute(if (prefix.isEmpty()) "" else lookup(prefix), name.substringAfter(':'), value)
        }
        pendingEnd = selfClosing
        event = Event.START
        return event
    }

    private fun readAttribute(): Pair<String, String> {
        val nameEnd = scanName()
        val name = input.substring(pos, nameEnd)
        pos = nameEnd
        skipWhitespace()
        if (pos >= input.length || input[pos] != '=') throw OfficeFormatException("Malformed attribute")
        pos++
        skipWhitespace()
        val quote = input.getOrNull(pos)
        if (quote != '"' && quote != '\'') throw OfficeFormatException("Unquoted attribute")
        val end = input.indexOf(quote, pos + 1)
        if (end < 0) throw OfficeFormatException("Unclosed attribute")
        val value = decodeEntities(input.substring(pos + 1, end))
        pos = end + 1
        return name to value
    }

    private fun resolveElementName(qName: String) {
        val prefix = qName.substringBefore(':', "")
        localName = qName.substringAfter(':')
        namespace = lookup(prefix)
    }

    private fun lookup(prefix: String): String {
        if (prefix == "xml") return XML_NS
        for (i in nsStack.indices.reversed()) nsStack[i][prefix]?.let { return it }
        return ""
    }

    private fun scanName(): Int {
        var end = pos
        while (end < input.length && !input[end].isWhitespace() && input[end] !in NAME_STOPS) end++
        if (end == pos) throw OfficeFormatException("Empty name")
        return end
    }

    private fun skipWhitespace() {
        while (pos < input.length && input[pos].isWhitespace()) pos++
    }

    companion object {
        const val XML_NS = "http://www.w3.org/XML/1998/namespace"

        /** Deepest element nesting accepted. Word, PowerPoint and Excel files stay well under 100. */
        const val MAX_DEPTH = 256
        private const val CDATA_OPEN = 9
        private const val CDATA_CLOSE = 3
        private const val HEX = 16
        private val NAME_STOPS = charArrayOf('>', '/', '=')

        /** Decodes XML entities and character references; unknown entities are kept literally. */
        fun decodeEntities(raw: String): String {
            if ('&' !in raw) return raw
            val sb = StringBuilder(raw.length)
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                val semi = if (c == '&') raw.indexOf(';', i) else -1
                if (semi < 0) {
                    sb.append(c)
                    i++
                    continue
                }
                val entity = raw.substring(i + 1, semi)
                val decoded = decodeEntity(entity)
                if (decoded == null) sb.append(c) else sb.append(decoded)
                i = if (decoded == null) i + 1 else semi + 1
            }
            return sb.toString()
        }

        private fun decodeEntity(entity: String): String? = when {
            entity == "lt" -> "<"
            entity == "gt" -> ">"
            entity == "amp" -> "&"
            entity == "quot" -> "\""
            entity == "apos" -> "'"
            entity.startsWith("#x") -> entity.substring(2).toIntOrNull(HEX)?.let(::codePoint)
            entity.startsWith("#") -> entity.substring(1).toIntOrNull()?.let(::codePoint)
            else -> null
        }

        private fun codePoint(cp: Int): String? =
            if (Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else null
    }
}
