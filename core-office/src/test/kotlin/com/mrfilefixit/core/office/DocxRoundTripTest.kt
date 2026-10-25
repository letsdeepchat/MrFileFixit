package com.mrfilefixit.core.office

import com.mrfilefixit.core.domain.document.DocImage
import com.mrfilefixit.core.domain.document.DocPageBreak
import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.DocRun
import com.mrfilefixit.core.domain.document.DocTable
import com.mrfilefixit.core.domain.document.DocTableCell
import com.mrfilefixit.core.domain.document.DocTableRow
import com.mrfilefixit.core.domain.document.ListKind
import com.mrfilefixit.core.domain.document.PageSetup
import com.mrfilefixit.core.domain.document.ParagraphStyle
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.domain.document.TextAlign
import com.mrfilefixit.core.office.docx.DocxReader
import com.mrfilefixit.core.office.docx.DocxWriter
import java.io.File
import java.util.zip.ZipFile
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DocxRoundTripTest {
    @TempDir
    lateinit var dir: File

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)

    private val document = RichDocument(
        blocks = listOf(
            DocParagraph.of("Asha Verma", ParagraphStyle(heading = 1, align = TextAlign.CENTER)),
            DocParagraph(
                listOf(
                    DocRun("Bold ", bold = true),
                    DocRun("italic", italic = true, colorArgb = 0xFF1E5EFF.toInt()),
                    DocRun(" हिन्दी & <xml>", sizePt = 14f),
                    DocRun(" site", link = "https://example.org"),
                ),
                ParagraphStyle(shadingArgb = 0xFFEEEEEE.toInt(), bottomBorderArgb = 0xFF000000.toInt()),
            ),
            DocParagraph.of("First", ParagraphStyle(list = ListKind.BULLET)),
            DocParagraph.of("Second", ParagraphStyle(list = ListKind.BULLET, listLevel = 1)),
            DocParagraph.of("One", ParagraphStyle(list = ListKind.NUMBERED)),
            DocTable(
                rows = listOf(
                    DocTableRow(
                        listOf(
                            DocTableCell(listOf(DocParagraph.of("A1")), shadingArgb = 0xFFDDDDDD.toInt()),
                            DocTableCell(listOf(DocParagraph.of("B1"))),
                        ),
                        header = true,
                    ),
                    DocTableRow(listOf(DocTableCell(listOf(DocParagraph.of("A2"))), DocTableCell(emptyList()))),
                ),
                columnWeights = listOf(1f, 3f),
            ),
            DocImage(png, "image/png", 100f, 50f),
            DocPageBreak,
            DocParagraph.of("Line one\nLine two\tTabbed"),
        ),
        page = PageSetup(widthPt = PageSetup.LETTER_WIDTH, heightPt = PageSetup.LETTER_HEIGHT),
        title = "Resume",
        appData = """{"name":"Asha","x":"<&>"}""",
    )

    private fun roundTrip(doc: RichDocument): Pair<File, RichDocument> {
        val file = File(dir, "out.docx")
        file.outputStream().use { DocxWriter().write(doc, it) }
        return file to DocxReader().read(file)
    }

    @Test
    fun `text, formatting, lists, tables, images and page setup survive a round trip`() {
        val (_, read) = roundTrip(document)
        val paragraphs = read.blocks.filterIsInstance<DocParagraph>()
        assertEquals(1, paragraphs.first().style.heading)
        assertEquals(TextAlign.CENTER, paragraphs.first().style.align)
        val rich = paragraphs[1]
        assertEquals("Bold italic हिन्दी & <xml> site", rich.plainText)
        assertTrue(rich.runs[0].bold)
        assertEquals(0xFF1E5EFF.toInt(), rich.runs[1].colorArgb)
        assertEquals(14f, rich.runs[2].sizePt)
        assertEquals("https://example.org", rich.runs[3].link)
        assertEquals(0xFFEEEEEE.toInt(), rich.style.shadingArgb)
        assertEquals(0xFF000000.toInt(), rich.style.bottomBorderArgb)
        assertEquals(ListKind.BULLET, paragraphs[2].style.list)
        assertEquals(1, paragraphs[3].style.listLevel)
        assertEquals(ListKind.NUMBERED, paragraphs[4].style.list)

        val table = read.blocks.filterIsInstance<DocTable>().single()
        assertEquals(2, table.rows.size)
        assertTrue(table.rows[0].header)
        assertEquals(0xFFDDDDDD.toInt(), table.rows[0].cells[0].shadingArgb)
        assertEquals("B1", (table.rows[0].cells[1].blocks.single() as DocParagraph).plainText)
        assertEquals(3f, table.columnWeights[1] / table.columnWeights[0], 0.01f)

        val image = read.blocks.filterIsInstance<DocImage>().single()
        assertArrayEquals(png, image.bytes)
        assertEquals(100f, image.widthPt, 0.1f)
        assertTrue(DocPageBreak in read.blocks)
        assertEquals("Line one\nLine two\tTabbed", paragraphs.last().plainText)
        assertEquals(PageSetup.LETTER_WIDTH, read.page.widthPt, 0.1f)
        assertEquals(document.appData, read.appData)
    }

    @Test
    fun `package contains the parts Word requires`() {
        val (file, _) = roundTrip(document)
        val names = ZipFile(file).use { zip -> zip.entries().asSequence().map { it.name }.toSet() }
        listOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "word/document.xml",
            "word/styles.xml",
            "word/numbering.xml",
            "word/_rels/document.xml.rels",
            "docProps/core.xml",
            "word/media/image1.png",
        ).forEach { assertTrue(it in names, it) }
        val types = ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry("[Content_Types].xml")).readBytes() }
            .toString(Charsets.UTF_8)
        assertTrue("Extension=\"png\"" in types)
        assertTrue("/word/document.xml" in types)
    }

    @Test
    fun `numbered lists restart at one for each list`() {
        val doc = RichDocument(
            listOf(
                DocParagraph.of("a", ParagraphStyle(list = ListKind.NUMBERED)),
                DocParagraph.of("gap"),
                DocParagraph.of("b", ParagraphStyle(list = ListKind.NUMBERED)),
            ),
        )
        val (file, _) = roundTrip(doc)
        val body = ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry("word/document.xml")).readBytes() }
            .toString(Charsets.UTF_8)
        val ids = Regex("""<w:numId w:val="(\d+)"/>""").findAll(body).map { it.groupValues[1] }.toList()
        assertEquals(2, ids.distinct().size)
    }
}
