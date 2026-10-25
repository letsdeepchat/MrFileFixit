package com.mrfilefixit.core.office.pdftext

/**
 * One character (or ligature) drawn on a PDF page, as the PDF text extractor reports it.
 * Coordinates are points from the page's top-left; [y] is the baseline.
 */
data class PdfGlyph(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val fontSize: Float,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val colorArgb: Int? = null,
) {
    val right: Float get() = x + width
    val top: Float get() = y - fontSize * ASCENT
    val isSpace: Boolean get() = text.isBlank()

    companion object {
        /** Typical ascent as a share of the font size. */
        const val ASCENT = 0.8f
    }
}

/** A picture drawn on a page, in points from the top-left, with its encoded bytes (JPEG or PNG). */
class PdfPicture(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val bytes: ByteArray,
    val mimeType: String,
)

data class PdfPageText(
    val widthPt: Float,
    val heightPt: Float,
    val glyphs: List<PdfGlyph>,
    val pictures: List<PdfPicture> = emptyList(),
)

/** A horizontal run of glyphs on one baseline, with no wide gaps inside. */
data class TextLine(val glyphs: List<PdfGlyph>) {
    val x: Float = glyphs.minOf { it.x }
    val right: Float = glyphs.maxOf { it.right }
    val baseline: Float = glyphs.map { it.y }.sorted()[glyphs.size / 2]
    val fontSize: Float = glyphs.map { it.fontSize }.sorted()[glyphs.size / 2]
    val top: Float get() = baseline - fontSize * PdfGlyph.ASCENT
    val bottom: Float get() = baseline + fontSize * DESCENT
    val width: Float get() = right - x

    /** Text with spaces inserted where glyphs are visibly apart. */
    val text: String by lazy { TextLayout.joinGlyphs(glyphs) }
    val allBold: Boolean get() = glyphs.filterNot { it.isSpace }.all { it.bold }

    companion object {
        const val DESCENT = 0.25f
    }
}
