package com.mrfilefixit.core.office.docx

import com.mrfilefixit.core.domain.document.DocBlock
import com.mrfilefixit.core.domain.document.DocImage
import com.mrfilefixit.core.domain.document.DocPageBreak
import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.DocRun
import com.mrfilefixit.core.domain.document.DocTable
import com.mrfilefixit.core.domain.document.DocTableCell
import com.mrfilefixit.core.domain.document.DocTableRow
import com.mrfilefixit.core.domain.document.FontFamily
import com.mrfilefixit.core.domain.document.ListKind
import com.mrfilefixit.core.domain.document.PageSetup
import com.mrfilefixit.core.domain.document.ParagraphStyle
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.domain.document.TextAlign
import com.mrfilefixit.core.office.OfficeFormatException
import com.mrfilefixit.core.office.OfficeLimitException
import com.mrfilefixit.core.office.OfficeLimits
import com.mrfilefixit.core.office.xml.XmlReader
import com.mrfilefixit.core.office.zip.OpcPackage
import com.mrfilefixit.core.office.zip.Relationship
import java.io.File

/**
 * Reads a .docx into a [RichDocument]: paragraphs with bold/italic/underline/size/colour, headings, bullet and
 * numbered lists, tables (with shading and column widths), inline pictures, text boxes, hyperlinks and page
 * breaks. Headers, footers, footnotes, comments, charts and floating-shape positions are not carried over.
 */
class DocxReader(private val limits: OfficeLimits = OfficeLimits.DEFAULT) {

    fun read(file: File): RichDocument = OpcPackage(file, limits).use { pkg ->
        val main = pkg.rootPart("/officeDocument") ?: throw OfficeFormatException("Not a Word document")
        val xml = pkg.readXml(main) ?: throw OfficeFormatException("Missing document part")
        val rels = pkg.relationships(main)
        val stylesPart = rels.values.firstOrNull { it.type.endsWith("/styles") }?.target
        val styles = stylesPart?.let { pkg.readXml(it) }
        val numbering = rels.values.firstOrNull { it.type.endsWith("/numbering") }?.let { pkg.readXml(it.target) }
        val context = Context(
            pkg = pkg,
            rels = rels,
            headingStyles = DocxStyles.headingLevels(styles),
            styleLists = DocxStyles.styleLists(stylesPart?.let { pkg.readXml(it) }),
            listKinds = readNumbering(numbering),
        )
        val baseSize = stylesPart?.let { pkg.readXml(it) }?.let(::readDefaultSize) ?: RichDocument.DEFAULT_SIZE_PT
        val appData = rels.values.filter { it.type.endsWith("/customXml") }
            .firstNotNullOfOrNull { rel -> pkg.readXml(rel.target)?.let { DocxAppData.fromXml(it) } }
        val blocks = mutableListOf<DocBlock>()
        var page = PageSetup()
        while (xml.next() != XmlReader.Event.EOF) {
            if (xml.event == XmlReader.Event.START && xml.localName == "body") {
                xml.forEachChild { child ->
                    if (child.localName == "sectPr") page = readSection(child) else readBlock(child, context, blocks)
                }
            }
        }
        RichDocument(blocks = blocks, page = page, baseSizePt = baseSize, appData = appData)
    }

    private class Context(
        val pkg: OpcPackage,
        val rels: Map<String, Relationship>,
        val headingStyles: Map<String, Int>,
        /** Paragraph style id → (numId, level), for list styles. */
        val styleLists: Map<String, Pair<String, Int>>,
        /** (numId, level) → list kind. */
        val listKinds: Map<Pair<String, Int>, ListKind>,
        var blockCount: Int = 0,
    )

    private fun readBlock(xml: XmlReader, context: Context, into: MutableList<DocBlock>) {
        if (++context.blockCount > limits.maxBlocks) throw OfficeLimitException("Too many paragraphs")
        when (xml.localName) {
            "p" -> readParagraph(xml, context, into)
            "tbl" -> into += readTable(xml, context)
            "sdt" -> xml.forEachChild { c ->
                if (c.localName == "sdtContent") c.forEachChild { readBlock(it, context, into) }
            }
            "customXml", "ins", "smartTag" -> xml.forEachChild { readBlock(it, context, into) }
            else -> xml.skipElement()
        }
    }

