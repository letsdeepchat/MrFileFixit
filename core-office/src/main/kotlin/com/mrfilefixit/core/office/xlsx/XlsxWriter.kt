package com.mrfilefixit.core.office.xlsx

import com.mrfilefixit.core.domain.document.SheetCell
import com.mrfilefixit.core.domain.document.Workbook
import com.mrfilefixit.core.office.xml.XmlWriter
import com.mrfilefixit.core.office.zip.OpcWriter
import com.mrfilefixit.core.office.zip.OpcWriter.RelEntry
import java.io.OutputStream

/** Writes a [Workbook] as .xlsx. Strings are inline, numbers stay numbers, bold cells use a bold font. */
class XlsxWriter {
    fun write(workbook: Workbook, output: OutputStream) {
        val opc = OpcWriter(output)
        val names = uniqueSheetNames(workbook.sheets.map { it.name })
        val rels = mutableListOf<RelEntry>()
        workbook.sheets.forEachIndexed { i, sheet ->
            val part = "worksheets/sheet${i + 1}.xml"
            rels += RelEntry("rId${i + 1}", OpcWriter.REL_BASE + "worksheet", part)
            opc.addPart("xl/$part", CT_SHEET, sheetXml(sheet.rows, sheet.columnWidths))
        }
        rels += RelEntry("rId${rels.size + 1}", OpcWriter.REL_BASE + "styles", "styles.xml")
        opc.addPart("xl/workbook.xml", CT_WORKBOOK, workbookXml(names))
        opc.addPart("xl/styles.xml", CT_STYLES, stylesXml())
        opc.addPart("xl/_rels/workbook.xml.rels", null, OpcWriter.relationshipsXml(rels))
        opc.addPart("docProps/core.xml", OpcWriter.CT_CORE, OpcWriter.coreProperties("", ""))
        opc.addPart("docProps/app.xml", OpcWriter.CT_APP, OpcWriter.appProperties())
        opc.addRootRelationship(OpcWriter.REL_OFFICE_DOCUMENT, "xl/workbook.xml")
        opc.addRootRelationship(OpcWriter.REL_CORE_PROPERTIES, "docProps/core.xml")
        opc.addRootRelationship(OpcWriter.REL_EXTENDED_PROPERTIES, "docProps/app.xml")
        opc.finish()
    }

    private fun workbookXml(names: List<String>) = XmlWriter().within(
        "workbook",
        "xmlns" to MAIN_NS,
        "xmlns:r" to R_NS,
    ) {
        within("sheets") {
            names.forEachIndexed { i, name ->
                empty(
                    "sheet",
                    "name" to name,
                    "sheetId" to i + 1,
                    "r:id" to "rId${i + 1}",
                )
            }
        }
    }

    private fun sheetXml(rows: List<List<SheetCell>>, widths: Map<Int, Float>) =
        XmlWriter().within("worksheet", "xmlns" to MAIN_NS, "xmlns:r" to R_NS) {
            val autoWidths = autoColumnWidths(rows)
            val columns = (autoWidths.keys + widths.keys).sorted()
            if (columns.isNotEmpty()) {
                within("cols") {
                    columns.forEach { c ->
                        val width = widths[c] ?: autoWidths[c] ?: DEFAULT_WIDTH
                        empty(
                            "col",
                            "min" to c + 1,
                            "max" to c + 1,
                            "width" to "%.1f".format(java.util.Locale.ROOT, width),
                            "customWidth" to 1,
                        )
                    }
                }
            }
            within("sheetData") {
                rows.forEachIndexed { r, row ->
                    within("row", "r" to r + 1) {
                        row.forEachIndexed { c, cell -> cell(cellRef(c, r), cell) }
                    }
                }
            }
        }

