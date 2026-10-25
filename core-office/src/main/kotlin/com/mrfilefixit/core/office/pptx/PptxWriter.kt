package com.mrfilefixit.core.office.pptx

import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.DocRun
import com.mrfilefixit.core.domain.document.FrameRect
import com.mrfilefixit.core.domain.document.Presentation
import com.mrfilefixit.core.domain.document.SlideShape
import com.mrfilefixit.core.domain.document.TextAlign
import com.mrfilefixit.core.domain.document.VerticalAnchor
import com.mrfilefixit.core.office.docx.DocxWriter
import com.mrfilefixit.core.office.xml.XmlWriter
import com.mrfilefixit.core.office.zip.OpcWriter
import com.mrfilefixit.core.office.zip.OpcWriter.RelEntry
import java.io.OutputStream

/**
 * Writes a [Presentation] as an editable .pptx with one blank layout. Text boxes keep their position,
 * size, fonts and colours; pictures are embedded as they are.
 */
class PptxWriter {
    fun write(presentation: Presentation, output: OutputStream) {
        val opc = OpcWriter(output)
        val presentationRels =
            mutableListOf(RelEntry("rId1", OpcWriter.REL_BASE + "slideMaster", "slideMasters/slideMaster1.xml"))
        presentation.slides.forEachIndexed { i, slide ->
            val media = mutableListOf<RelEntry>()
            val xml = slideXml(slide.backgroundArgb, slide.shapes) { bytes, mime ->
                val ext = if (mime == "image/png") "png" else "jpg"
                val name = "slide${i + 1}-image${media.size + 1}.$ext"
                opc.addMedia("ppt/media/$name", ext, mime, bytes)
                val id = "rId${media.size + 2}"
                media += RelEntry(id, OpcWriter.REL_BASE + "image", "../media/$name")
                id
            }
            opc.addPart("ppt/slides/slide${i + 1}.xml", CT_SLIDE, xml)
            val layout = RelEntry("rId1", OpcWriter.REL_BASE + "slideLayout", "../slideLayouts/slideLayout1.xml")
            val rels = listOf(layout) + media
            opc.addPart("ppt/slides/_rels/slide${i + 1}.xml.rels", null, OpcWriter.relationshipsXml(rels))
            presentationRels += RelEntry("rId${i + 2}", OpcWriter.REL_BASE + "slide", "slides/slide${i + 1}.xml")
        }
        val themeId = "rId${presentationRels.size + 1}"
        presentationRels += RelEntry(themeId, OpcWriter.REL_BASE + "theme", "theme/theme1.xml")
        presentationRels +=
            RelEntry("rId${presentationRels.size + 1}", OpcWriter.REL_BASE + "presProps", "presProps.xml")
        opc.addPart("ppt/presentation.xml", CT_PRESENTATION, presentationXml(presentation))
        opc.addPart("ppt/_rels/presentation.xml.rels", null, OpcWriter.relationshipsXml(presentationRels))
        opc.addPart("ppt/presProps.xml", CT_PRES_PROPS, PptxTemplates.presProps())
        opc.addPart("ppt/slideMasters/slideMaster1.xml", CT_MASTER, PptxTemplates.master())
        opc.addPart(
            "ppt/slideMasters/_rels/slideMaster1.xml.rels",
            null,
            OpcWriter.relationshipsXml(
                listOf(
                    RelEntry("rId1", OpcWriter.REL_BASE + "slideLayout", "../slideLayouts/slideLayout1.xml"),
                    RelEntry("rId2", OpcWriter.REL_BASE + "theme", "../theme/theme1.xml"),
                ),
            ),
        )
        opc.addPart("ppt/slideLayouts/slideLayout1.xml", CT_LAYOUT, PptxTemplates.layout())
        opc.addPart(
            "ppt/slideLayouts/_rels/slideLayout1.xml.rels",
            null,
            OpcWriter.relationshipsXml(
                listOf(RelEntry("rId1", OpcWriter.REL_BASE + "slideMaster", "../slideMasters/slideMaster1.xml")),
            ),
        )
        opc.addPart("ppt/theme/theme1.xml", CT_THEME, PptxTemplates.theme())
        opc.addPart("docProps/core.xml", OpcWriter.CT_CORE, OpcWriter.coreProperties("", ""))
        opc.addPart("docProps/app.xml", OpcWriter.CT_APP, OpcWriter.appProperties())
        opc.addRootRelationship(OpcWriter.REL_OFFICE_DOCUMENT, "ppt/presentation.xml")
        opc.addRootRelationship(OpcWriter.REL_CORE_PROPERTIES, "docProps/core.xml")
        opc.addRootRelationship(OpcWriter.REL_EXTENDED_PROPERTIES, "docProps/app.xml")
        opc.finish()
    }