    private class ParagraphBuilder {
        var style = ParagraphStyle(spaceAfterPt = 0f)
        val runs = mutableListOf<DocRun>()
        val after = mutableListOf<DocBlock>()
        var pageBreak = false
    }

    private fun readParagraph(xml: XmlReader, context: Context, into: MutableList<DocBlock>) {
        val p = ParagraphBuilder()
        xml.forEachChild { child ->
            when (child.localName) {
                "pPr" -> p.style = readParagraphProperties(child, context)
                else -> readInline(child, context, p, link = null)
            }
        }
        val hasText = p.runs.any { it.text.isNotEmpty() }
        if (hasText || p.after.isEmpty() && !p.pageBreak) into += DocParagraph(mergeRuns(p.runs), p.style)
        into += p.after
        if (p.pageBreak) into += DocPageBreak
    }

    private fun readInline(xml: XmlReader, context: Context, p: ParagraphBuilder, link: String?) {
        when (xml.localName) {
            "r" -> readRun(xml, context, p, link)
            "hyperlink" -> {
                val target = xml.attr("id")?.let { context.rels[it] }?.takeIf { it.external }?.target
                xml.forEachChild { readInline(it, context, p, target ?: link) }
            }
            "ins", "smartTag", "fldSimple", "customXml", "bdo", "dir" ->
                xml.forEachChild { readInline(it, context, p, link) }
            "sdt" -> xml.forEachChild { c ->
                if (c.localName == "sdtContent") c.forEachChild { readInline(it, context, p, link) }
            }
            else -> xml.skipElement()
        }
    }

    private fun readRun(xml: XmlReader, context: Context, p: ParagraphBuilder, link: String?) {
        var props = DocRun("")
        var hidden = false
        val text = StringBuilder()
        fun flush() {
            if (text.isNotEmpty()) {
                if (!hidden) p.runs += props.copy(text = text.toString(), link = link)
                text.clear()
            }
        }
        xml.forEachChild { child ->
            when (child.localName) {
                "rPr" -> readRunProperties(child).let { (run, isHidden) ->
                    props = run
                    hidden = isHidden
                }
                "t" -> text.append(child.readElementText())
                "tab", "ptab" -> text.append('\t')
                "cr" -> text.append('\n')
                "noBreakHyphen" -> text.append('‑')
                "softHyphen" -> Unit
                "br" -> if (child.attr("type") == "page") p.pageBreak = true else text.append('\n')
                "sym" -> child.attr("char")?.toIntOrNull(HEX)?.let { text.appendCodePoint(it) }
                "drawing", "pict", "object", "AlternateContent" -> {
                    flush()
                    readDrawing(child, context, p)
                }
                else -> child.skipElement()
            }
        }
        flush()
    }

    /** Pictures become image blocks after the paragraph; text boxes become paragraphs after it. */
    private fun readDrawing(xml: XmlReader, context: Context, p: ParagraphBuilder) {
        val startDepth = xml.depth
        var widthPt = 0f
        var heightPt = 0f
        var skipFallback = false
        while (xml.next() != XmlReader.Event.EOF) {
            if (xml.event == XmlReader.Event.END && xml.depth < startDepth) return
            if (xml.event != XmlReader.Event.START) continue
            when (xml.localName) {
                // AlternateContent repeats the picture as a VML fallback: take only the first choice.
                "Fallback" -> if (skipFallback) xml.skipElement()
                "Choice" -> skipFallback = true
                "extent" -> {
                    widthPt = emuToPt(xml.attr("cx"))
                    heightPt = emuToPt(xml.attr("cy"))
                }
                "blip" -> imageFor(xml.attr("embed"), context, widthPt, heightPt)?.let { p.after += it }
                "imagedata" -> imageFor(xml.attr("id"), context, widthPt, heightPt)?.let { p.after += it }
                "txbxContent" -> xml.forEachChild { readBlock(it, context, p.after) }
            }
        }
    }

