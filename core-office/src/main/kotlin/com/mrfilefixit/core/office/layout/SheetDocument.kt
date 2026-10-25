package com.mrfilefixit.core.office.layout

import com.mrfilefixit.core.domain.document.DocBlock
import com.mrfilefixit.core.domain.document.DocPageBreak
import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.DocRun
import com.mrfilefixit.core.domain.document.DocTable
import com.mrfilefixit.core.domain.document.DocTableCell
import com.mrfilefixit.core.domain.document.DocTableRow
import com.mrfilefixit.core.domain.document.PageSetup
import com.mrfilefixit.core.domain.document.ParagraphStyle
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.domain.document.Sheet
import com.mrfilefixit.core.domain.document.SheetCell
import com.mrfilefixit.core.domain.document.TextAlign
import com.mrfilefixit.core.domain.document.Workbook

/**
 * Lays a workbook out as printable tables: one section per sheet, numbers right-aligned, a bold first row
 * repeated on every page, and wide sheets split into column bands so text stays readable on paper.
 */
object SheetDocument {
    private const val TABLE_SIZE = 9f
    private const val CELL_PADDING = 3f
    private const val MARGIN = 36f
    private const val LANDSCAPE_FROM = 7
    const val COLUMNS_PER_BAND = 10
    private const val DEFAULT_WIDTH = 8.43f
    private const val MIN_WIDTH = 3f
    private const val MAX_WIDTH = 40f
    private const val HEADER_FILL = -0xd0d0e // 0xFFF2F2F2

    fun toRichDocument(
        workbook: Workbook,
        paperWidthPt: Float,
        paperHeightPt: Float,
        title: String = "",
    ): RichDocument {
        val sheets = workbook.sheets.map(::trim).filter { it.rows.isNotEmpty() }
        val widest = sheets.maxOfOrNull { it.columnCount } ?: 0
        val landscape = widest >= LANDSCAPE_FROM
        val page = PageSetup(
            widthPt = if (landscape) paperHeightPt else paperWidthPt,
            heightPt = if (landscape) paperWidthPt else paperHeightPt,
            marginTopPt = MARGIN,
            marginBottomPt = MARGIN,
            marginStartPt = MARGIN,
            marginEndPt = MARGIN,
        )
        val blocks = mutableListOf<DocBlock>()
        sheets.forEachIndexed { index, sheet ->
            if (index > 0) blocks += DocPageBreak
            if (sheets.size > 1) blocks += DocParagraph.of(sheet.name, ParagraphStyle(heading = 2, keepWithNext = true))
            blocks += bands(sheet)
        }
        if (blocks.isEmpty()) blocks += DocParagraph.of("")
        return RichDocument(blocks = blocks, page = page, baseSizePt = TABLE_SIZE, title = title)
    }

    /** Drops empty trailing rows and columns. */
    private fun trim(sheet: Sheet): Sheet {
        val rows = sheet.rows.map { row -> row.dropLastWhile { it == SheetCell.Empty } }.dropLastWhile { it.isEmpty() }
        return sheet.copy(rows = rows)
    }

    private fun bands(sheet: Sheet): List<DocBlock> {
        val columns = sheet.columnCount
        val header = sheet.rows.first().let { first -> first.isNotEmpty() && first.all { it.isHeaderLike() } }
        return (0 until columns step COLUMNS_PER_BAND).flatMap { start ->
            val end = minOf(columns, start + COLUMNS_PER_BAND)
            val label = if (columns > COLUMNS_PER_BAND) {
                listOf(DocParagraph.of("${columnName(start)}–${columnName(end - 1)}", ParagraphStyle(heading = 0)))
            } else {
                emptyList()
            }
            label + table(sheet, start until end, header)
        }
    }

    private fun table(sheet: Sheet, columns: IntRange, header: Boolean): DocTable {
        val rows = sheet.rows.mapIndexed { r, row ->
            val isHeader = header && r == 0
            DocTableRow(
                cells = columns.map { c -> cell(row.getOrNull(c) ?: SheetCell.Empty, isHeader) },
                header = isHeader,
            )
        }
        val weights = columns.map { c ->
            (sheet.columnWidths[c] ?: autoWidth(sheet, c)).coerceIn(MIN_WIDTH, MAX_WIDTH)
        }
        return DocTable(rows, weights)
    }

    private fun autoWidth(sheet: Sheet, column: Int): Float {
        val longest = sheet.rows.maxOfOrNull { row -> row.getOrNull(column)?.display?.length ?: 0 } ?: 0
        return if (longest == 0) DEFAULT_WIDTH else longest.toFloat()
    }

    private fun cell(value: SheetCell, header: Boolean): DocTableCell {
        val bold = header || value is SheetCell.Text && value.bold || value is SheetCell.Number && value.bold
        val align = if (value is SheetCell.Number || value is SheetCell.Bool) TextAlign.END else TextAlign.START
        val paragraph = DocParagraph(
            listOf(DocRun(value.display, bold = bold)),
            ParagraphStyle(align = align, spaceAfterPt = 0f, lineSpacing = 1f),
        )
        return DocTableCell(
            listOf(paragraph),
            shadingArgb = if (header) HEADER_FILL else null,
            paddingPt = CELL_PADDING,
        )
    }

    private fun SheetCell.isHeaderLike(): Boolean = this is SheetCell.Text && bold || this == SheetCell.Empty

    /** 0 → A, 25 → Z, 26 → AA. */
    fun columnName(index: Int): String {
        val sb = StringBuilder()
        var n = index + 1
        while (n > 0) {
            val rem = (n - 1) % ALPHABET
            sb.append('A' + rem)
            n = (n - 1) / ALPHABET
        }
        return sb.reverse().toString()
    }

    private const val ALPHABET = 26
}
