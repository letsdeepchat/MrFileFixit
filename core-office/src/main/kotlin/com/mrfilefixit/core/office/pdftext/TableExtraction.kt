package com.mrfilefixit.core.office.pdftext

import com.mrfilefixit.core.domain.document.Sheet
import com.mrfilefixit.core.domain.document.SheetCell
import kotlin.math.abs

/**
 * Turns a page's text into a grid for spreadsheets. Text pieces separated by wide gaps are cells; columns
 * are the union of where cells sit across all rows, so a column keeps its place even when some rows leave it
 * empty. Numbers (with thousands separators, currency symbols, percent and accounting negatives) become numbers.
 */
object TableExtraction {
    private const val ROW_TOLERANCE = 0.4f
    private const val COLUMN_SLACK = 1f

    fun sheet(page: PdfPageText, name: String): Sheet {
        val pieces = TextLayout.lines(page.glyphs, TextLayout.TABLE_SPLIT)
        if (pieces.isEmpty()) return Sheet(name, emptyList())
        val rows = groupRows(pieces)
        val columns = columnRanges(pieces)
        val grid = rows.map { row ->
            val cells = arrayOfNulls<String>(columns.size)
            row.forEach { piece ->
                val center = (piece.x + piece.right) / 2
                val index = columns.indexOfFirst { center >= it.start && center <= it.endInclusive }
                    .takeIf { it >= 0 } ?: columns.indexOfFirst { piece.x <= it.endInclusive }.coerceAtLeast(0)
                cells[index] = listOfNotNull(cells[index], piece.text).joinToString(" ")
            }
            cells.map { text -> text?.let(::cellOf) ?: SheetCell.Empty }.dropLastWhile { it == SheetCell.Empty }
        }
        return Sheet(name, grid)
    }

    private fun groupRows(pieces: List<TextLine>): List<List<TextLine>> {
        val rows = mutableListOf<MutableList<TextLine>>()
        for (piece in pieces.sortedBy { it.baseline }) {
            val row = rows.lastOrNull()
            if (row != null && abs(row.first().baseline - piece.baseline) <= ROW_TOLERANCE * piece.fontSize) {
                row += piece
            } else {
                rows += mutableListOf(piece)
            }
        }
        return rows.map { row -> row.sortedBy { it.x } }
    }

    /** Merges the horizontal extents of all pieces into disjoint column ranges, left to right. */
    fun columnRanges(pieces: List<TextLine>): List<ClosedFloatingPointRange<Float>> {
        val sorted = pieces.map { it.x to it.right }.sortedBy { it.first }
        val merged = mutableListOf<Pair<Float, Float>>()
        for ((start, end) in sorted) {
            val last = merged.lastOrNull()
            if (last != null && start <= last.second + COLUMN_SLACK) {
                merged[merged.size - 1] = last.first to maxOf(last.second, end)
            } else {
                merged += start to end
            }
        }
        return merged.map { (a, b) -> a..b }
    }

    private val NUMBER = Regex(
        """^\(?[-+]?[₹$€£¥]?\s?[-+]?\d{1,3}([,\s]\d{2,3})*(\.\d+)?\)?%?$|^\(?[-+]?[₹$€£¥]?\s?[-+]?\d+(\.\d+)?\)?%?$""",
    )

    fun cellOf(text: String): SheetCell {
        val trimmed = text.trim()
        if (!NUMBER.matches(trimmed)) return SheetCell.Text(trimmed)
        val negative = trimmed.startsWith("(") && trimmed.endsWith(")") || trimmed.startsWith("-")
        val percent = trimmed.endsWith("%")
        val digits = trimmed.filter { it.isDigit() || it == '.' }
        val value = digits.toDoubleOrNull() ?: return SheetCell.Text(trimmed)
        val signed = if (negative) -value else value
        return SheetCell.Number(if (percent) signed / 100 else signed, formatted = trimmed)
    }
}