    private fun imageFor(relId: String?, context: Context, widthPt: Float, heightPt: Float): DocImage? {
        val rel = relId?.let { context.rels[it] }?.takeIf { !it.external } ?: return null
        val mime = imageMime(rel.target) ?: return null
        val bytes = context.pkg.read(rel.target) ?: return null
        val w = if (widthPt > 0f) widthPt else DEFAULT_IMAGE_PT
        val h = if (heightPt > 0f) heightPt else DEFAULT_IMAGE_PT
        return DocImage(bytes, mime, w, h)
    }

    private fun readParagraphProperties(xml: XmlReader, context: Context): ParagraphStyle {
        var style = ParagraphStyle(spaceAfterPt = 0f)
        xml.forEachChild { c ->
            style = when (c.localName) {
                "pStyle" -> styleFromId(c.attr("val"), context, style)
                "outlineLvl" -> c.attr("val")?.toIntOrNull()?.takeIf { it in 0..MAX_OUTLINE }
                    ?.let { style.copy(heading = it + 1) } ?: style
                "keepNext" -> style.copy(keepWithNext = isOn(c))
                "jc" -> style.copy(align = alignment(c.attr("val")))
                "spacing" -> style.copy(
                    spaceBeforePt = twipsToPt(c.attr("before")) ?: style.spaceBeforePt,
                    spaceAfterPt = twipsToPt(c.attr("after")) ?: style.spaceAfterPt,
                    lineSpacing = lineSpacing(c) ?: style.lineSpacing,
                )
                "ind" -> style.copy(indentStartPt = twipsToPt(c.attr("left") ?: c.attr("start")) ?: 0f)
                "shd" -> style.copy(shadingArgb = parseColor(c.attr("fill")))
                "numPr" -> readNumPr(c, context, style)
                "pBdr" -> {
                    var color: Int? = null
                    c.forEachChild { edge ->
                        if (edge.localName == "bottom") color = parseColor(edge.attr("color")) ?: BLACK
                    }
                    style.copy(bottomBorderArgb = color)
                }
                else -> style
            }
        }
        return style
    }

    private fun styleFromId(id: String?, context: Context, style: ParagraphStyle): ParagraphStyle {
        val withHeading = style.copy(heading = context.headingStyles[id] ?: style.heading)
        val (numId, level) = context.styleLists[id] ?: return withHeading
        return listStyle(numId, level, context, withHeading)
    }

    private fun listStyle(numId: String, level: Int, context: Context, style: ParagraphStyle): ParagraphStyle {
        val kind = context.listKinds[numId to level] ?: context.listKinds[numId to 0] ?: ListKind.BULLET
        return style.copy(list = kind, listLevel = level)
    }

    private fun readNumPr(xml: XmlReader, context: Context, style: ParagraphStyle): ParagraphStyle {
        var level = 0
        var numId: String? = null
        xml.forEachChild { c ->
            when (c.localName) {
                "ilvl" -> level = c.attr("val")?.toIntOrNull() ?: 0
                "numId" -> numId = c.attr("val")
            }
        }
        val id = numId ?: return style
        // numId 0 switches off a list inherited from the paragraph style.
        return if (id == "0") style.copy(list = ListKind.NONE, listLevel = 0) else listStyle(id, level, context, style)
    }

    /** Run formatting, and whether the run is hidden text (`w:vanish`). */
    private fun readRunProperties(xml: XmlReader): Pair<DocRun, Boolean> {
        var run = DocRun("")
        var hidden = false
        xml.forEachChild { c ->
            if (c.localName == "vanish") hidden = isOn(c)
            run = when (c.localName) {
                "b" -> run.copy(bold = isOn(c))
                "i" -> run.copy(italic = isOn(c))
                "u" -> run.copy(underline = c.attr("val").let { it != null && it != "none" })
                "sz" -> c.attr("val")?.toFloatOrNull()?.let { run.copy(sizePt = it / 2f) } ?: run
                "color" -> run.copy(colorArgb = parseColor(c.attr("val")))
                "rFonts" -> run.copy(font = fontFamily(c.attr("ascii") ?: c.attr("hAnsi")))
                else -> run
            }
        }
        return run to hidden
    }