    private fun presentationXml(presentation: Presentation) = XmlWriter().within(
        "p:presentation",
        "xmlns:a" to A_NS,
        "xmlns:r" to R_NS,
        "xmlns:p" to P_NS,
        "saveSubsetFonts" to 1,
    ) {
        within("p:sldMasterIdLst") { empty("p:sldMasterId", "id" to MASTER_ID, "r:id" to "rId1") }
        within("p:sldIdLst") {
            presentation.slides.indices.forEach { i ->
                empty(
                    "p:sldId",
                    "id" to FIRST_SLIDE_ID + i,
                    "r:id" to "rId${i + 2}",
                )
            }
        }
        empty("p:sldSz", "cx" to DocxWriter.emu(presentation.widthPt), "cy" to DocxWriter.emu(presentation.heightPt))
        empty("p:notesSz", "cx" to NOTES_CX, "cy" to NOTES_CY)
    }

    private fun slideXml(background: Int?, shapes: List<SlideShape>, addImage: (ByteArray, String) -> String) =
        XmlWriter().within("p:sld", "xmlns:a" to A_NS, "xmlns:r" to R_NS, "xmlns:p" to P_NS) {
            within("p:cSld") {
                if (background != null) {
                    within("p:bg") {
                        within("p:bgPr") {
                            solidFill(background)
                            empty("a:effectLst")
                        }
                    }
                }
                within("p:spTree") {
                    PptxTemplates.groupHeader(this)
                    shapes.forEachIndexed { i, shape -> shape(shape, i + 2, addImage) }
                }
            }
            within("p:clrMapOvr") { empty("a:masterClrMapping") }
        }

    private fun XmlWriter.shape(shape: SlideShape, id: Int, addImage: (ByteArray, String) -> String) {
        when (shape) {
            is SlideShape.Picture -> picture(shape, id, addImage(shape.bytes, shape.mimeType))
            is SlideShape.TextBox -> textBox(shape, id)
            is SlideShape.Box -> within("p:sp") {
                within("p:nvSpPr") {
                    empty("p:cNvPr", "id" to id, "name" to "Shape $id")
                    empty("p:cNvSpPr")
                    empty("p:nvPr")
                }
                shapeProperties(shape.frame, shape.fillArgb, shape.lineArgb)
            }
        }
    }

    private fun XmlWriter.picture(shape: SlideShape.Picture, id: Int, relId: String) {
        within("p:pic") {
            within("p:nvPicPr") {
                empty("p:cNvPr", "id" to id, "name" to "Picture $id")
                within("p:cNvPicPr") { empty("a:picLocks", "noChangeAspect" to 1) }
                empty("p:nvPr")
            }
            within("p:blipFill") {
                empty("a:blip", "r:embed" to relId)
                within("a:stretch") { empty("a:fillRect") }
            }
            within("p:spPr") {
                xfrm(shape.frame)
                within("a:prstGeom", "prst" to "rect") { empty("a:avLst") }
            }
        }
    }

