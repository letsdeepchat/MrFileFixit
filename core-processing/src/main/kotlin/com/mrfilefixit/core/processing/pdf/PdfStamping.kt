package com.mrfilefixit.core.processing.pdf

import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.job.step
import com.mrfilefixit.core.domain.pdf.PdfOperation
import com.mrfilefixit.core.processing.io.newOutputFile
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Watermark and page numbers: text drawn on top of each page, appended to the existing content. */
internal object PdfStamping {
    private const val FONT_UNITS = 1000f
    private const val MARGIN_PT = 24f
    private const val PAGE_NUMBER_SIZE = 10f

    suspend fun watermark(engine: PdfBoxPdfProcessor, op: PdfOperation.Watermark, progress: ProgressReporter): OutputFile {
        val src = engine.copyToJob(op.source, op.outputDir, "watermark-src")
        val text = PdfLayout.toWinAnsi(op.text)
        val font = PDType1Font.HELVETICA_BOLD
        return engine.openDocument(src, null).use { doc ->
            val state = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = op.opacity.coerceIn(0.05f, 1f) }
            val total = doc.numberOfPages
            for (index in 0 until total) {
                coroutineContext.ensureActive()
                val page = doc.getPage(index)
                val box = page.mediaBox
                val width = font.getStringWidth(text) / FONT_UNITS * op.fontSizePt
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.setGraphicsStateParameters(state)
                    cs.beginText()
                    cs.setFont(font, op.fontSizePt)
                    cs.setNonStrokingColor(0.5f, 0.5f, 0.5f)
                    if (op.diagonal) {
                        val angle = atan2(box.height.toDouble(), box.width.toDouble())
                        val cx = box.width / 2f - (width / 2f) * cos(angle).toFloat()
                        val cy = box.height / 2f - (width / 2f) * sin(angle).toFloat()
                        cs.setTextMatrix(Matrix.getRotateInstance(angle, cx, cy))
                    } else {
                        cs.newLineAtOffset((box.width - width) / 2f, box.height / 2f)
                    }
                    cs.showText(text)
                    cs.endText()
                }
                progress.step(index, total)
            }
            val out = newOutputFile(op.outputDir, op.outputBaseName, PdfBoxPdfProcessor.PDF_EXT)
            doc.save(out)
            OutputFile(out.absolutePath, out.name, PdfBoxPdfProcessor.PDF_MIME, out.length(), pageCount = total)
        }.also { src.delete() }
    }

    suspend fun pageNumbers(engine: PdfBoxPdfProcessor, op: PdfOperation.PageNumbers, progress: ProgressReporter): OutputFile {
        val src = engine.copyToJob(op.source, op.outputDir, "numbers-src")
        val font = PDType1Font.HELVETICA
        return engine.openDocument(src, null).use { doc ->
            val total = doc.numberOfPages
            for (index in 0 until total) {
                coroutineContext.ensureActive()
                val page = doc.getPage(index)
                val box = page.mediaBox
                val label = PdfLayout.toWinAnsi(PdfLayout.pageLabel(op.pattern, op.startAt + index, op.startAt + total - 1))
                val width = font.getStringWidth(label) / FONT_UNITS * PAGE_NUMBER_SIZE
                val (x, y) = PdfLayout.textOrigin(op.position, width, PAGE_NUMBER_SIZE, box.width, box.height, MARGIN_PT)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.beginText()
                    cs.setFont(font, PAGE_NUMBER_SIZE)
                    cs.newLineAtOffset(x, y)
                    cs.showText(label)
                    cs.endText()
                }
                progress.step(index, total)
            }
            val out = newOutputFile(op.outputDir, op.outputBaseName, PdfBoxPdfProcessor.PDF_EXT)
            doc.save(out)
            OutputFile(out.absolutePath, out.name, PdfBoxPdfProcessor.PDF_MIME, out.length(), pageCount = total)
        }.also { src.delete() }
    }
}
