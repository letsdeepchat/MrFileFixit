package com.mrfilefixit.core.office

import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.DocTable
import com.mrfilefixit.core.domain.document.FrameRect
import com.mrfilefixit.core.domain.document.ListKind
import com.mrfilefixit.core.domain.document.Presentation
import com.mrfilefixit.core.domain.document.SheetCell
import com.mrfilefixit.core.domain.document.Slide
import com.mrfilefixit.core.domain.document.SlideShape
import com.mrfilefixit.core.office.pdftext.PdfGlyph
import com.mrfilefixit.core.office.pdftext.PdfPageText
import com.mrfilefixit.core.office.pdftext.TableExtraction
import com.mrfilefixit.core.office.pdftext.TextLayout
import com.mrfilefixit.core.office.pptx.PptxReader
import com.mrfilefixit.core.office.pptx.PptxWriter
import java.io.File
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SlidesAndLayoutTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `pptx round trip keeps frames, text and pictures`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 9, 9)
        val presentation = Presentation(
            Presentation.WIDE_WIDTH,
            Presentation.WIDE_HEIGHT,
            listOf(
                Slide(
                    backgroundArgb = 0xFF102030.toInt(),
                    shapes = listOf(
                        SlideShape.TextBox(FrameRect(40f, 30f, 400f, 60f), listOf(DocParagraph.of("Quarterly review"))),
                        SlideShape.Picture(FrameRect(500f, 100f, 200f, 100f), png, "image/png"),
                    ),
                ),
            ),
        )
        val file = File(dir, "deck.pptx")
        file.outputStream().use { PptxWriter().write(presentation, it) }
        val read = PptxReader().read(file)
        assertEquals(960f, read.widthPt)
        val shapes = read.slides.single().shapes
        val text = shapes.filterIsInstance<SlideShape.TextBox>().single()
        assertEquals("Quarterly review", text.paragraphs.single().plainText)
        assertEquals(FrameRect(40f, 30f, 400f, 60f), text.frame)
        assertArrayEquals(png, shapes.filterIsInstance<SlideShape.Picture>().single().bytes)
        assertEquals(0xFF102030.toInt(), read.slides.single().backgroundArgb)
    }

    private fun word(text: String, x: Float, y: Float, size: Float = 11f, bold: Boolean = false): List<PdfGlyph> {
        val advance = size * 0.5f
        return text.mapIndexed { i, c -> PdfGlyph(c.toString(), x + i * advance, y, advance, size, bold = bold) }
    }

    private fun line(text: String, x: Float, y: Float, size: Float = 11f, bold: Boolean = false): List<PdfGlyph> {
        var cursor = x
        return text.split(' ').flatMap { w ->
            word(w, cursor, y, size, bold).also { cursor += (w.length + 1) * size * 0.5f }
        }
    }

    @Test
    fun `pdf text becomes headings, paragraphs and bullets`() {
        val glyphs = line("Project Report", 72f, 80f, size = 24f, bold = true) +
            line("This report covers the first", 72f, 120f) +
            line("quarter of the year.", 72f, 134f) +
            line("• Revenue grew", 72f, 170f) +
            line("• Costs fell", 72f, 184f)
        val doc = TextLayout.toRichDocument(listOf(PdfPageText(595f, 842f, glyphs)))
        val paragraphs = doc.blocks.filterIsInstance<DocParagraph>()
        assertEquals(1, paragraphs[0].style.heading)
        assertEquals("Project Report", paragraphs[0].plainText)
        assertEquals("This report covers the first quarter of the year.", paragraphs[1].plainText)
        assertEquals(listOf("Revenue grew", "Costs fell"), paragraphs.drop(2).map { it.plainText })
        assertTrue(paragraphs.drop(2).all { it.style.list == ListKind.BULLET })
        assertTrue(doc.blocks.none { it is DocTable })
    }

    @Test
    fun `pdf table text becomes a grid with numbers`() {
        val glyphs = word("Item", 72f, 100f) + word("Qty", 250f, 100f) + word("Price", 400f, 100f) +
            word("Pens", 72f, 116f) + word("12", 250f, 116f) + word("1,250.50", 400f, 116f) +
            word("Ink", 72f, 132f) + word("(3)", 400f, 132f)
        val sheet = TableExtraction.sheet(PdfPageText(595f, 842f, glyphs), "Page 1")
        assertEquals(listOf("Item", "Qty", "Price"), sheet.rows[0].map { it.display })
        assertEquals(1_250.5, (sheet.rows[1][2] as SheetCell.Number).value)
        assertEquals(SheetCell.Empty, sheet.rows[2][1])
        assertEquals(-3.0, (sheet.rows[2][2] as SheetCell.Number).value)
    }

    @Test
    fun `a heading and paragraph above a table do not merge its columns`() {
        val glyphs = line("Annual Summary", 72f, 90f, size = 24f, bold = true) +
            line("Revenue rose in every region this year.", 72f, 130f) +
            word("Region", 72f, 200f) + word("Revenue", 250f, 200f) + word("Growth", 400f, 200f) +
            word("North", 72f, 216f) + word("1,250.50", 250f, 216f) + word("12%", 400f, 216f)
        val rows = TableExtraction.sheet(PdfPageText(595f, 842f, glyphs), "Page 1").rows
        val north = rows.first { it.firstOrNull()?.display == "North" }
        assertEquals(1_250.5, (north[1] as SheetCell.Number).value)
        assertEquals("Annual Summary", rows[0][0].display)
    }
}