    private fun XmlWriter.cell(ref: String, cell: SheetCell) {
        when (cell) {
            SheetCell.Empty -> Unit
            is SheetCell.Text -> within("c", "r" to ref, "t" to "inlineStr", "s" to if (cell.bold) 1 else null) {
                within("is") { element("t", cell.value, "xml:space" to "preserve") }
            }
            is SheetCell.Number -> within("c", "r" to ref, "s" to if (cell.bold) 1 else null) {
                element("v", SheetCell.formatPlain(cell.value))
            }
            is SheetCell.Bool -> within("c", "r" to ref, "t" to "b") { element("v", if (cell.value) "1" else "0") }
        }
    }

    private fun stylesXml() = XmlWriter().within("styleSheet", "xmlns" to MAIN_NS) {
        within("fonts", "count" to 2) {
            within("font") { font(bold = false) }
            within("font") { font(bold = true) }
        }
        within("fills", "count" to 2) {
            within("fill") { empty("patternFill", "patternType" to "none") }
            within("fill") { empty("patternFill", "patternType" to "gray125") }
        }
        within("borders", "count" to 1) {
            within("border") { listOf("left", "right", "top", "bottom", "diagonal").forEach { empty(it) } }
        }
        within(
            "cellStyleXfs",
            "count" to 1,
        ) { empty("xf", "numFmtId" to 0, "fontId" to 0, "fillId" to 0, "borderId" to 0) }
        within("cellXfs", "count" to 2) {
            empty("xf", "numFmtId" to 0, "fontId" to 0, "fillId" to 0, "borderId" to 0, "xfId" to 0)
            empty("xf", "numFmtId" to 0, "fontId" to 1, "fillId" to 0, "borderId" to 0, "xfId" to 0, "applyFont" to 1)
        }
        within("cellStyles", "count" to 1) { empty("cellStyle", "name" to "Normal", "xfId" to 0, "builtinId" to 0) }
    }

    private fun XmlWriter.font(bold: Boolean) {
        if (bold) empty("b")
        empty("sz", "val" to FONT_SIZE)
        empty("name", "val" to "Calibri")
        empty("family", "val" to 2)
    }

    companion object {
        private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val CT_WORKBOOK = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"
        private const val CT_SHEET = "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"
        private const val CT_STYLES = "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"
        private const val FONT_SIZE = 11
        private const val DEFAULT_WIDTH = 10f
        private const val MIN_WIDTH = 6f
        private const val MAX_WIDTH = 60f
        private const val WIDTH_PADDING = 2f
        private const val MAX_SHEET_NAME = 31
        private const val LETTERS = 26
        private val ILLEGAL_SHEET_CHARS = Regex("""[\[\]:*?/\\]""")

        /** "A1"-style reference for zero-based [column] and [row]. */
        fun cellRef(column: Int, row: Int): String {
            val sb = StringBuilder()
            var n = column + 1
            while (n > 0) {
                val rem = (n - 1) % LETTERS
                sb.append('A' + rem)
                n = (n - 1) / LETTERS
            }
            return sb.reverse().toString() + (row + 1)
        }

        /** Excel sheet names: 1-31 characters, none of []:*?/\, unique ignoring case. */
        fun uniqueSheetNames(names: List<String>): List<String> {
            val used = mutableSetOf<String>()
            return names.mapIndexed { i, raw ->
                val base = raw.replace(ILLEGAL_SHEET_CHARS, " ").trim().take(MAX_SHEET_NAME).ifBlank { "Sheet${i + 1}" }
                var name = base
                var n = 2
                while (!used.add(name.lowercase())) {
                    val suffix = " ($n)"
                    name = base.take(MAX_SHEET_NAME - suffix.length) + suffix
                    n++
                }
                name
            }
        }

        private fun autoColumnWidths(rows: List<List<SheetCell>>): Map<Int, Float> {
            val widths = mutableMapOf<Int, Float>()
            rows.forEach { row ->
                row.forEachIndexed { c, cell ->
                    val len = cell.display.lines().maxOfOrNull { it.length } ?: 0
                    val width = (len + WIDTH_PADDING).coerceIn(MIN_WIDTH, MAX_WIDTH)
                    if (len > 0) widths[c] = maxOf(widths[c] ?: 0f, width)
                }
            }
            return widths
        }
    }
}
