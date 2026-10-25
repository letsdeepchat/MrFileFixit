package com.mrfilefixit.core.office.docx

import com.mrfilefixit.core.domain.document.DocBlock
import com.mrfilefixit.core.domain.document.DocImage
import com.mrfilefixit.core.domain.document.DocPageBreak
import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.DocRun
import com.mrfilefixit.core.domain.document.DocTable
import com.mrfilefixit.core.domain.document.FontFamily
import com.mrfilefixit.core.domain.document.ListKind
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.domain.document.TextAlign
import com.mrfilefixit.core.office.xml.XmlWriter
import com.mrfilefixit.core.office.zip.OpcWriter
import com.mrfilefixit.core.office.zip.OpcWriter.RelEntry
import java.io.OutputStream

/**
 * Writes a [RichDocument] as an editable Word document (Office Open XML, .docx).
 *
 * Headings use Word's built-in Heading 1-6 styles, so the navigation pane, tables of contents and applicant
 * tracking systems recognise them. Lists use real numbering definitions, not typed bullet characters.
 */
class DocxWriter {
    private val rels = mutableListOf<RelEntry>()
    private val media = mutableListOf<Triple<String, String, ByteArray>>()
    private val numbered = mutableListOf<Int>()
    private var drawingId = 0
    private var lastListKind = ListKind.NONE
    private var currentNumId = BULLET_NUM_ID

    fun write(document: RichDocument, output: OutputStream) {
        val opc = OpcWriter(output)
        rels += RelEntry(nextRelId(), OpcWriter.REL_BASE + "styles", "styles.xml")
        rels += RelEntry(nextRelId(), OpcWriter.REL_BASE + "numbering", "numbering.xml")
        val body = documentXml(document)
        media.forEach { (name, mime, bytes) ->
            opc.addMedia("word/media/$name", name.substringAfterLast('.'), mime, bytes)
        }
        document.appData?.let { data ->
            opc.addPart("customXml/item1.xml", null, DocxAppData.toXml(data))
            rels += RelEntry(nextRelId(), OpcWriter.REL_BASE + "customXml", "../customXml/item1.xml")
        }
        opc.addPart("word/document.xml", CT_DOCUMENT, body)
        opc.addPart("word/styles.xml", CT_STYLES, DocxStyles.styles(document))
        opc.addPart("word/numbering.xml", CT_NUMBERING, DocxStyles.numbering(numbered))
        opc.addPart("word/_rels/document.xml.rels", null, OpcWriter.relationshipsXml(rels))
        opc.addPart("docProps/core.xml", OpcWriter.CT_CORE, OpcWriter.coreProperties(document.title, document.author))
        opc.addPart("docProps/app.xml", OpcWriter.CT_APP, OpcWriter.appProperties())
        opc.addRootRelationship(OpcWriter.REL_OFFICE_DOCUMENT, "word/document.xml")
        opc.addRootRelationship(OpcWriter.REL_CORE_PROPERTIES, "docProps/core.xml")
        opc.addRootRelationship(OpcWriter.REL_EXTENDED_PROPERTIES, "docProps/app.xml")
        opc.finish()
    }

    private fun documentXml(document: RichDocument): XmlWriter = XmlWriter().within(
        "w:document",
        "xmlns:w" to W_NS,
        "xmlns:r" to R_NS,
        "xmlns:wp" to WP_NS,
        "xmlns:a" to A_NS,
        "xmlns:pic" to PIC_NS,
    ) {
        within("w:body") {
            document.blocks.forEach { block(it, document) }
            sectionProperties(document)
        }
    }

    private fun XmlWriter.block(block: DocBlock, document: RichDocument) {
        when (block) {
            is DocParagraph -> paragraph(block, document)
            is DocTable -> table(block, document)
            is DocImage -> within("w:p") {
                paragraphAlign(block.align)
                image(block)
            }
            DocPageBreak -> within("w:p") { within("w:r") { empty("w:br", "w:type" to "page") } }
        }
    }

    private fun XmlWriter.paragraph(p: DocParagraph, document: RichDocument) {
        val style = p.style
        within("w:p") {
            within("w:pPr") {
                if (style.heading > 0) empty("w:pStyle", "w:val" to "Heading${style.heading}")
                if (style.keepWithNext) empty("w:keepNext")
                listNumbering(style.list, style.listLevel)
                style.bottomBorderArgb?.let { color ->
                    within("w:pBdr") {
                        empty(
                            "w:bottom",
                            "w:val" to "single",
                            "w:sz" to BORDER_EIGHTHS,
                            "w:space" to 1,
                            "w:color" to hex(color),
                        )
                    }
                }
                style.shadingArgb?.let { empty("w:shd", "w:val" to "clear", "w:color" to "auto", "w:fill" to hex(it)) }
                empty(
                    "w:spacing",
                    "w:before" to twips(style.spaceBeforePt),
                    "w:after" to twips(style.spaceAfterPt),
                    "w:line" to (style.lineSpacing * SINGLE_LINE).toInt(),
                    "w:lineRule" to "auto",
                )
                if (style.indentStartPt > 0f && style.list == ListKind.NONE) {
                    empty("w:ind", "w:left" to twips(style.indentStartPt))
                }
                jc(style.align)
            }
            p.runs.forEach { run(it, document) }
        }
    }