    private fun readTable(xml: XmlReader, context: Context): DocTable {
        val weights = mutableListOf<Float>()
        val rows = mutableListOf<DocTableRow>()
        xml.forEachChild { c ->
            when (c.localName) {
                "tblGrid" -> c.forEachChild { g -> g.attr("w")?.toFloatOrNull()?.let { weights += it } }
                "tr" -> rows += readRow(c, context)
            }
        }
        return DocTable(rows, weights)
    }

    private fun readRow(xml: XmlReader, context: Context): DocTableRow {
        val cells = mutableListOf<DocTableCell>()
        var header = false
        xml.forEachChild { c ->
            when (c.localName) {
                "trPr" -> c.forEachChild { if (it.localName == "tblHeader") header = isOn(it) }
                "tc" -> cells += readCell(c, context)
                "sdt" -> c.forEachChild { s ->
                    if (s.localName == "sdtContent") {
                        s.forEachChild { if (it.localName == "tc") cells += readCell(it, context) }
                    }
                }
            }
        }
        return DocTableRow(cells, header)
    }

    private fun readCell(xml: XmlReader, context: Context): List<DocTableCell> {
        val blocks = mutableListOf<DocBlock>()
        var shading: Int? = null
        var span = 1
        var mergedContinue = false
        xml.forEachChild { c ->
            when (c.localName) {
                "tcPr" -> c.forEachChild { prop ->
                    when (prop.localName) {
                        "shd" -> shading = parseColor(prop.attr("fill"))
                        "gridSpan" -> span = prop.attr("val")?.toIntOrNull()?.coerceIn(1, MAX_SPAN) ?: 1
                        "vMerge" -> mergedContinue = prop.attr("val") != "restart"
                    }
                }
                else -> readBlock(c, context, blocks)
            }
        }
        val first = DocTableCell(if (mergedContinue) emptyList() else blocks, shading)
        return listOf(first) + List(span - 1) { DocTableCell(emptyList(), shading) }
    }

    private fun readSection(xml: XmlReader): PageSetup {
        var page = PageSetup()
        xml.forEachChild { c ->
            when (c.localName) {
                "pgSz" -> {
                    val w = twipsToPt(c.attr("w"))
                    val h = twipsToPt(c.attr("h"))
                    if (w != null && h != null && w > MIN_PAGE && h > MIN_PAGE) {
                        page = page.copy(widthPt = w, heightPt = h)
                    }
                }
                "pgMar" -> page = page.copy(
                    marginTopPt = twipsToPt(c.attr("top"))?.let(::margin) ?: page.marginTopPt,
                    marginBottomPt = twipsToPt(c.attr("bottom"))?.let(::margin) ?: page.marginBottomPt,
                    marginStartPt = twipsToPt(c.attr("left"))?.let(::margin) ?: page.marginStartPt,
                    marginEndPt = twipsToPt(c.attr("right"))?.let(::margin) ?: page.marginEndPt,
                )
            }
        }
        return page
    }

    private fun readNumbering(xml: XmlReader?): Map<Pair<String, Int>, ListKind> {
        if (xml == null) return emptyMap()
        val abstractFormats = mutableMapOf<String, MutableMap<Int, ListKind>>()
        val numToAbstract = mutableMapOf<String, String>()
        while (xml.next() != XmlReader.Event.EOF) {
            val starts = xml.event == XmlReader.Event.START && xml.depth == 2
            when (if (starts) xml.localName else null) {
                "abstractNum" -> xml.attr("abstractNumId")?.let { id ->
                    val levels = abstractFormats.getOrPut(id) { mutableMapOf() }
                    xml.forEachChild { lvl ->
                        if (lvl.localName != "lvl") return@forEachChild
                        val level = lvl.attr("ilvl")?.toIntOrNull() ?: 0
                        lvl.forEachChild { f ->
                            if (f.localName == "numFmt") {
                                levels[level] = if (f.attr("val") == "bullet") ListKind.BULLET else ListKind.NUMBERED
                            }
                        }
                    }
                }
                "num" -> xml.attr("numId")?.let { id ->
                    xml.forEachChild { c ->
                        if (c.localName == "abstractNumId") c.attr("val")?.let { numToAbstract[id] = it }
                    }
                }
            }
        }
        return numToAbstract.flatMap { (num, abstract) ->
            abstractFormats[abstract].orEmpty().map { (level, kind) -> (num to level) to kind }
        }.toMap()
    }

