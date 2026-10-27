package com.mrfilefixit.core.processing.convert

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mrfilefixit.core.domain.convert.ConversionKind
import com.mrfilefixit.core.domain.convert.ConversionOptions
import com.mrfilefixit.core.domain.convert.ConversionRequest
import com.mrfilefixit.core.domain.convert.DocumentFormat
import com.mrfilefixit.core.domain.convert.SheetOutput
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.DocRun
import com.mrfilefixit.core.domain.document.DocTable
import com.mrfilefixit.core.domain.document.DocTableCell
import com.mrfilefixit.core.domain.document.DocTableRow
import com.mrfilefixit.core.domain.document.FrameRect
import com.mrfilefixit.core.domain.document.ListKind
import com.mrfilefixit.core.domain.document.ParagraphStyle
import com.mrfilefixit.core.domain.document.Presentation
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.domain.document.Sheet
import com.mrfilefixit.core.domain.document.SheetCell
import com.mrfilefixit.core.domain.document.Slide
import com.mrfilefixit.core.domain.document.SlideShape
import com.mrfilefixit.core.domain.document.Workbook
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.security.InputLimits
import com.mrfilefixit.core.office.docx.DocxReader
import com.mrfilefixit.core.office.docx.DocxWriter
import com.mrfilefixit.core.office.pptx.PptxReader
import com.mrfilefixit.core.office.pptx.PptxWriter
import com.mrfilefixit.core.office.xlsx.XlsxReader
import com.mrfilefixit.core.office.xlsx.XlsxWriter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every conversion direction on real Android graphics: office files made with :core-office's writers go to
 * PDF and are checked with PDFBox's text extraction; PDFs drawn with PDFBox go to Word, PowerPoint and Excel
 * and are read back with :core-office's readers.
 */
