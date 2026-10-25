package com.mrfilefixit.core.office.xlsx

import com.mrfilefixit.core.office.xml.XmlReader
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

/** The parts of styles.xml that change how a value reads: number formats (dates, percent) and bold fonts. */
internal class XlsxStyles(
    private val xfNumFmt: List<Int>,
    private val xfFont: List<Int>,
    private val boldFonts: Set<Int>,
    private val customFormats: Map<Int, String>,
) {
    fun isBold(xf: Int): Boolean = xfFont.getOrNull(xf)?.let { it in boldFonts } == true

    /** Display text for a numeric cell, or null to show the plain number. */
    fun format(xf: Int, value: Double): String? {
        val id = xfNumFmt.getOrNull(xf) ?: return null
        val code = customFormats[id]
        return when {
            id in BUILTIN_DATE_IDS || code != null && isDateCode(code) -> formatDate(value, hasTime(id, code))
            id == PERCENT_ID || id == PERCENT_DECIMAL_ID || code?.endsWith("%") == true -> formatPercent(value, id)
            else -> null
        }
    }

    companion object {
        val EMPTY = XlsxStyles(emptyList(), emptyList(), emptySet(), emptyMap())

        private val BUILTIN_DATE_IDS = (14..22).toSet() + (45..47).toSet()
        private val BUILTIN_TIME_IDS = setOf(18, 19, 20, 21, 22, 45, 46, 47)
        private const val PERCENT_ID = 9
        private const val PERCENT_DECIMAL_ID = 10
        private const val SECONDS_PER_DAY = 86_400.0

        /** Excel's day 0 in the 1900 date system, adjusted for its fictional 29 Feb 1900. */
        private val EPOCH = LocalDate.of(1899, 12, 30)
        private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        private const val MAX_SERIAL = 2_958_465.0 // 31 Dec 9999
        private val QUOTED = Regex("\"[^\"]*\"")

        fun read(xml: XmlReader): XlsxStyles {
            val numFmts = mutableMapOf<Int, String>()
            val fonts = mutableListOf<Boolean>()
            val xfNumFmt = mutableListOf<Int>()
            val xfFont = mutableListOf<Int>()
            while (xml.next() != XmlReader.Event.EOF) {
                if (xml.event != XmlReader.Event.START || xml.depth != 2) continue
                when (xml.localName) {
                    "numFmts" -> xml.forEachChild { f ->
                        val id = f.attr("numFmtId")?.toIntOrNull()
                        val code = f.attr("formatCode")
                        if (id != null && code != null) numFmts[id] = code
                    }
                    "fonts" -> xml.forEachChild { font ->
                        var bold = false
                        font.forEachChild { if (it.localName == "b") bold = it.attr("val") != "0" }
                        fonts += bold
                    }
                    "cellXfs" -> xml.forEachChild { xf ->
                        xfNumFmt += xf.attr("numFmtId")?.toIntOrNull() ?: 0
                        xfFont += xf.attr("fontId")?.toIntOrNull() ?: 0
                    }
                    else -> xml.skipElement()
                }
            }
            val bold = fonts.withIndex().filter { it.value }.map { it.index }.toSet()
            return XlsxStyles(xfNumFmt, xfFont, bold, numFmts)
        }

        /** A format code is a date when it has d/m/y/h/s outside quotes and brackets, and no plain digits. */
        fun isDateCode(code: String): Boolean {
            val stripped = code.replace(Regex("\"[^\"]*\""), "").replace(Regex("\\[[^]]*]"), "")
                .replace(Regex("\\\\."), "")
            return stripped.any { it.lowercaseChar() in "dmyhs" } && !stripped.contains("0") && !stripped.contains("#")
        }

        private fun hasTime(id: Int, code: String?): Boolean =
            id in BUILTIN_TIME_IDS ||
                code != null && code.lowercase().replace(QUOTED, "").any { it in "hs" }

        fun formatDate(serial: Double, withTime: Boolean): String? {
            if (serial < 0 || serial > MAX_SERIAL) return null
            val days = serial.toLong()
            val seconds = ((serial - days) * SECONDS_PER_DAY).roundToLong()
            val dateTime = LocalDateTime.of(EPOCH.plusDays(days), java.time.LocalTime.MIDNIGHT).plusSeconds(seconds)
            return if (withTime) dateTime.format(DATE_TIME) else dateTime.toLocalDate().format(DATE)
        }

        private fun formatPercent(value: Double, id: Int): String {
            val percent = value * 100
            return if (id == PERCENT_ID) {
                "${percent.roundToLong()}%"
            } else {
                "%.2f%%".format(
                    java.util.Locale.ROOT,
                    percent,
                )
            }
        }
    }
}
