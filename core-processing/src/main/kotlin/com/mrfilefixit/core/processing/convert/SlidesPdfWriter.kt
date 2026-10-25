package com.mrfilefixit.core.processing.convert

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.mrfilefixit.core.domain.document.FontFamily
import com.mrfilefixit.core.domain.document.Presentation
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.domain.document.SlideShape
import com.mrfilefixit.core.domain.document.VerticalAnchor
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlinx.coroutines.ensureActive

/** Draws each slide on its own PDF page of the slide's size: backgrounds, boxes, pictures and text boxes. */
internal class SlidesPdfWriter {

    suspend fun write(presentation: Presentation, out: File, progress: (Float) -> Unit = {}): Int {
        val pdf = PdfDocument()
        val painter = ParagraphPainter(FontFamily.SANS, RichDocument.BLACK)
        try {
            val width = presentation.widthPt.roundToInt().coerceAtLeast(1)
            val height = presentation.heightPt.roundToInt().coerceAtLeast(1)
            presentation.slides.forEachIndexed { index, slide ->
                coroutineContext.ensureActive()
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
                val canvas = page.canvas
                canvas.drawColor(slide.backgroundArgb ?: Color.WHITE)
                slide.shapes.forEach { shape -> draw(canvas, shape, painter) }
                pdf.finishPage(page)
                progress((index + 1f) / presentation.slides.size)
            }
            if (presentation.slides.isEmpty()) {
                pdf.finishPage(pdf.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create()))
            }
            out.outputStream().use { pdf.writeTo(it) }
            return presentation.slides.size.coerceAtLeast(1)
        } finally {
            pdf.close()
        }
    }

    private fun draw(canvas: Canvas, shape: SlideShape, painter: ParagraphPainter) {
        val f = shape.frame
        val rect = RectF(f.x, f.y, f.x + f.width, f.y + f.height)
        when (shape) {
            is SlideShape.Box -> box(canvas, rect, shape.fillArgb, shape.lineArgb)
            is SlideShape.Picture -> Pictures.draw(canvas, shape.bytes, rect)
            is SlideShape.TextBox -> {
                box(canvas, rect, shape.fillArgb, shape.lineArgb)
                text(canvas, rect, shape, painter)
            }
        }
    }

    private fun box(canvas: Canvas, rect: RectF, fill: Int?, line: Int?) {
        fill?.let { canvas.drawRect(rect, Paint().apply { color = it }) }
        line?.let {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = it
                style = Paint.Style.STROKE
                strokeWidth = LINE_WIDTH
            }
            canvas.drawRect(rect, paint)
        }
    }

    private fun text(canvas: Canvas, rect: RectF, shape: SlideShape.TextBox, painter: ParagraphPainter) {
        val width = (rect.width() - 2 * INSET_X).coerceAtLeast(1f)
        val lists = ListCounter()
        val measured = shape.paragraphs.map { painter.measure(it, width, shape.defaultSizePt, lists.label(it.style)) }
        val total = measured.sumOf { (it.height + it.style.spaceAfterPt).toDouble() }.toFloat()
        val inner = rect.height() - 2 * INSET_Y
        var y = rect.top + INSET_Y + when (shape.anchor) {
            VerticalAnchor.TOP -> 0f
            VerticalAnchor.MIDDLE -> ((inner - total) / 2).coerceAtLeast(0f)
            VerticalAnchor.BOTTOM -> (inner - total).coerceAtLeast(0f)
        }
        measured.forEach { m ->
            m.drawLines(canvas, rect.left + INSET_X, y, width, 0, m.layout.lineCount)
            y += m.height + m.style.spaceAfterPt
        }
    }

    private companion object {
        /** PowerPoint's default text box insets: 0.1 inch left and right, 0.05 inch top and bottom. */
        const val INSET_X = 7.2f
        const val INSET_Y = 3.6f
        const val LINE_WIDTH = 0.75f
    }
}
