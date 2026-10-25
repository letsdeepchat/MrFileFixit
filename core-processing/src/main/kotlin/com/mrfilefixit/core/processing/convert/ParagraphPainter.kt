package com.mrfilefixit.core.processing.convert

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.FontFamily
import com.mrfilefixit.core.domain.document.ListKind
import com.mrfilefixit.core.domain.document.ParagraphStyle
import com.mrfilefixit.core.domain.document.TextAlign
import kotlin.math.ceil

/** A paragraph laid out for a given width, ready to draw line by line. Units are PDF points. */
internal class MeasuredParagraph(
    val layout: StaticLayout,
    val style: ParagraphStyle,
    /** Left offset of the text inside the paragraph box (indent plus list hang). */
    val textOffset: Float,
    val marker: String?,
    val markerPaint: TextPaint,
) {
    val height: Float get() = layout.height.toFloat()

    fun lineTop(line: Int): Float = layout.getLineTop(line).toFloat()

    fun lineBottom(line: Int): Float = layout.getLineBottom(line).toFloat()

    /** Draws lines [from] until [until] with their top at [y]. */
    fun drawLines(canvas: Canvas, x: Float, y: Float, width: Float, from: Int, until: Int) {
        val top = lineTop(from)
        val bottom = lineBottom(until - 1)
        style.shadingArgb?.let { fill(canvas, it, x, y, x + width, y + bottom - top) }
        if (from == 0 && marker != null) {
            val baseline = y + layout.getLineBaseline(0) - top
            canvas.drawText(
                marker,
                x + textOffset - markerPaint.measureText(marker) - MARKER_GAP,
                baseline,
                markerPaint,
            )
        }
        canvas.save()
        canvas.clipRect(x, y, x + width, y + bottom - top)
        canvas.translate(x + textOffset, y - top)
        layout.draw(canvas)
        canvas.restore()
        if (until == layout.lineCount) {
            style.bottomBorderArgb?.let { color ->
                val rule = y + bottom - top + RULE_GAP
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    strokeWidth = RULE_WIDTH
                }
                canvas.drawLine(x, rule, x + width, rule, paint)
            }
        }
    }

    private fun fill(canvas: Canvas, color: Int, left: Float, top: Float, right: Float, bottom: Float) {
        val paint = Paint().apply { this.color = color }
        canvas.drawRect(left, top - SHADE_PAD, right, bottom + SHADE_PAD, paint)
    }

    companion object {
        const val MARKER_GAP = 4f
        const val RULE_GAP = 2f
        const val RULE_WIDTH = 0.75f
        const val SHADE_PAD = 2f
    }
}

/** Numbers list items per level and restarts when a list is interrupted. */
internal class ListCounter {
    private val counts = IntArray(ParagraphStyle.MAX_HEADING + 3)

    fun label(style: ParagraphStyle): String? = when (style.list) {
        ListKind.NONE -> {
            counts.fill(0)
            null
        }
        ListKind.BULLET -> BULLETS[style.listLevel.coerceIn(0, counts.size - 1) % BULLETS.size]
        ListKind.NUMBERED -> {
            val level = style.listLevel.coerceIn(0, counts.size - 1)
            counts[level]++
            for (i in level + 1 until counts.size) counts[i] = 0
            "${counts[level]}."
        }
    }

    private companion object {
        val BULLETS = listOf("•", "◦", "▪")
    }
}

/**
 * Turns [DocParagraph]s into [StaticLayout]s. Text falls back to the system fonts, so Hindi, Tamil, Arabic
 * and CJK runs render even when the document asked for a Latin font.
 */
internal class ParagraphPainter(private val defaultFont: FontFamily, private val textColor: Int) {

    fun measure(paragraph: DocParagraph, width: Float, baseSizePt: Float, marker: String?): MeasuredParagraph {
        val style = paragraph.style
        val size = baseSizePt * ParagraphStyle.headingScale(style.heading)
        val paint = textPaint(size, defaultFont, bold = style.heading > 0)
        val hang = if (style.list == ListKind.NONE) 0f else LIST_HANG + style.listLevel * LIST_STEP
        val offset = style.indentStartPt + hang
        val textWidth = (width - offset).coerceAtLeast(MIN_WIDTH)
        val text = spans(paragraph, size)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, ceil(textWidth).toInt())
            .setAlignment(alignment(style.align))
            .setLineSpacing(0f, style.lineSpacing.coerceIn(MIN_SPACING, MAX_SPACING))
            .setIncludePad(false)
            .setJustificationMode(justification(style.align))
            .build()
        val markerPaint = TextPaint(paint).apply { textSize = size }
        return MeasuredParagraph(layout, style, offset, marker, markerPaint)
    }

    private fun spans(paragraph: DocParagraph, size: Float): CharSequence {
        val sb = SpannableStringBuilder()
        paragraph.runs.forEach { run ->
            if (run.text.isEmpty()) return@forEach
            val start = sb.length
            sb.append(run.text.replace('\t', ' '))
            val end = sb.length
            val styleBits = (if (run.bold) Typeface.BOLD else 0) or (if (run.italic) Typeface.ITALIC else 0)
            if (styleBits != 0) sb.setSpan(StyleSpan(styleBits), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (run.underline || run.link != null) {
                sb.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            run.sizePt?.takeIf { it > 0f }?.let {
                sb.setSpan(RelativeSizeSpan(it / size), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            run.colorArgb?.let { sb.setSpan(ForegroundColorSpan(it), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            run.font?.takeIf { it != defaultFont }?.let {
                sb.setSpan(TypefaceSpan(familyName(it)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return sb
    }

    fun textPaint(size: Float, font: FontFamily, bold: Boolean): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = size
            color = textColor
            typeface = Typeface.create(familyName(font), if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun justification(align: TextAlign): Int =
        if (align == TextAlign.JUSTIFY) Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE

    private fun alignment(align: TextAlign): Layout.Alignment = when (align) {
        TextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
        TextAlign.END -> Layout.Alignment.ALIGN_OPPOSITE
        TextAlign.START, TextAlign.JUSTIFY -> Layout.Alignment.ALIGN_NORMAL
    }

    companion object {
        const val LIST_HANG = 16f
        const val LIST_STEP = 18f
        private const val MIN_WIDTH = 8f
        private const val MIN_SPACING = 0.8f
        private const val MAX_SPACING = 3f

        fun familyName(font: FontFamily): String = when (font) {
            FontFamily.SANS -> "sans-serif"
            FontFamily.SERIF -> "serif"
            FontFamily.MONO -> "monospace"
            FontFamily.CONDENSED -> "sans-serif-condensed"
        }
    }
}