    private fun XmlWriter.listNumbering(kind: ListKind, level: Int) {
        if (kind == ListKind.NONE) {
            lastListKind = ListKind.NONE
            return
        }
        if (kind != lastListKind) {
            // Each numbered list restarts at 1; bullets share one definition.
            currentNumId = if (kind == ListKind.BULLET) {
                BULLET_NUM_ID
            } else {
                (FIRST_NUMBERED_ID + numbered.size).also { numbered += it }
            }
            lastListKind = kind
        }
        within("w:numPr") {
            empty("w:ilvl", "w:val" to level.coerceIn(0, MAX_LEVEL))
            empty("w:numId", "w:val" to currentNumId)
        }
    }

    private fun XmlWriter.run(run: DocRun, document: RichDocument) {
        val link = run.link?.takeIf { url -> LINK_SCHEMES.any { url.startsWith(it) } }
        if (link != null) {
            val id = nextRelId()
            rels += RelEntry(id, OpcWriter.REL_BASE + "hyperlink", link, external = true)
            within("w:hyperlink", "r:id" to id) { textRun(run, document) }
        } else {
            textRun(run, document)
        }
    }

    private fun XmlWriter.textRun(run: DocRun, document: RichDocument) {
        within("w:r") {
            within("w:rPr") {
                run.font?.takeIf { it != document.defaultFont }?.let { font ->
                    empty(
                        "w:rFonts",
                        "w:ascii" to font.docxName,
                        "w:hAnsi" to font.docxName,
                        "w:cs" to font.docxName,
                    )
                }
                if (run.bold) empty("w:b")
                if (run.italic) empty("w:i")
                run.colorArgb?.let { empty("w:color", "w:val" to hex(it)) }
                run.sizePt?.let {
                    empty("w:sz", "w:val" to halfPoints(it))
                    empty("w:szCs", "w:val" to halfPoints(it))
                }
                if (run.underline) empty("w:u", "w:val" to "single")
            }
            runText(run.text)
        }
    }

    private fun XmlWriter.runText(text: String) {
        val lines = text.split('\n')
        lines.forEachIndexed { index, line ->
            if (index > 0) empty("w:br")
            val parts = line.split('\t')
            parts.forEachIndexed { i, part ->
                if (i > 0) empty("w:tab")
                if (part.isNotEmpty()) element("w:t", part, "xml:space" to "preserve")
            }
        }
    }

    private fun XmlWriter.table(table: DocTable, document: RichDocument) {
        val columns = table.columnCount.coerceAtLeast(1)
        val weights = List(columns) { table.columnWeights.getOrNull(it)?.coerceAtLeast(MIN_WEIGHT) ?: 1f }
        val total = weights.sum()
        val contentTwips = twips(document.page.contentWidthPt)
        val widths = weights.map { (it / total * contentTwips).toInt() }
        within("w:tbl") {
            within("w:tblPr") {
                empty("w:tblW", "w:w" to contentTwips, "w:type" to "dxa")
                within("w:tblBorders") {
                    TABLE_EDGES.forEach { edge ->
                        val color = table.borderArgb
                        if (color == null) {
                            empty("w:$edge", "w:val" to "nil")
                        } else {
                            empty(
                                "w:$edge",
                                "w:val" to "single",
                                "w:sz" to BORDER_EIGHTHS,
                                "w:space" to 0,
                                "w:color" to hex(color),
                            )
                        }
                    }
                }
                empty("w:tblLayout", "w:type" to "fixed")
            }
            within("w:tblGrid") { widths.forEach { empty("w:gridCol", "w:w" to it) } }
            table.rows.forEach { row ->
                within("w:tr") {
                    if (row.header) within("w:trPr") { empty("w:tblHeader") }
                    for (c in 0 until columns) {
                        val cell = row.cells.getOrNull(c)
                        within("w:tc") {
                            within("w:tcPr") {
                                empty("w:tcW", "w:w" to widths[c], "w:type" to "dxa")
                                cell?.shadingArgb?.let {
                                    empty("w:shd", "w:val" to "clear", "w:color" to "auto", "w:fill" to hex(it))
                                }
                                cell?.let { cellMargins(it.paddingPt) }
                            }
                            val blocks = cell?.blocks.orEmpty()
                            blocks.forEach { block(it, document) }
                            // A cell must end with a paragraph.
                            if (blocks.lastOrNull() !is DocParagraph) empty("w:p")
                        }
                    }
                }
            }
        }
        // Word merges two adjacent tables; an empty paragraph keeps them apart.
        empty("w:p")
    }

