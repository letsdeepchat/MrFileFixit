package com.mrfilefixit.core.office.docx

import com.mrfilefixit.core.domain.document.ParagraphStyle
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.office.xml.XmlReader
import com.mrfilefixit.core.office.xml.XmlWriter

/** styles.xml and numbering.xml for [DocxWriter]. */
internal object DocxStyles {
    private const val BULLETS = "•◦▪•◦▪•◦▪"
    private const val LEVELS = 9
    private const val INDENT_STEP = 360
    private const val HANGING = 360
    private const val HEADING_SPACE_BEFORE = 240
    private const val HEADING_SPACE_AFTER = 80

    fun styles(document: RichDocument): XmlWriter {
        val font = DocxWriter.fontName(document.defaultFont)
        val base = document.baseSizePt
        return XmlWriter().within("w:styles", "xmlns:w" to DocxWriter.W_NS) {
            within("w:docDefaults") {
                within("w:rPrDefault") {
                    within("w:rPr") {
                        empty("w:rFonts", "w:ascii" to font, "w:hAnsi" to font, "w:cs" to font, "w:eastAsia" to font)
                        empty("w:color", "w:val" to DocxWriter.hex(document.textColor))
                        empty("w:sz", "w:val" to DocxWriter.halfPoints(base))
                        empty("w:szCs", "w:val" to DocxWriter.halfPoints(base))
                    }
                }
                within("w:pPrDefault") { within("w:pPr") { empty("w:spacing", "w:after" to 0) } }
            }
            within("w:style", "w:type" to "paragraph", "w:default" to 1, "w:styleId" to "Normal") {
                empty("w:name", "w:val" to "Normal")
                empty("w:qFormat")
            }
            for (level in 1..ParagraphStyle.MAX_HEADING) heading(level, base * ParagraphStyle.headingScale(level))
            within("w:style", "w:type" to "paragraph", "w:styleId" to "ListParagraph") {
                empty("w:name", "w:val" to "List Paragraph")
                empty("w:basedOn", "w:val" to "Normal")
            }
        }
    }

    private fun XmlWriter.heading(level: Int, sizePt: Float) {
        within("w:style", "w:type" to "paragraph", "w:styleId" to "Heading$level") {
            empty("w:name", "w:val" to "heading $level")
            empty("w:basedOn", "w:val" to "Normal")
            empty("w:next", "w:val" to "Normal")
            empty("w:qFormat")
            within("w:pPr") {
                empty("w:keepNext")
                empty("w:spacing", "w:before" to HEADING_SPACE_BEFORE, "w:after" to HEADING_SPACE_AFTER)
                empty("w:outlineLvl", "w:val" to level - 1)
            }
            within("w:rPr") {
                empty("w:b")
                empty("w:sz", "w:val" to DocxWriter.halfPoints(sizePt))
                empty("w:szCs", "w:val" to DocxWriter.halfPoints(sizePt))
            }
        }
    }

    /** Abstract 0 is bullets, abstract 1 decimal; num 1 is bullets and every id in [numberedIds] restarts at 1. */
    fun numbering(numberedIds: List<Int>): XmlWriter = XmlWriter().within("w:numbering", "xmlns:w" to DocxWriter.W_NS) {
        abstractNum(0) { level -> "bullet" to BULLETS[level].toString() }
        abstractNum(1) { level -> "decimal" to "%${level + 1}." }
        within("w:num", "w:numId" to DocxWriter.BULLET_NUM_ID) { empty("w:abstractNumId", "w:val" to 0) }
        numberedIds.forEach { id ->
            within("w:num", "w:numId" to id) {
                empty("w:abstractNumId", "w:val" to 1)
                within("w:lvlOverride", "w:ilvl" to 0) { empty("w:startOverride", "w:val" to 1) }
            }
        }
    }

    private inline fun XmlWriter.abstractNum(id: Int, format: (Int) -> Pair<String, String>) {
        within("w:abstractNum", "w:abstractNumId" to id) {
            empty("w:multiLevelType", "w:val" to "hybridMultilevel")
            for (level in 0 until LEVELS) {
                val (numFmt, text) = format(level)
                within("w:lvl", "w:ilvl" to level) {
                    empty("w:start", "w:val" to 1)
                    empty("w:numFmt", "w:val" to numFmt)
                    empty("w:lvlText", "w:val" to text)
                    empty("w:lvlJc", "w:val" to "left")
                    within("w:pPr") {
                        empty("w:ind", "w:left" to INDENT_STEP * (level + 2), "w:hanging" to HANGING)
                    }
                }
            }
        }
    }

    /** Heading level of each paragraph style id, read from styles.xml (outline level or "heading N" name). */
    fun headingLevels(xml: XmlReader?): Map<String, Int> {
        if (xml == null) return emptyMap()
        val result = mutableMapOf<String, Int>()
        while (xml.next() != XmlReader.Event.EOF) {
            if (!xml.isStart(DocxWriter.W_NS, "style")) continue
            val id = xml.attr("styleId") ?: continue
            var level: Int? = null
            xml.forEachChild { child ->
                when (child.localName) {
                    "name" -> level = level ?: headingFromName(child.attr("val"))
                    "pPr" -> child.forEachChild { p ->
                        if (p.localName == "outlineLvl") p.attr("val")?.toIntOrNull()?.let { level = it + 1 }
                    }
                }
            }
            level?.takeIf { it in 1..ParagraphStyle.MAX_HEADING }?.let { result[id] = it }
        }
        return result
    }

    /** Paragraph style id → (numId, level) for styles that make their paragraphs list items, like "List Bullet". */
    fun styleLists(xml: XmlReader?): Map<String, Pair<String, Int>> {
        if (xml == null) return emptyMap()
        val result = mutableMapOf<String, Pair<String, Int>>()
        while (xml.next() != XmlReader.Event.EOF) {
            val id = if (xml.isStart(DocxWriter.W_NS, "style")) xml.attr("styleId") else null
            id?.let { styleId ->
                xml.forEachChild { child ->
                    if (child.localName == "pPr") {
                        readStyleNumPr(
                            child,
                            styleId,
                            result,
                        )
                    }
                }
            }
        }
        return result
    }

    private fun readStyleNumPr(pPr: XmlReader, styleId: String, into: MutableMap<String, Pair<String, Int>>) {
        pPr.forEachChild { numPr ->
            if (numPr.localName != "numPr") return@forEachChild
            var numId: String? = null
            var level = 0
            numPr.forEachChild { c ->
                when (c.localName) {
                    "numId" -> numId = c.attr("val")
                    "ilvl" -> level = c.attr("val")?.toIntOrNull() ?: 0
                }
            }
            numId?.takeIf { it != "0" }?.let { into[styleId] = it to level }
        }
    }

    private fun headingFromName(name: String?): Int? {
        val n = name?.lowercase()?.trim() ?: return null
        return when {
            n == "title" -> 1
            n.startsWith("heading ") -> n.removePrefix("heading ").toIntOrNull()
            else -> null
        }
    }
}

/** Stores app data (a resume's JSON) as a custom XML part so a DOCX we exported can be re-opened for editing. */
internal object DocxAppData {
    const val NS = "urn:mrfilefixit:appdata:1"

    fun toXml(data: String): XmlWriter = XmlWriter().within("mff:appData", "xmlns:mff" to NS) { text(data) }

    fun fromXml(xml: XmlReader): String? {
        while (xml.next() != XmlReader.Event.EOF) {
            if (xml.isStart(NS, "appData")) return xml.readElementText()
        }
        return null
    }
}
