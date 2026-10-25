package com.mrfilefixit.core.domain.document

/** A slide deck: fixed-size slides with positioned shapes. Geometry is in points from the slide's top-left. */
data class Presentation(val widthPt: Float, val heightPt: Float, val slides: List<Slide>) {
    companion object {
        /** 16:9 at 13.333 x 7.5 inches, PowerPoint's default. */
        const val WIDE_WIDTH = 960f
        const val WIDE_HEIGHT = 540f
    }
}

data class Slide(val backgroundArgb: Int? = null, val shapes: List<SlideShape>)

data class FrameRect(val x: Float, val y: Float, val width: Float, val height: Float)

enum class VerticalAnchor { TOP, MIDDLE, BOTTOM }

sealed interface SlideShape {
    val frame: FrameRect

    data class TextBox(
        override val frame: FrameRect,
        val paragraphs: List<DocParagraph>,
        val fillArgb: Int? = null,
        val lineArgb: Int? = null,
        val anchor: VerticalAnchor = VerticalAnchor.TOP,
        /** Default size for runs without their own size. */
        val defaultSizePt: Float = DEFAULT_TEXT_SIZE,
    ) : SlideShape

    class Picture(override val frame: FrameRect, val bytes: ByteArray, val mimeType: String) : SlideShape {
        override fun toString(): String = "Picture($frame, $mimeType)"
    }

    /** A filled or outlined rectangle without text. */
    data class Box(override val frame: FrameRect, val fillArgb: Int?, val lineArgb: Int?) : SlideShape

    companion object {
        const val DEFAULT_TEXT_SIZE = 18f
    }
}

/** A spreadsheet: sheets of rows of cells. Formulas are represented by their cached values. */
data class Workbook(val sheets: List<Sheet>)

data class Sheet(
    val name: String,
    val rows: List<List<SheetCell>>,
    /** Column widths in characters, as spreadsheets store them; empty means automatic. */
    val columnWidths: Map<Int, Float> = emptyMap(),
) {
    val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0
}

sealed interface SheetCell {
    val display: String

    data object Empty : SheetCell {
        override val display: String = ""
    }

    data class Text(val value: String, val bold: Boolean = false) : SheetCell {
        override val display: String get() = value
    }

    data class Number(val value: Double, val formatted: String? = null, val bold: Boolean = false) : SheetCell {
        override val display: String get() = formatted ?: formatPlain(value)
    }

    data class Bool(val value: Boolean) : SheetCell {
        override val display: String get() = if (value) "TRUE" else "FALSE"
    }

    companion object {
        private const val MAX_EXACT = 1e15

        /** 3.0 shows as "3", 0.1 + 0.2 as "0.3": what a spreadsheet shows in a General cell. */
        fun formatPlain(value: Double): String = when {
            value.isNaN() || value.isInfinite() -> value.toString()
            value == Math.rint(value) && kotlin.math.abs(value) < MAX_EXACT -> value.toLong().toString()
            else -> java.math.BigDecimal(value).round(java.math.MathContext(SIGNIFICANT)).stripTrailingZeros()
                .toPlainString()
        }

        private const val SIGNIFICANT = 15
    }
}