    private fun XmlWriter.textBox(shape: SlideShape.TextBox, id: Int) {
        within("p:sp") {
            within("p:nvSpPr") {
                empty("p:cNvPr", "id" to id, "name" to "TextBox $id")
                empty("p:cNvSpPr", "txBox" to 1)
                empty("p:nvPr")
            }
            shapeProperties(shape.frame, shape.fillArgb, shape.lineArgb)
            within("p:txBody") {
                within(
                    "a:bodyPr",
                    "wrap" to "none",
                    "lIns" to 0,
                    "tIns" to 0,
                    "rIns" to 0,
                    "bIns" to 0,
                    "rtlCol" to 0,
                    "anchor" to when (shape.anchor) {
                        VerticalAnchor.TOP -> "t"
                        VerticalAnchor.MIDDLE -> "ctr"
                        VerticalAnchor.BOTTOM -> "b"
                    },
                ) { empty("a:noAutofit") }
                empty("a:lstStyle")
                val paragraphs = shape.paragraphs.ifEmpty { listOf(DocParagraph(emptyList())) }
                paragraphs.forEach { paragraph(it, shape.defaultSizePt) }
            }
        }
    }

    private fun XmlWriter.paragraph(paragraph: DocParagraph, defaultSize: Float) {
        within("a:p") {
            val align = when (paragraph.style.align) {
                TextAlign.START -> null
                TextAlign.CENTER -> "ctr"
                TextAlign.END -> "r"
                TextAlign.JUSTIFY -> "just"
            }
            if (align != null) empty("a:pPr", "algn" to align)
            paragraph.runs.forEach { run(it, defaultSize) }
            empty("a:endParaRPr", "lang" to "en-US", "sz" to hundredths(defaultSize), "dirty" to 0)
        }
    }

    private fun XmlWriter.run(run: DocRun, defaultSize: Float) {
        within("a:r") {
            within(
                "a:rPr",
                "lang" to "en-US",
                "sz" to hundredths(run.sizePt ?: defaultSize),
                "b" to if (run.bold) 1 else null,
                "i" to if (run.italic) 1 else null,
                "u" to if (run.underline) "sng" else null,
                "dirty" to 0,
            ) {
                run.colorArgb?.let { solidFill(it) }
                run.font?.let { empty("a:latin", "typeface" to it.docxName) }
            }
            element("a:t", run.text)
        }
    }

    private fun XmlWriter.shapeProperties(frame: FrameRect, fill: Int?, line: Int?) {
        within("p:spPr") {
            xfrm(frame)
            within("a:prstGeom", "prst" to "rect") { empty("a:avLst") }
            if (fill != null) solidFill(fill) else empty("a:noFill")
            if (line != null) within("a:ln", "w" to LINE_WIDTH) { solidFill(line) }
        }
    }

    private fun XmlWriter.xfrm(frame: FrameRect) {
        within("a:xfrm") {
            empty("a:off", "x" to DocxWriter.emu(frame.x), "y" to DocxWriter.emu(frame.y))
            empty(
                "a:ext",
                "cx" to DocxWriter.emu(frame.width.coerceAtLeast(1f)),
                "cy" to DocxWriter.emu(frame.height.coerceAtLeast(1f)),
            )
        }
    }

    private fun XmlWriter.solidFill(argb: Int) {
        within("a:solidFill") { empty("a:srgbClr", "val" to DocxWriter.hex(argb)) }
    }

    companion object {
        const val A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main"
        const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        const val P_NS = "http://schemas.openxmlformats.org/presentationml/2006/main"
        private const val CT_PRESENTATION =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"
        private const val CT_SLIDE = "application/vnd.openxmlformats-officedocument.presentationml.slide+xml"
        private const val CT_MASTER = "application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"
        private const val CT_LAYOUT = "application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"
        private const val CT_THEME = "application/vnd.openxmlformats-officedocument.theme+xml"
        private const val CT_PRES_PROPS = "application/vnd.openxmlformats-officedocument.presentationml.presProps+xml"
        private const val MASTER_ID = 2_147_483_648L
        private const val FIRST_SLIDE_ID = 256
        private const val NOTES_CX = 6_858_000
        private const val NOTES_CY = 9_144_000
        private const val LINE_WIDTH = 9_525
        private const val HUNDREDTHS = 100
        private const val MIN_SIZE = 100
        private const val MAX_SIZE = 400_000

        private fun hundredths(pt: Float): Int = (pt * HUNDREDTHS).toInt().coerceIn(MIN_SIZE, MAX_SIZE)
    }
}
