package com.mrfilefixit.core.processing.convert

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.mrfilefixit.core.domain.document.BorderStyle
import com.mrfilefixit.core.domain.document.PageDecoration

/** Draws a [PageDecoration] frame on a PDF page before its content, so text never sits under the lines. */
internal object PageBorderPainter {
    private const val THIN = 0.75f
    private const val THICK = 2.25f
    private const val GAP = 4f
    private const val DIAMOND = 5f
    private const val CORNER_LENGTH = 42f
    private const val CORNER_STROKE = 2f

    fun draw(canvas: Canvas, decoration: PageDecoration, widthPt: Float, heightPt: Float) {
        val inset = decoration.insetPt
        val outer = RectF(inset, inset, widthPt - inset, heightPt - inset)
        if (outer.width() <= 0f || outer.height() <= 0f) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = decoration.colorArgb
        }
        when (decoration.style) {
            BorderStyle.SINGLE -> canvas.drawRect(outer, paint.apply { strokeWidth = THIN })
            BorderStyle.DOUBLE -> double(canvas, outer, paint, decoration.accentArgb)
            BorderStyle.ORNATE -> {
                double(canvas, outer, paint, decoration.accentArgb)
                ornaments(canvas, outer, decoration.accentArgb)
            }
            BorderStyle.CORNERS -> corners(canvas, outer, paint)
        }
    }

    private fun double(canvas: Canvas, outer: RectF, paint: Paint, accent: Int) {
        canvas.drawRect(outer, paint.apply { strokeWidth = THICK })
        val inner = RectF(outer).apply { inset(THICK + GAP, THICK + GAP) }
        paint.strokeWidth = THIN
        paint.color = accent
        canvas.drawRect(inner, paint)
    }

    /** Small filled diamonds at the four corners and the middle of each side, over the frame lines. */
    private fun ornaments(canvas: Canvas, outer: RectF, accent: Int) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = accent
        }
        val points = listOf(
            outer.left to outer.top,
            outer.right to outer.top,
            outer.left to outer.bottom,
            outer.right to outer.bottom,
            outer.centerX() to outer.top,
            outer.centerX() to outer.bottom,
            outer.left to outer.centerY(),
            outer.right to outer.centerY(),
        )
        points.forEach { (x, y) ->
            val path = Path().apply {
                moveTo(x, y - DIAMOND)
                lineTo(x + DIAMOND, y)
                lineTo(x, y + DIAMOND)
                lineTo(x - DIAMOND, y)
                close()
            }
            canvas.drawPath(path, fill)
        }
    }

    private fun corners(canvas: Canvas, outer: RectF, paint: Paint) {
        paint.strokeWidth = CORNER_STROKE
        val l = minOf(CORNER_LENGTH, outer.width() / 2, outer.height() / 2)
        val path = Path().apply {
            moveTo(outer.left, outer.top + l)
            lineTo(outer.left, outer.top)
            lineTo(outer.left + l, outer.top)
            moveTo(outer.right - l, outer.top)
            lineTo(outer.right, outer.top)
            lineTo(outer.right, outer.top + l)
            moveTo(outer.right, outer.bottom - l)
            lineTo(outer.right, outer.bottom)
            lineTo(outer.right - l, outer.bottom)
            moveTo(outer.left + l, outer.bottom)
            lineTo(outer.left, outer.bottom)
            lineTo(outer.left, outer.bottom - l)
        }
        canvas.drawPath(path, paint)
    }
}