    private fun readDefaultSize(xml: XmlReader): Float? {
        while (xml.next() != XmlReader.Event.EOF) {
            if (xml.event == XmlReader.Event.START && xml.localName == "rPrDefault") {
                var size: Float? = null
                xml.forEachChild { rPr ->
                    rPr.forEachChild { if (it.localName == "sz") size = it.attr("val")?.toFloatOrNull()?.div(2f) }
                }
                return size
            }
        }
        return null
    }

    companion object {
        private const val HEX = 16
        private const val MAX_OUTLINE = 5
        private const val MAX_SPAN = 64
        private const val MIN_PAGE = 72f
        private const val MAX_MARGIN = 200f
        private const val DEFAULT_IMAGE_PT = 144f
        private const val TWIPS_PER_PT = 20f
        private const val EMU_PER_PT = 12_700f
        private const val SINGLE_LINE = 240f
        private const val BLACK = -0x1000000
        private const val OPAQUE = -0x1000000
        private const val RGB_HEX_LENGTH = 6

        private fun isOn(xml: XmlReader): Boolean = xml.attr(
            "val",
        ).let { it == null || it == "1" || it == "true" || it == "on" }

        private fun twipsToPt(value: String?): Float? = value?.toFloatOrNull()?.div(TWIPS_PER_PT)

        private fun emuToPt(value: String?): Float = value?.toFloatOrNull()?.div(EMU_PER_PT) ?: 0f

        private fun margin(pt: Float): Float = pt.coerceIn(0f, MAX_MARGIN)

        private fun lineSpacing(xml: XmlReader): Float? {
            val line = xml.attr("line")?.toFloatOrNull() ?: return null
            val rule = xml.attr("lineRule")
            return if (rule == null || rule == "auto") (line / SINGLE_LINE).coerceIn(MIN_LINE, MAX_LINE) else null
        }

        private const val MIN_LINE = 0.8f
        private const val MAX_LINE = 3f

        private fun alignment(value: String?): TextAlign = when (value) {
            "center" -> TextAlign.CENTER
            "right", "end" -> TextAlign.END
            "both", "distribute" -> TextAlign.JUSTIFY
            else -> TextAlign.START
        }

        fun parseColor(value: String?): Int? {
            val hex = value?.takeIf { it.length == RGB_HEX_LENGTH && it != "auto" } ?: return null
            return hex.toIntOrNull(HEX)?.let { it or OPAQUE }
        }

        fun fontFamily(name: String?): FontFamily? {
            val n = name?.lowercase() ?: return null
            return when {
                "mono" in n || "courier" in n || "consolas" in n -> FontFamily.MONO
                "narrow" in n || "condensed" in n -> FontFamily.CONDENSED
                "times" in n || "georgia" in n || "garamond" in n || "cambria" in n || "serif" in n &&
                    "sans" !in n -> FontFamily.SERIF
                else -> null
            }
        }

        fun imageMime(partName: String): String? = when (partName.substringAfterLast('.').lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            else -> null // EMF/WMF/TIFF vector or legacy formats cannot be drawn on Android.
        }

        /** Joins adjacent runs with identical formatting. */
        fun mergeRuns(runs: List<DocRun>): List<DocRun> {
            val out = mutableListOf<DocRun>()
            for (run in runs) {
                val last = out.lastOrNull()
                if (last != null && last.copy(text = run.text) == run) {
                    out[out.size - 1] = last.copy(text = last.text + run.text)
                } else {
                    out += run
                }
            }
            return out
        }
    }
}
