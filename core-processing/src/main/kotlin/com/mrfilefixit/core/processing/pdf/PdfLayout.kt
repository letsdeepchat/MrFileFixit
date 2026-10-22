package com.mrfilefixit.core.processing.pdf

import com.mrfilefixit.core.domain.pdf.PageSize
import com.mrfilefixit.core.domain.pdf.Position

/** Pure layout maths for PDF pages, in PDF points (1/72 inch, origin bottom-left). */
internal object PdfLayout {
    data class Box(val x: Float, val y: Float, val width: Float, val height: Float)

    private const val PX_TO_PT_AT_150_DPI = 72f / 150f

    /** Page size for an image: a fixed paper size (rotated to match the image) or the image itself at 150 dpi. */
    fun pageBox(pageSize: PageSize, imageWidthPx: Int, imageHeightPx: Int): Box {
        if (pageSize == PageSize.FIT_IMAGE) {
            return Box(0f, 0f, imageWidthPx * PX_TO_PT_AT_150_DPI, imageHeightPx * PX_TO_PT_AT_150_DPI)
        }
        val landscape = imageWidthPx > imageHeightPx
        val w = if (landscape) pageSize.heightPt else pageSize.widthPt
        val h = if (landscape) pageSize.widthPt else pageSize.heightPt
        return Box(0f, 0f, w, h)
    }

    /** Largest box with the image's aspect ratio inside the page minus [margin], centred. */
    fun fit(imageW: Int, imageH: Int, pageW: Float, pageH: Float, margin: Float): Box {
        val availW = (pageW - 2 * margin).coerceAtLeast(1f)
        val availH = (pageH - 2 * margin).coerceAtLeast(1f)
        val scale = minOf(availW / imageW, availH / imageH)
        val w = imageW * scale
        val h = imageH * scale
        return Box((pageW - w) / 2f, (pageH - h) / 2f, w, h)
    }

    /** Baseline origin for a text of [textWidth] at [position], [margin] from the page edges. */
    fun textOrigin(
        position: Position,
        textWidth: Float,
        fontSize: Float,
        pageW: Float,
        pageH: Float,
        margin: Float,
    ): Pair<Float, Float> {
        val x = when (position) {
            Position.TOP_LEFT, Position.BOTTOM_LEFT -> margin
            Position.TOP_CENTER, Position.BOTTOM_CENTER -> (pageW - textWidth) / 2f
            Position.TOP_RIGHT, Position.BOTTOM_RIGHT -> pageW - margin - textWidth
        }
        val y = when (position) {
            Position.TOP_LEFT, Position.TOP_CENTER, Position.TOP_RIGHT -> pageH - margin - fontSize
            else -> margin
        }
        return x to y
    }

    /** Replaces `{n}` and `{total}` in a page-number pattern. */
    fun pageLabel(pattern: String, number: Int, total: Int): String =
        pattern.replace("{n}", number.toString()).replace("{total}", total.toString())

    /**
     * The standard PDF fonts only cover WinAnsi (Latin-1). Characters outside it are replaced with '?'
     * instead of failing the whole job; the UI tells users to stick to Latin letters for stamps.
     */
    fun toWinAnsi(text: String): String = buildString(text.length) {
        for (ch in text) append(if (ch.code in PRINTABLE_ASCII || ch.code in PRINTABLE_LATIN1) ch else '?')
    }
}

// Characters Helvetica can draw with WinAnsi encoding.
private val PRINTABLE_ASCII = 0x20..0x7E
private val PRINTABLE_LATIN1 = 0xA0..0xFF
