package com.mrfilefixit.core.office.xlsx

import com.mrfilefixit.core.domain.document.Sheet
import com.mrfilefixit.core.domain.document.SheetCell
import com.mrfilefixit.core.office.OfficeLimitException
import com.mrfilefixit.core.office.OfficeLimits

/** RFC 4180 CSV. Output starts with a UTF-8 BOM so Excel opens Hindi and other scripts correctly. */
object Csv {
    private const val BOM = '\uFEFF'
    private const val NEEDS_QUOTES = ",\"\n\r"

    fun write(rows: List<List<SheetCell>>): ByteArray {
        val sb = StringBuilder().append(BOM)
        rows.forEach { row ->
            sb.append(row.joinToString(",") { quote(it.display) }).append("\r\n")
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun quote(value: String): String =
        if (value.any { it in NEEDS_QUOTES } || value.startsWith(' ')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** Parses CSV (comma, semicolon or tab separated, detected from the first line) into a sheet. */
    fun read(text: String, name: String, limits: OfficeLimits = OfficeLimits.DEFAULT): Sheet {
        val body = text.removePrefix(BOM.toString())
        val delimiter = detectDelimiter(body.lineSequence().firstOrNull().orEmpty())
        val rows = mutableListOf<List<SheetCell>>()
        val row = mutableListOf<SheetCell>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        fun endField() {
            row += cellOf(field.toString())
            field.clear()
        }
        fun endRow() {
            endField()
            rows += row.toList()
            row.clear()
            if (rows.size > limits.maxSheetRows) throw OfficeLimitException("Too many rows")
        }
        while (i < body.length) {
            val c = body[i]
            when {
                quoted && c == '"' && body.getOrNull(i + 1) == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' && (quoted || field.isEmpty()) -> quoted = !quoted
                quoted -> field.append(c)
                c == delimiter -> endField()
                c == '\r' && body.getOrNull(i + 1) == '\n' -> Unit
                c == '\n' || c == '\r' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return Sheet(name, rows)
    }

    private fun detectDelimiter(firstLine: String): Char =
        listOf(',', ';', '\t').maxByOrNull { d -> firstLine.count { it == d } } ?: ','

    private fun cellOf(raw: String): SheetCell {
        if (raw.isEmpty()) return SheetCell.Empty
        val number = raw.trim().toDoubleOrNull()?.takeIf { raw.trim().none { it.isLetter() } }
        return if (number != null) SheetCell.Number(number, formatted = raw.trim()) else SheetCell.Text(raw)
    }
}