@RunWith(AndroidJUnit4::class)
class DocumentConverterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private val dispatchers = object : DispatcherProvider {
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val main: CoroutineDispatcher = Dispatchers.Main
    }
    private val converter by lazy {
        AndroidDocumentConverter(context, context.contentResolver, InputLimits.DEFAULT, dispatchers)
    }

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "convert-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun wordToPdfFlowsOntoSeveralPagesAndKeepsText() {
        val body = (1..60).map { DocParagraph.of("Paragraph $it explains the quarterly numbers in plain words.") }
        val table = DocTable(
            (1..40).map { r ->
                DocTableRow(listOf(cell("Row $r"), cell("${r * 10}")), header = r == 1)
            },
        )
        val doc = RichDocument(
            listOf(DocParagraph.of("Quarterly report", ParagraphStyle(heading = 1))) + body +
                DocParagraph.of("First point", ParagraphStyle(list = ListKind.BULLET)) +
                DocParagraph(listOf(DocRun("हिन्दी "), DocRun("bold", bold = true))) + table,
        )
        val source = File(dir, "report.docx").also { f -> f.outputStream().use { DocxWriter().write(doc, it) } }
        val pdf = convert(source, ConversionKind.WORD_TO_PDF, DocumentFormat.DOCX).single()
        PDDocument.load(File(pdf.path)).use { out ->
            assertTrue("expected several pages, got ${out.numberOfPages}", out.numberOfPages >= 3)
            val text = PDFTextStripper().getText(out)
            assertTrue(text.contains("Quarterly report"))
            assertTrue(text.contains("Paragraph 60 explains"))
            assertTrue(text.contains("Row 40"))
        }
    }

    @Test
    fun excelAndPowerPointToPdf() {
        val book = Workbook(
            listOf(
                Sheet(
                    "Fees",
                    listOf(
                        listOf(SheetCell.Text("Item", bold = true), SheetCell.Text("Amount", bold = true)),
                        listOf(SheetCell.Text("Tuition"), SheetCell.Number(12_500.0)),
                    ),
                ),
            ),
        )
        val xlsx = File(dir, "fees.xlsx").also { f -> f.outputStream().use { XlsxWriter().write(book, it) } }
        val sheetPdf = convert(xlsx, ConversionKind.EXCEL_TO_PDF, DocumentFormat.XLSX).single()
        PDDocument.load(File(sheetPdf.path)).use { assertTrue(PDFTextStripper().getText(it).contains("12500")) }

        val deck = Presentation(
            Presentation.WIDE_WIDTH,
            Presentation.WIDE_HEIGHT,
            (1..3).map { n ->
                val title = SlideShape.TextBox(FrameRect(60f, 60f, 600f, 80f), listOf(DocParagraph.of("Slide $n")))
                Slide(shapes = listOf(title))
            },
        )
        val pptx = File(dir, "deck.pptx").also { f -> f.outputStream().use { PptxWriter().write(deck, it) } }
        val slidesPdf = convert(pptx, ConversionKind.PPT_TO_PDF, DocumentFormat.PPTX).single()
        PDDocument.load(File(slidesPdf.path)).use { out ->
            assertEquals(3, out.numberOfPages)
            assertEquals(Presentation.WIDE_WIDTH, out.getPage(0).mediaBox.width, 1f)
            assertTrue(PDFTextStripper().getText(out).contains("Slide 3"))
        }
    }

    @Test
    fun pdfToWordFindsHeadingsAndParagraphs() {
        val pdf = textPdf()
        val docx = convert(pdf, ConversionKind.PDF_TO_WORD, DocumentFormat.PDF).single()
        val doc = DocxReader().read(File(docx.path))
        val paragraphs = doc.blocks.filterIsInstance<DocParagraph>()
        assertEquals("Annual Summary", paragraphs.first().plainText)
        assertTrue(paragraphs.first().style.heading in 1..2)
        assertTrue(paragraphs.any { it.plainText.contains("Revenue rose in every region") })
    }

    @Test
    fun pdfToExcelReadsTheTable() {
        val xlsx = convert(textPdf(), ConversionKind.PDF_TO_EXCEL, DocumentFormat.PDF).single()
        val rows = XlsxReader().read(File(xlsx.path)).sheets.single().rows
        val north = rows.first { row -> row.firstOrNull()?.display == "North" }
        assertEquals(1_250.5, (north[1] as SheetCell.Number).value, 0.001)

        val csv = convert(
            textPdf(),
            ConversionKind.PDF_TO_EXCEL,
            DocumentFormat.PDF,
            ConversionOptions(sheetOutput = SheetOutput.CSV),
        ).single()
        assertTrue(File(csv.path).readText().contains("North,"))
    }

    @Test
    fun pdfToPowerPointKeepsEditableText() {
        val pptx = convert(textPdf(), ConversionKind.PDF_TO_PPT, DocumentFormat.PDF).single()
        val deck = PptxReader().read(File(pptx.path))
        assertEquals(1, deck.slides.size)
        val shapes = deck.slides.single().shapes
        assertTrue(shapes.any { it is SlideShape.Picture })
        val texts = shapes.filterIsInstance<SlideShape.TextBox>()
            .map { box -> box.paragraphs.joinToString { it.plainText } }
        assertTrue(texts.any { it.contains("Annual Summary") })
    }

    @Test
    fun scannedPdfWithoutTextIsReported() {
        val blank = File(dir, "blank.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle.A4))
            doc.save(blank)
        }
        val result = runBlocking { converter.convert(request(blank, ConversionKind.PDF_TO_EXCEL, DocumentFormat.PDF)) }
        assertEquals(AppError.NoTextFound, (result as Result.Failure).error)
    }

    @Test
    fun corruptedFilesFailCleanlyAndLeaveNoEmptyOutput() {
        val cases = listOf(
            ConversionKind.WORD_TO_PDF to DocumentFormat.DOCX,
            ConversionKind.PPT_TO_PDF to DocumentFormat.PPTX,
            ConversionKind.EXCEL_TO_PDF to DocumentFormat.XLSX,
            ConversionKind.PDF_TO_WORD to DocumentFormat.PDF,
            ConversionKind.PDF_TO_PPT to DocumentFormat.PDF,
            ConversionKind.PDF_TO_EXCEL to DocumentFormat.PDF,
        )
        val contents = listOf(
            ByteArray(0),
            "not a real document".toByteArray(),
            // Valid magic number, truncated body.
            byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00),
            "%PDF-1.7\n1 0 obj <<".toByteArray(),
        )
        cases.forEach { (kind, format) ->
            contents.forEachIndexed { i, bytes ->
                val file = File(dir, "broken-$i.${format.extension}").apply { writeBytes(bytes) }
                val request = request(file, kind, format)
                val result = runBlocking { converter.convert(request, ProgressReporter.NONE) }
                assertTrue("$kind #$i: $result", result is Result.Failure)
                val written = File(request.outputDir).walkTopDown().filter { it.isFile }.toList()
                assertTrue("$kind #$i left $written", written.none { it.length() == 0L })
            }
        }
    }

    private fun cell(text: String) = DocTableCell(listOf(DocParagraph.of(text)))

    /** A one-page PDF: a 24 pt heading, a paragraph and a three-column table. */
    private fun textPdf(): File {
        val file = File(dir, "text-${System.nanoTime()}.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                fun text(x: Float, y: Float, size: Float, value: String, bold: Boolean = false) {
                    cs.beginText()
                    cs.setFont(if (bold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA, size)
                    cs.newLineAtOffset(x, page.mediaBox.height - y)
                    cs.showText(value)
                    cs.endText()
                }
                text(72f, 90f, 24f, "Annual Summary", bold = true)
                text(72f, 130f, 11f, "Revenue rose in every region this year.")
                text(72f, 200f, 11f, "Region", bold = true)
                text(250f, 200f, 11f, "Revenue", bold = true)
                text(400f, 200f, 11f, "Growth", bold = true)
                text(72f, 216f, 11f, "North")
                text(250f, 216f, 11f, "1,250.50")
                text(400f, 216f, 11f, "12%")
                text(72f, 232f, 11f, "South")
                text(250f, 232f, 11f, "980.00")
                text(400f, 232f, 11f, "8%")
            }
            doc.save(file)
        }
        return file
    }

    private fun request(
        source: File,
        kind: ConversionKind,
        format: DocumentFormat,
        options: ConversionOptions = ConversionOptions(),
    ) =
        ConversionRequest(
            source = FileRef(Uri.fromFile(source).toString(), source.name, format.mimeType, source.length()),
            kind = kind,
            outputDir = File(dir, "job-${System.nanoTime()}").absolutePath,
            outputBaseName = source.nameWithoutExtension,
            options = options,
        )

    private fun convert(
        source: File,
        kind: ConversionKind,
        format: DocumentFormat,
        options: ConversionOptions = ConversionOptions(),
    ): List<OutputFile> {
        val result = runBlocking { converter.convert(request(source, kind, format, options), ProgressReporter.NONE) }
        assertTrue("conversion failed: $result", result is Result.Success)
        return (result as Result.Success).value
    }
}
