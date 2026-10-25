package com.mrfilefixit.core.processing.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.ocr.OcrLine
import com.mrfilefixit.core.domain.ocr.OcrPage
import com.mrfilefixit.core.domain.ocr.OcrResult
import com.mrfilefixit.core.domain.ocr.PixelBox
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.security.InputLimits
import com.mrfilefixit.core.processing.image.ImageDecoding
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

/** The searchable PDF keeps the page and adds text that a PDF reader can extract. */
@RunWith(AndroidJUnit4::class)
class SearchablePdfTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private val dispatchers = object : DispatcherProvider {
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val main: CoroutineDispatcher = Dispatchers.Main
    }
    private val exporter by lazy {
        val resolver = context.contentResolver
        val limits = InputLimits.DEFAULT
        val pages = OcrPageImages(resolver, ImageDecoding(resolver, limits), limits)
        AndroidOcrExporter(context, resolver, pages, limits, dispatchers)
    }

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "jobs/ocr-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun ref(file: File, mime: String) = FileRef(Uri.fromFile(file).toString(), file.name, mime, file.length())

    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrLine(text, PixelBox(left, top, right, bottom))

    private fun export(sources: List<FileRef>, result: OcrResult): File {
        val out = runBlocking { exporter.searchablePdf(sources, result, File(dir, "out").absolutePath, "scan") }
        assertTrue("export failed: $out", out is Result.Success)
        return File((out as Result.Success).value.path)
    }

    private fun text(pdf: File): String = PDDocument.load(pdf).use { PDFTextStripper().getText(it) }

    @Test
    fun imageBecomesAPageWithItsTextUnderneath() {
        val image = File(dir, "photo.png")
        val bitmap = createBitmap(1000, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val page = OcrPage(0, 0, 1000, 500, "Invoice 2045", listOf(line("Invoice 2045", 100, 100, 700, 160)))
        val pdf = export(listOf(ref(image, "image/png")), OcrResult(page.text, 1, listOf(page)))
        PDDocument.load(pdf).use { doc ->
            assertEquals(1, doc.numberOfPages)
            // A4 wide, in the image's proportions.
            assertEquals(PDRectangle.A4.width, doc.getPage(0).mediaBox.width, 0.5f)
            assertEquals(PDRectangle.A4.width / 2, doc.getPage(0).mediaBox.height, 0.5f)
        }
        assertTrue(text(pdf).contains("Invoice 2045"))
        // Nothing but the output is left in the job folder.
        assertEquals(listOf("scan.pdf"), File(dir, "out").list()?.toList())
    }

    @Test
    fun pdfPagesAreKeptAndGainTheRecognizedText() {
        val source = File(dir, "scan.pdf")
        PDDocument().use { doc ->
            repeat(2) { index ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 12f)
                    cs.newLineAtOffset(72f, 700f)
                    cs.showText("Original page ${index + 1}")
                    cs.endText()
                }
            }
            doc.save(source)
        }
        val pages = listOf(
            OcrPage(0, 0, 1448, 2048, "Recognized one", listOf(line("Recognized one", 200, 400, 900, 450))),
            OcrPage(0, 1, 1448, 2048, "Recognized two", listOf(line("Recognized two", 200, 400, 900, 450))),
        )
        val pdf = export(listOf(ref(source, "application/pdf")), OcrResult("x", 2, pages))
        val extracted = text(pdf)
        assertTrue(extracted, extracted.contains("Original page 1"))
        assertTrue(extracted, extracted.contains("Recognized two"))
        PDDocument.load(pdf).use { assertEquals(2, it.numberOfPages) }
    }
}