    private fun XmlWriter.cellMargins(paddingPt: Float) {
        val pad = twips(paddingPt)
        within("w:tcMar") {
            listOf("top", "left", "bottom", "right").forEach { empty("w:$it", "w:w" to pad, "w:type" to "dxa") }
        }
    }

    private fun XmlWriter.paragraphAlign(align: TextAlign) {
        within("w:pPr") { jc(align) }
    }

    private fun XmlWriter.jc(align: TextAlign) {
        val value = when (align) {
            TextAlign.START -> return
            TextAlign.CENTER -> "center"
            TextAlign.END -> "right"
            TextAlign.JUSTIFY -> "both"
        }
        empty("w:jc", "w:val" to value)
    }

    private fun XmlWriter.image(image: DocImage) {
        val extension = if (image.mimeType == "image/png") "png" else "jpg"
        drawingId++
        val name = "image$drawingId.$extension"
        media += Triple(name, image.mimeType, image.bytes)
        val relId = nextRelId()
        rels += RelEntry(relId, OpcWriter.REL_BASE + "image", "media/$name")
        val cx = emu(image.widthPt)
        val cy = emu(image.heightPt)
        within("w:r") {
            within("w:drawing") {
                within("wp:inline", "distT" to 0, "distB" to 0, "distL" to 0, "distR" to 0) {
                    empty("wp:extent", "cx" to cx, "cy" to cy)
                    empty("wp:docPr", "id" to drawingId, "name" to "Picture $drawingId")
                    within("wp:cNvGraphicFramePr") { empty("a:graphicFrameLocks", "noChangeAspect" to 1) }
                    within("a:graphic") {
                        within("a:graphicData", "uri" to PIC_NS) {
                            picture(name, relId, cx, cy)
                        }
                    }
                }
            }
        }
    }

    private fun XmlWriter.picture(name: String, relId: String, cx: Long, cy: Long) {
        within("pic:pic") {
            within("pic:nvPicPr") {
                empty("pic:cNvPr", "id" to 0, "name" to name)
                empty("pic:cNvPicPr")
            }
            within("pic:blipFill") {
                empty("a:blip", "r:embed" to relId)
                within("a:stretch") { empty("a:fillRect") }
            }
            within("pic:spPr") {
                within("a:xfrm") {
                    empty("a:off", "x" to 0, "y" to 0)
                    empty("a:ext", "cx" to cx, "cy" to cy)
                }
                within("a:prstGeom", "prst" to "rect") { empty("a:avLst") }
            }
        }
    }

    private fun XmlWriter.sectionProperties(document: RichDocument) {
        val page = document.page
        within("w:sectPr") {
            empty("w:pgSz", "w:w" to twips(page.widthPt), "w:h" to twips(page.heightPt))
            empty(
                "w:pgMar",
                "w:top" to twips(page.marginTopPt),
                "w:right" to twips(page.marginEndPt),
                "w:bottom" to twips(page.marginBottomPt),
                "w:left" to twips(page.marginStartPt),
                "w:header" to HEADER_TWIPS,
                "w:footer" to HEADER_TWIPS,
                "w:gutter" to 0,
            )
        }
    }

    private fun nextRelId(): String = "rId${rels.size + 1}"

    companion object {
        const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        const val WP_NS = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
        const val A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main"
        const val PIC_NS = "http://schemas.openxmlformats.org/drawingml/2006/picture"
        private const val CT_DOCUMENT =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
        private const val CT_STYLES = "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"
        private const val CT_NUMBERING =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"
        const val BULLET_NUM_ID = 1
        private const val FIRST_NUMBERED_ID = 2
        private const val MAX_LEVEL = 8
        private const val SINGLE_LINE = 240
        private const val BORDER_EIGHTHS = 6
        private const val HEADER_TWIPS = 708
        private const val MIN_WEIGHT = 0.01f
        private const val TWIPS_PER_PT = 20
        private const val EMU_PER_PT = 12_700L
        private const val RGB_MASK = 0xFFFFFF
        private val LINK_SCHEMES = listOf("https://", "http://", "mailto:")
        private val TABLE_EDGES = listOf("top", "left", "bottom", "right", "insideH", "insideV")

        fun twips(pt: Float): Int = (pt * TWIPS_PER_PT).toInt()

        fun halfPoints(pt: Float): Int = (pt * 2).toInt().coerceAtLeast(2)

        fun emu(pt: Float): Long = (pt * EMU_PER_PT).toLong()

        fun hex(argb: Int): String = "%06X".format(argb and RGB_MASK)

        /** Word name of a family, used by styles. */
        fun fontName(font: FontFamily): String = font.docxName
    }
}
