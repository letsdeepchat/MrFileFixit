package com.mrfilefixit.core.processing.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.pdf.CompressionLevel
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.security.InputLimits
import com.mrfilefixit.core.processing.io.InputRejectedException
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The compressor's promise, checked on five generated PDFs (text-heavy, image-heavy, scanned, mixed and
 * metadata-heavy): under Medium and Strong the output is always smaller than the source, text pages keep
 * their text, and a PDF that cannot shrink is reported instead of being written out bigger.
 */
@RunWith(AndroidJUnit4::class)
class PdfCompressorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private val compressor = PdfCompressor(InputLimits.DEFAULT) {
        PDDocument.load(it, MemoryUsageSetting.setupTempFileOnly())
    }

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "pdf-compressor-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun textHeavyPdfShrinksAndKeepsItsText() {
        val source = textHeavy()
        listOf(CompressionLevel.MEDIUM, CompressionLevel.HIGH).forEach { level ->
            val out = compress(source, level)
            assertSmaller(source, out, level)
            // Text pages must not have been rasterized: the font is still there.
            PDDocument.load(out).use { doc -> assertTrue(doc.getPage(0).resources.fontNames.iterator().hasNext()) }
        }
    }

    @Test
    fun imageHeavyPdfShrinks() = assertShrinksAtMediumAndStrong(imageHeavy())

    @Test
    fun scannedPdfShrinks() = assertShrinksAtMediumAndStrong(scanned())

    @Test
    fun mixedPdfShrinks() = assertShrinksAtMediumAndStrong(mixed())

    @Test
    fun metadataHeavyPdfShrinksAndLosesItsMetadata() {
        val source = withMetadata()
        assertShrinksAtMediumAndStrong(source)
        val out = compress(source, CompressionLevel.MEDIUM)
        PDDocument.load(out).use { doc -> assertEquals(null, doc.documentInformation.author) }
    }

    @Test
    fun grayscaleOptionStillShrinks() {
        val source = scanned()
        val out = File(dir, "gray.pdf")
        runBlocking { compressor.compress(source, CompressionLevel.HIGH, true, out, ProgressReporter.NONE) }
        assertSmaller(source, out, CompressionLevel.HIGH)
    }

    @Test
    fun pdfThatCannotShrinkIsReportedAndNothingBiggerIsWritten() {
        val source = tiny()
        val out = File(dir, "tiny-out.pdf")
        try {
            runBlocking { compressor.compress(source, CompressionLevel.MEDIUM, false, out, ProgressReporter.NONE) }
            assertSmaller(source, out, CompressionLevel.MEDIUM)
        } catch (e: InputRejectedException) {
            assertEquals(AppError.AlreadyOptimized, e.error)
            assertFalse("No output may be left behind", out.exists() && out.length() >= source.length())
        }
        val leftovers = dir.listFiles().orEmpty().filter { it.name.endsWith(".tmp") }
        assertTrue("Temp candidates must be deleted: $leftovers", leftovers.isEmpty())
    }

    private fun assertShrinksAtMediumAndStrong(source: File) {
        listOf(CompressionLevel.MEDIUM, CompressionLevel.HIGH).forEach { level ->
            assertSmaller(source, compress(source, level), level)
        }
    }

    private fun compress(source: File, level: CompressionLevel): File {
        val out = File(dir, "${source.nameWithoutExtension}-${level.name}.pdf")
        runBlocking { compressor.compress(source, level, false, out, ProgressReporter.NONE) }
        return out
    }

    private fun assertSmaller(source: File, out: File, level: CompressionLevel) {
        assertTrue(
            "${source.name} at $level: ${out.length()} bytes is not smaller than ${source.length()}",
            out.length() in 1 until source.length(),
        )
    }

    // region sample PDFs

    /** Ten pages of text in uncompressed content streams, as some generators write them. */
    private fun textHeavy(): File = build("text-heavy") { doc ->
        repeat(TEXT_PAGES) { pageIndex ->
            val page = PDPage(PDRectangle.A4).also(doc::addPage)
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.OVERWRITE, false).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, FONT_SIZE)
                cs.setLeading(LEADING)
                cs.newLineAtOffset(MARGIN, PDRectangle.A4.height - MARGIN)
                repeat(LINES_PER_PAGE) { line ->
                    cs.showText("Page ${pageIndex + 1}, line ${line + 1}: The quick brown fox jumps over the lazy dog.")
                    cs.newLine()
                }
                cs.endText()
            }
        }
    }

    /** Three pages, each a large lossless (Flate) picture. */
    private fun imageHeavy(): File = build("image-heavy") { doc ->
        repeat(IMAGE_PAGES) { addImagePage(doc, LosslessFactory.createFromImage(doc, picture(1800, 2400, it))) }
    }

    /** Three A4 pages scanned at 300 dpi, stored as high-quality JPEG. */
    private fun scanned(): File = build("scanned") { doc ->
        repeat(IMAGE_PAGES) {
            addImagePage(doc, JPEGFactory.createFromImage(doc, picture(2480, 3508, it), HIGH_JPEG_QUALITY))
        }
    }

    /** Text pages and a picture page in one document. */
    private fun mixed(): File = build("mixed") { doc ->
        val page = PDPage(PDRectangle.A4).also(doc::addPage)
        PDPageContentStream(doc, page).use { cs ->
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA_BOLD, HEADING_SIZE)
            cs.newLineAtOffset(MARGIN, PDRectangle.A4.height - MARGIN)
            cs.showText("Application form")
            cs.endText()
            val photo = LosslessFactory.createFromImage(doc, picture(1200, 1600, 7))
            cs.drawImage(photo, MARGIN, MARGIN, PHOTO_WIDTH, PHOTO_HEIGHT)
        }
        addImagePage(doc, JPEGFactory.createFromImage(doc, picture(2480, 3508, 9), HIGH_JPEG_QUALITY))
    }

    /** A JPEG page plus author, keywords and other document information. */
    private fun withMetadata(): File = build("metadata") { doc ->
        doc.documentInformation = PDDocumentInformation().apply {
            author = "Test Author"
            title = "Form with metadata"
            keywords = "a".repeat(KEYWORDS_LENGTH)
            producer = "Some scanner app"
        }
        addImagePage(doc, JPEGFactory.createFromImage(doc, picture(2000, 2800, 3), HIGH_JPEG_QUALITY))
    }

    /** One short line of compressed text: there is almost nothing to remove. */
    private fun tiny(): File = build("tiny") { doc ->
        val page = PDPage(PDRectangle.A4).also(doc::addPage)
        PDPageContentStream(doc, page).use { cs ->
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA, FONT_SIZE)
            cs.newLineAtOffset(MARGIN, MARGIN)
            cs.showText("Hi")
            cs.endText()
        }
    }

    private fun addImagePage(doc: PDDocument, image: PDImageXObject) {
        val page = PDPage(PDRectangle.A4).also(doc::addPage)
        PDPageContentStream(doc, page).use { it.drawImage(image, 0f, 0f, PDRectangle.A4.width, PDRectangle.A4.height) }
    }

    /** A photo-like picture: smooth gradient plus seeded random shapes, so JPEG behaves as on real scans. */
    private fun picture(width: Int, height: Int, seed: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = width.toFloat()
        val h = height.toFloat()
        paint.shader = LinearGradient(0f, 0f, w, h, Color.WHITE, Color.LTGRAY, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        val random = Random(seed)
        repeat(SHAPES) {
            paint.color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            val radius = random.nextFloat() * w / RADIUS_DIVISOR
            canvas.drawCircle(random.nextFloat() * w, random.nextFloat() * h, radius, paint)
        }
        return bitmap
    }

    private fun build(name: String, content: (PDDocument) -> Unit): File {
        val file = File(dir, "$name.pdf")
        PDDocument().use { doc ->
            content(doc)
            doc.save(file)
        }
        return file
    }

    // endregion

    private companion object {
        const val TEXT_PAGES = 10
        const val LINES_PER_PAGE = 50
        const val IMAGE_PAGES = 3
        const val SHAPES = 400
        const val FONT_SIZE = 11f
        const val HEADING_SIZE = 18f
        const val LEADING = 14f
        const val MARGIN = 50f
        const val PHOTO_WIDTH = 300f
        const val PHOTO_HEIGHT = 400f
        const val HIGH_JPEG_QUALITY = 0.95f
        const val KEYWORDS_LENGTH = 20_000
        const val RADIUS_DIVISOR = 10
    }
}
