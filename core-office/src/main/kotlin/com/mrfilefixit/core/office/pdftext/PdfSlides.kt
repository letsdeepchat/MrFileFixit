package com.mrfilefixit.core.office.pdftext

import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.FrameRect
import com.mrfilefixit.core.domain.document.ParagraphStyle
import com.mrfilefixit.core.domain.document.Slide
import com.mrfilefixit.core.domain.document.SlideShape

/**
 * Builds an editable slide from a PDF page: the page drawn without its text becomes the slide picture,
 * and every line of text becomes its own text box at the same place, so the slide looks like the page
 * while its words stay editable.
 */
object PdfSlides {
    /** Text boxes get a little extra width because PowerPoint's fonts rarely match the PDF's exactly. */
    private const val WIDTH_SLACK = 1.12f
    private const val HEIGHT_FACTOR = 1.25f

    fun slide(page: PdfPageText, background: ByteArray?, backgroundMime: String = "image/jpeg"): Slide {
        val shapes = mutableListOf<SlideShape>()
        if (background != null) {
            shapes += SlideShape.Picture(FrameRect(0f, 0f, page.widthPt, page.heightPt), background, backgroundMime)
        }
        TextLayout.lines(page.glyphs, TextLayout.TEXT_SPLIT).forEach { line ->
            val runs = TextLayout.lineRuns(line)
            if (runs.isEmpty()) return@forEach
            val width = (line.width * WIDTH_SLACK).coerceAtMost(page.widthPt - line.x).coerceAtLeast(line.width)
            shapes += SlideShape.TextBox(
                frame = FrameRect(line.x, line.top, width, line.fontSize * HEIGHT_FACTOR),
                paragraphs = listOf(DocParagraph(runs, ParagraphStyle(spaceAfterPt = 0f, lineSpacing = 1f))),
                defaultSizePt = line.fontSize,
            )
        }
        return Slide(backgroundArgb = WHITE, shapes = shapes)
    }

    private const val WHITE = -0x1
}
