package com.mrfilefixit.core.processing.ocr

import android.content.ContentResolver
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import com.mrfilefixit.core.domain.convert.DocumentFormat
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.job.step
import com.mrfilefixit.core.domain.ocr.OcrExporter
import com.mrfilefixit.core.domain.ocr.OcrPage
import com.mrfilefixit.core.domain.ocr.OcrResult
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.runCatchingAppFlat
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.security.InputLimits
import com.mrfilefixit.core.processing.io.InputRejectedException
import com.mrfilefixit.core.processing.io.mapEngineError
import com.mrfilefixit.core.processing.io.newOutputFile
import com.mrfilefixit.core.processing.io.openLimited
import com.mrfilefixit.core.processing.io.throwIfFailure
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.util.Matrix
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Writes OCR results. A searchable PDF keeps each original PDF page as it is (re-drawn as a JPEG only when the
 * page is rotated) and puts every image on its own page, then lays the recognized lines over the page as
 * nearly transparent text, sized to each line's box, so the PDF can be searched, selected and copied while it
 * still looks like the original. The text layer is drawn with the platform [PdfDocument], which shapes and
 * embeds fonts for every script, and stamped onto the pages with PDFBox.
 */
internal class AndroidOcrExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: ContentResolver,
    private val pages: OcrPageImages,
    private val limits: InputLimits,
    private val dispatchers: DispatcherProvider,
) : OcrExporter {
    private val pdfBoxReady by lazy { PDFBoxResourceLoader.init(context) }

    override suspend fun writeText(text: String, outputDir: String, baseName: String): AppResult<OutputFile> =
        withContext(dispatchers.io) {
            runCatchingAppFlat(::mapEngineError) {
                val format = DocumentFormat.TXT
                val out = newOutputFile(outputDir, baseName, format.extension)
                out.writeText(text, Charsets.UTF_8)
                success(OutputFile(out.absolutePath, out.name, format.mimeType, out.length()))
            }
        }

    override suspend fun searchablePdf(
        sources: List<FileRef>,
        result: OcrResult,
        outputDir: String,
        baseName: String,
        progress: ProgressReporter,
    ): AppResult<OutputFile> = withContext(dispatchers.default) {
        runCatchingAppFlat(::mapEngineError) {
            pdfBoxReady
            val byPage = result.pages.associateBy { it.sourceIndex to it.pageIndex }
            val temps = mutableListOf<File>()
            val opened = mutableListOf<PDDocument>()
            val layer = PdfDocument()
            try {
                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { out ->
                    val placed = mutableListOf<Placement>()
                    sources.forEachIndexed { sourceIndex, source ->
                        coroutineContext.ensureActive()
                        placed += if (source.mimeType == OcrPageImages.PDF_MIME) {
                            val copy = copyToJob(source, outputDir).also { temps += it }
                            val original = openPdf(copy).also { opened += it }
                            if (original.pages.any { it.rotation % FULL_TURN != 0 }) {
                                rasterPages(out, source, sourceIndex)
                            } else {
                                original.pages.mapIndexed { index, page -> keepPage(out, page, sourceIndex, index) }
                            }
                        } else {
                            rasterPages(out, source, sourceIndex)
                        }
                        progress.step(sourceIndex, sources.size, HALF)
                    }
                    placed.forEachIndexed { index, placement ->
                        drawTextLayer(layer, index, placement, byPage[placement.sourceIndex to placement.pageIndex])
                    }
                    val layerFile = File.createTempFile("ocr-layer", ".pdf", File(outputDir)).also { temps += it }
                    layerFile.outputStream().use { layer.writeTo(it) }
                    stampLayer(out, layerFile, placed)
                    val file = newOutputFile(outputDir, baseName, DocumentFormat.PDF.extension)
                    out.save(file)
                    progress.report(1f)
                    success(OutputFile(file.absolutePath, file.name, PDF_MIME, file.length(), pageCount = placed.size))
                }
            } finally {
                layer.close()
                opened.forEach { it.close() }
                temps.forEach { it.delete() }
            }
        }
    }

    /** Where a source page landed in the output, with its size and the offset of its visible area. */
    private class Placement(
        val sourceIndex: Int,
        val pageIndex: Int,
        val page: PDPage,
        val box: PDRectangle,
    )

    private fun keepPage(out: PDDocument, page: PDPage, sourceIndex: Int, index: Int): Placement {
        val imported = out.importPage(page)
        return Placement(sourceIndex, index, imported, page.cropBox)
    }

    /** Re-draws every page of [source] as a JPEG on its own page: A4 wide for images, the PDF's size for PDFs. */
    private suspend fun rasterPages(out: PDDocument, source: FileRef, sourceIndex: Int): List<Placement> =
        pages.forEachPage(source) { image ->
            val bitmap = image.bitmap
            val widthPt = image.sizePt?.first ?: PDRectangle.A4.width
            val heightPt = widthPt * bitmap.height / bitmap.width
            val page = PDPage(PDRectangle(widthPt, heightPt))
            out.addPage(page)
            val jpeg = JPEGFactory.createFromImage(out, bitmap, JPEG_QUALITY)
            PDPageContentStream(out, page).use { it.drawImage(jpeg, 0f, 0f, widthPt, heightPt) }
            Placement(sourceIndex, image.index, page, page.mediaBox)
        }

    /** One page of the text layer, the size of [placement]'s visible area, with each line fitted to its box. */
    private fun drawTextLayer(layer: PdfDocument, index: Int, placement: Placement, ocr: OcrPage?) {
        val widthPt = placement.box.width.roundToInt().coerceAtLeast(1)
        val heightPt = placement.box.height.roundToInt().coerceAtLeast(1)
        val page = layer.startPage(PdfDocument.PageInfo.Builder(widthPt, heightPt, index + 1).create())
        if (ocr != null && ocr.widthPx > 0 && ocr.heightPx > 0) {
            drawLines(page.canvas, ocr, widthPt.toFloat() / ocr.widthPx, heightPt.toFloat() / ocr.heightPx)
        }
        layer.finishPage(page)
    }

    private fun drawLines(canvas: Canvas, ocr: OcrPage, scaleX: Float, scaleY: Float) {
        // Fully transparent text is dropped by the PDF backend, so the text is drawn at the lowest alpha.
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 1
        }
        ocr.lines.forEach { line ->
            val width = line.box.width * scaleX
            val height = line.box.height * scaleY
            if (line.text.isBlank() || width < 1f || height < 1f) return@forEach
            paint.textScaleX = 1f
            paint.textSize = height * TEXT_HEIGHT
            val measured = paint.measureText(line.text)
            if (measured > 0f) paint.textScaleX = (width / measured).coerceIn(MIN_STRETCH, MAX_STRETCH)
            canvas.drawText(line.text, line.box.left * scaleX, line.box.top * scaleY + height * BASELINE, paint)
        }
    }

    /** Draws each text-layer page over the matching output page, in the page's own coordinates. */
    private fun stampLayer(out: PDDocument, layerFile: File, placed: List<Placement>) {
        PDDocument.load(layerFile, MemoryUsageSetting.setupTempFileOnly()).use { layerDoc ->
            val layers = LayerUtility(out)
            placed.forEachIndexed { index, placement ->
                val form = layers.importPageAsForm(layerDoc, index)
                val box = placement.box
                val layerBox = layerDoc.getPage(index).mediaBox
                PDPageContentStream(out, placement.page, PDPageContentStream.AppendMode.APPEND, true, true).use {
                    it.saveGraphicsState()
                    it.transform(Matrix.getTranslateInstance(box.lowerLeftX, box.lowerLeftY))
                    // The layer page size was rounded to whole points; stretch it back to the exact box.
                    it.transform(Matrix.getScaleInstance(box.width / layerBox.width, box.height / layerBox.height))
                    it.drawForm(form)
                    it.restoreGraphicsState()
                }
            }
        }
    }

    private fun copyToJob(source: FileRef, outputDir: String): File {
        limits.checkFileSize(source.sizeBytes).throwIfFailure()
        val target = File.createTempFile("ocr-source", ".pdf", File(outputDir).apply { mkdirs() })
        resolver.openLimited(source.uri, limits.maxFileBytes).use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        return target
    }

    private fun openPdf(file: File): PDDocument {
        val document = try {
            PDDocument.load(file, "", MemoryUsageSetting.setupTempFileOnly())
        } catch (_: InvalidPasswordException) {
            throw InputRejectedException(AppError.PasswordRequired)
        }
        if (document.isEncrypted && !document.currentAccessPermission.canExtractContent()) {
            document.close()
            throw InputRejectedException(AppError.PasswordRequired)
        }
        val check = limits.checkPageCount(document.numberOfPages)
        if (check is Result.Failure) {
            document.close()
            throw InputRejectedException(check.error)
        }
        return document
    }

    private companion object {
        const val PDF_MIME = "application/pdf"
        const val JPEG_QUALITY = 0.8f
        const val FULL_TURN = 360
        const val HALF = 0.5f

        /** Text size as a share of the line box height, and the baseline position inside the box. */
        const val TEXT_HEIGHT = 0.8f
        const val BASELINE = 0.8f
        const val MIN_STRETCH = 0.2f
        const val MAX_STRETCH = 5f
    }
}
