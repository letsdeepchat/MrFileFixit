package com.mrfilefixit.core.domain.document

/**
 * A flowing, word-processor style document. It is the common model between readers (DOCX, DOC, PDF text),
 * writers (DOCX) and the PDF layout engine, and it is what resume templates produce, so one model gives
 * both the PDF and the Word export.
 *
 * Sizes are PDF points (1/72 inch). Colours are packed ARGB ints.
 */
data class RichDocument(
    val blocks: List<DocBlock>,
    val page: PageSetup = PageSetup(),
    val defaultFont: FontFamily = FontFamily.SANS,
    val baseSizePt: Float = DEFAULT_SIZE_PT,
    val textColor: Int = BLACK,
    val title: String = "",
    val author: String = "",
    /** Opaque app data stored inside the exported file (for example a resume's JSON so it can be re-opened). */
    val appData: String? = null,
) {
    companion object {
        const val DEFAULT_SIZE_PT = 11f
        const val BLACK = -0x1000000 // 0xFF000000
    }
}

data class PageSetup(
    val widthPt: Float = A4_WIDTH,
    val heightPt: Float = A4_HEIGHT,
    val marginTopPt: Float = DEFAULT_MARGIN,
    val marginBottomPt: Float = DEFAULT_MARGIN,
    val marginStartPt: Float = DEFAULT_MARGIN,
    val marginEndPt: Float = DEFAULT_MARGIN,
) {
    val contentWidthPt: Float get() = widthPt - marginStartPt - marginEndPt
    val contentHeightPt: Float get() = heightPt - marginTopPt - marginBottomPt

    companion object {
        const val A4_WIDTH = 595f
        const val A4_HEIGHT = 842f
        const val LETTER_WIDTH = 612f
        const val LETTER_HEIGHT = 792f

        /** 2 cm. */
        const val DEFAULT_MARGIN = 56.7f
    }
}

/** Font families every target supports: Android system fonts in PDFs, common Office fonts in DOCX. */
enum class FontFamily(val docxName: String) {
    SANS("Calibri"),
    SERIF("Georgia"),
    MONO("Consolas"),
    CONDENSED("Arial Narrow"),
}

enum class TextAlign { START, CENTER, END, JUSTIFY }

enum class ListKind { NONE, BULLET, NUMBERED }

sealed interface DocBlock

data class DocRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    /** Null inherits the paragraph's size (heading or body). */
    val sizePt: Float? = null,
    val colorArgb: Int? = null,
    val font: FontFamily? = null,
    /** Hyperlink target (https: or mailto:). Kept as text in PDFs, as a real link in DOCX. */
    val link: String? = null,
)

data class ParagraphStyle(
    /** 0 is body text, 1..6 are heading levels. */
    val heading: Int = 0,
    val align: TextAlign = TextAlign.START,
    val list: ListKind = ListKind.NONE,
    val listLevel: Int = 0,
    val spaceBeforePt: Float = 0f,
    val spaceAfterPt: Float = DEFAULT_SPACE_AFTER,
    val lineSpacing: Float = DEFAULT_LINE_SPACING,
    val indentStartPt: Float = 0f,
    /** Background band behind the paragraph, full content width. */
    val shadingArgb: Int? = null,
    /** A rule under the paragraph, as resume section headings use. */
    val bottomBorderArgb: Int? = null,
    /** Avoid a page break between this paragraph and the next (headings). */
    val keepWithNext: Boolean = false,
) {
    init {
        require(heading in 0..MAX_HEADING) { "Heading level must be 0..$MAX_HEADING" }
    }

    companion object {
        const val MAX_HEADING = 6
        const val DEFAULT_SPACE_AFTER = 6f
        const val DEFAULT_LINE_SPACING = 1.15f

        /** Heading sizes as multiples of the body size, levels 1..6; shared by the PDF and DOCX writers. */
        private val HEADING_SCALE = floatArrayOf(1.8f, 1.45f, 1.25f, 1.1f, 1.05f, 1f)

        fun headingScale(level: Int): Float = if (level in 1..MAX_HEADING) HEADING_SCALE[level - 1] else 1f
    }
}

data class DocParagraph(val runs: List<DocRun>, val style: ParagraphStyle = ParagraphStyle()) : DocBlock {
    val plainText: String get() = runs.joinToString("") { it.text }

    companion object {
        fun of(text: String, style: ParagraphStyle = ParagraphStyle()) = DocParagraph(listOf(DocRun(text)), style)
    }
}

data class DocTableCell(
    val blocks: List<DocBlock>,
    val shadingArgb: Int? = null,
    val paddingPt: Float = DEFAULT_CELL_PADDING,
) {
    companion object {
        const val DEFAULT_CELL_PADDING = 4f
    }
}

data class DocTableRow(val cells: List<DocTableCell>, val header: Boolean = false)

data class DocTable(
    val rows: List<DocTableRow>,
    /** Relative column widths; normalised by the renderer. Empty means equal columns. */
    val columnWeights: List<Float> = emptyList(),
    val borderArgb: Int? = GRID_GREY,
) : DocBlock {
    val columnCount: Int get() = maxOf(columnWeights.size, rows.maxOfOrNull { it.cells.size } ?: 0)

    companion object {
        const val GRID_GREY = -0x404041 // 0xFFBFBFBF
    }
}

/** An embedded picture, sized in points. [bytes] are JPEG or PNG. */
class DocImage(
    val bytes: ByteArray,
    val mimeType: String,
    val widthPt: Float,
    val heightPt: Float,
    val align: TextAlign = TextAlign.START,
) : DocBlock {
    override fun toString(): String = "DocImage($mimeType, ${widthPt}x$heightPt, ${bytes.size} bytes)"
}

data object DocPageBreak : DocBlock
