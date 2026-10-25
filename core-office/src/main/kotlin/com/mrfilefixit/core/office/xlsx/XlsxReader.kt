package com.mrfilefixit.core.office.xlsx

import com.mrfilefixit.core.domain.document.Sheet
import com.mrfilefixit.core.domain.document.SheetCell
import com.mrfilefixit.core.domain.document.Workbook
import com.mrfilefixit.core.office.OfficeFormatException
import com.mrfilefixit.core.office.OfficeLimitException
import com.mrfilefixit.core.office.OfficeLimits
import com.mrfilefixit.core.office.xml.XmlReader
import com.mrfilefixit.core.office.zip.OpcPackage
import java.io.File

/**
 * Reads cell values from an .xlsx workbook: shared and inline strings, numbers, booleans, cached formula
 * results, bold cells and column widths. Dates are shown as dates when their cell format says so.
 * Charts, images, merged-cell layout and conditional formatting are not carried over.
 */
class XlsxReader(private val limits: OfficeLimits = OfficeLimits.DEFAULT) {

    fun read(file: File): Workbook = OpcPackage(file, limits).use { pkg ->
        val main = pkg.rootPart("/officeDocument") ?: throw OfficeFormatException("Not a workbook")
        val rels = pkg.relationships(main)
        val shared = rels.values.firstOrNull { it.type.endsWith("/sharedStrings") }
            ?.let { pkg.readXml(it.target) }?.let(::readSharedStrings).orEmpty()
        val styles = rels.values.firstOrNull { it.type.endsWith("/styles") }
            ?.let { pkg.readXml(it.target) }?.let(XlsxStyles::read) ?: XlsxStyles.EMPTY
        val workbook = pkg.readXml(main) ?: throw OfficeFormatException("Missing workbook part")
        val sheets = mutableListOf<Sheet>()
        while (workbook.next() != XmlReader.Event.EOF) {
            if (workbook.event != XmlReader.Event.START || workbook.localName != "sheet") continue
            val name = workbook.attr("name") ?: "Sheet${sheets.size + 1}"
            val xml = workbook.attr(R_NS, "id")?.let { rels[it] }?.let { pkg.readXml(it.target) }
            if (xml != null) sheets += readSheet(name, xml, shared, styles)
        }
        Workbook(sheets)
    }

    private fun readSharedStrings(xml: XmlReader): List<String> {
        val strings = mutableListOf<String>()
        while (xml.next() != XmlReader.Event.EOF) {
            if (xml.event == XmlReader.Event.START && xml.localName == "si") strings += readStringItem(xml)
        }
        return strings
    }

    /** Text of an `si` or `is` element: plain `t` or rich-text runs, ignoring phonetic hints (`rPh`). */
    private fun readStringItem(xml: XmlReader): String {
        val sb = StringBuilder()
        xml.forEachChild { c ->
            when (c.localName) {
                "t" -> sb.append(c.readElementText())
                "r" -> c.forEachChild { r -> if (r.localName == "t") sb.append(r.readElementText()) }
            }
        }
        return sb.toString()
    }

    private fun readSheet(name: String, xml: XmlReader, shared: List<String>, styles: XlsxStyles): Sheet {
        val rows = java.util.TreeMap<Int, MutableMap<Int, SheetCell>>()
        val widths = mutableMapOf<Int, Float>()
        while (xml.next() != XmlReader.Event.EOF) {
            if (xml.event != XmlReader.Event.START) continue
            when (xml.localName) {
                "col" -> readColumn(xml, widths)
                "row" -> {
                    val rowIndex = (xml.attr("r")?.toIntOrNull() ?: (rows.lastKeyOrNull() ?: -1) + 2) - 1
                    if (rowIndex >= limits.maxSheetRows) throw OfficeLimitException("Too many rows")
                    val cells = rows.getOrPut(rowIndex) { mutableMapOf() }
                    xml.forEachChild { c -> if (c.localName == "c") readCell(c, cells, shared, styles) }
                }
            }
        }
        val lastRow = rows.lastKeyOrNull() ?: -1
        val grid = (0..lastRow).map { r ->
            val cells = rows[r].orEmpty()
            val lastCol = cells.keys.maxOrNull() ?: -1
            (0..lastCol).map { cells[it] ?: SheetCell.Empty }
        }
        return Sheet(name, trimTrailingEmpty(grid), widths)
    }

    private fun readColumn(xml: XmlReader, widths: MutableMap<Int, Float>) {
        val min = xml.attr("min")?.toIntOrNull() ?: return
        val max = (xml.attr("max")?.toIntOrNull() ?: min).coerceAtMost(min + MAX_COL_SPAN)
        val width = xml.attr("width")?.toFloatOrNull() ?: return
        for (c in min..max) widths[c - 1] = width
    }

    private fun readCell(xml: XmlReader, cells: MutableMap<Int, SheetCell>, shared: List<String>, styles: XlsxStyles) {
        val column = xml.attr("r")?.let(::columnIndex) ?: (cells.keys.maxOrNull()?.plus(1) ?: 0)
        if (column >= limits.maxSheetColumns) {
            xml.skipElement()
            return
        }
        val type = xml.attr("t") ?: "n"
        val style = xml.attr("s")?.toIntOrNull() ?: 0
        var value: String? = null
        var inline: String? = null
        xml.forEachChild { c ->
            when (c.localName) {
                "v" -> value = c.readElementText()
                "is" -> inline = readStringItem(c)
            }
        }
        val bold = styles.isBold(style)
        val cell = when (type) {
            "s" -> value?.toIntOrNull()?.let { shared.getOrNull(it) }?.let { SheetCell.Text(it, bold) }
            "inlineStr" -> inline?.let { SheetCell.Text(it, bold) }
            "str", "e" -> value?.let { SheetCell.Text(it, bold) }
            "b" -> value?.let { SheetCell.Bool(it == "1" || it == "true") }
            else -> value?.toDoubleOrNull()?.let { SheetCell.Number(it, styles.format(style, it), bold) }
        }
        if (cell != null) cells[column] = cell
    }

    companion object {
        private const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val LETTERS = 26
        private const val MAX_COL_SPAN = 1_000

        /** "B7" → 1, "AA3" → 26. */
        fun columnIndex(ref: String): Int? {
            var index = 0
            var letters = 0
            for (c in ref) {
                if (!c.isLetter()) break
                index = index * LETTERS + (c.uppercaseChar() - 'A' + 1)
                letters++
            }
            return if (letters == 0) null else index - 1
        }

        private fun <V> java.util.TreeMap<Int, V>.lastKeyOrNull(): Int? = if (isEmpty()) null else lastKey()

        private fun trimTrailingEmpty(rows: List<List<SheetCell>>): List<List<SheetCell>> {
            val trimmed = rows.map { row -> row.dropLastWhile { it == SheetCell.Empty } }
            return trimmed.dropLastWhile { it.isEmpty() }
        }
    }
}
