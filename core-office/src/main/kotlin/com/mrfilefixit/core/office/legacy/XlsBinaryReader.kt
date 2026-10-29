package com.mrfilefixit.core.office.legacy

import com.mrfilefixit.core.domain.document.Sheet
import com.mrfilefixit.core.domain.document.SheetCell
import com.mrfilefixit.core.domain.document.Workbook
import com.mrfilefixit.core.office.OfficeEncryptedException
import com.mrfilefixit.core.office.OfficeFormatException
import com.mrfilefixit.core.office.OfficeLimitException
import com.mrfilefixit.core.office.OfficeLimits
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * Cell values of a legacy Excel 97-2003 (.xls, BIFF8) workbook, and of Excel 5/95 (BIFF5) files:
 * strings, numbers, booleans and cached formula results. Formatting, dates and merged cells are not read
 * (dates show as their serial numbers).
 */
class XlsBinaryReader(private val limits: OfficeLimits = OfficeLimits.DEFAULT) {

    private class SheetRef(val name: String, val offset: Int)

    fun read(data: ByteArray): Workbook {
        val cfb = CompoundFile(data)
        val (stream, biff8) = cfb.stream("Workbook")?.let { it to true }
            ?: cfb.stream("Book")?.let { it to false }
            ?: throw OfficeFormatException("No workbook stream")
        val sheets = mutableListOf<SheetRef>()
        val sst = mutableListOf<String>()
        val records = BiffRecords(stream)
        var record = records.next()
        while (record != null && record.type != EOF) {
            when (record.type) {
                FILEPASS -> throw OfficeEncryptedException()
                BOUNDSHEET -> readBoundSheet(record.data, biff8)?.let { sheets += it }
                SST -> sst += readSst(record.data, records.continuations())
            }
            record = records.next()
        }
        return Workbook(sheets.map { readSheet(stream, it, sst, biff8) })
    }

    private fun readBoundSheet(data: ByteArray, biff8: Boolean): SheetRef? {
        if (data.size < BOUNDSHEET_NAME) return null
        val b = le(data)
        val offset = b.getInt(0)
        val type = data[BOUNDSHEET_TYPE].toInt()
        if (type != WORKSHEET_TYPE) return null // chart sheets and macro sheets carry no cells
        val cch = data[BOUNDSHEET_NAME].toUByte().toInt()
        val name = if (biff8) {
            val high = data.getOrNull(BOUNDSHEET_NAME + 1)?.toInt()?.and(1) == 1
            readChars(data, BOUNDSHEET_NAME + 2, cch, high)
        } else {
            readChars(data, BOUNDSHEET_NAME + 1, cch, high = false)
        }
        return SheetRef(name, offset)
    }

    private fun readSheet(stream: ByteArray, ref: SheetRef, sst: List<String>, biff8: Boolean): Sheet {
        if (ref.offset !in stream.indices) return Sheet(ref.name, emptyList())
        val records = BiffRecords(stream, ref.offset)
        val cells = sortedMapOf<Int, MutableMap<Int, SheetCell>>()
        var pendingFormula: Pair<Int, Int>? = null
        fun put(row: Int, col: Int, cell: SheetCell) {
            if (row >= limits.maxSheetRows) throw OfficeLimitException("Too many rows")
            if (col < limits.maxSheetColumns) cells.getOrPut(row) { mutableMapOf() }[col] = cell
        }
        records.next() // the sheet's own BOF
        var record = records.next()
        while (record != null && record.type != EOF && record.type != BOF) {
            val formula = if (record.data.size >= CELL_HEADER || record.type !in CELL_RECORDS) {
                readCell(record, sst, biff8, pendingFormula, ::put)
            } else {
                null
            }
            pendingFormula = formula
            record = records.next()
        }
        val lastRow = if (cells.isEmpty()) -1 else cells.lastKey()
        val rows = (0..lastRow).map { r ->
            val row = cells[r].orEmpty()
            val last = row.keys.maxOrNull() ?: -1
            (0..last).map { row[it] ?: SheetCell.Empty }
        }
        return Sheet(ref.name, rows.dropLastWhile { it.isEmpty() })
    }

    /** Stores one cell record; returns the formula cell still waiting for its STRING record, if any. */
    private fun readCell(
        record: BiffRecords.Record,
        sst: List<String>,
        biff8: Boolean,
        pendingFormula: Pair<Int, Int>?,
        put: (Int, Int, SheetCell) -> Unit,
    ): Pair<Int, Int>? {
        val d = record.data
        val b = le(d)
        when (record.type) {
            LABELSST -> if (d.size >= CELL_HEADER + Int.SIZE_BYTES) {
                sst.getOrNull(b.getInt(CELL_HEADER))?.let { put(row(b), col(b), SheetCell.Text(it)) }
            }
            NUMBER -> if (d.size >= CELL_HEADER + DOUBLE) {
                put(
                    row(b),
                    col(b),
                    SheetCell.Number(b.getDouble(CELL_HEADER)),
                )
            }
            RK -> if (d.size >= CELL_HEADER + Int.SIZE_BYTES) {
                put(row(b), col(b), SheetCell.Number(decodeRk(b.getInt(CELL_HEADER))))
            }
            MULRK -> readMulRk(b, d.size) { r, c, v -> put(r, c, SheetCell.Number(v)) }
            LABEL -> put(row(b), col(b), SheetCell.Text(readLabel(d, biff8)))
            BOOLERR -> if (d.size >= CELL_HEADER + 2 && d[CELL_HEADER + 1].toInt() == 0) {
                put(row(b), col(b), SheetCell.Bool(d[CELL_HEADER].toInt() != 0))
            }
            FORMULA -> return readFormula(b, d, put)
            STRING -> pendingFormula?.let { (r, c) -> put(r, c, SheetCell.Text(readLabel(d, biff8, offset = 0))) }
        }
        return null
    }

    /** Returns the formula's cell when its string result follows in a STRING record. */
    private fun readFormula(b: ByteBuffer, d: ByteArray, put: (Int, Int, SheetCell) -> Unit): Pair<Int, Int>? {
        if (d.size < CELL_HEADER + DOUBLE) return null
        val r = row(b)
        val c = col(b)
        val special = b.getShort(CELL_HEADER + SPECIAL_MARK_OFFSET).toUShort().toInt() == SPECIAL_MARK
        if (!special) {
            put(r, c, SheetCell.Number(b.getDouble(CELL_HEADER)))
            return null
        }
        return when (d[CELL_HEADER].toInt()) {
            0 -> r to c // string: value comes in the next STRING record
            1 -> {
                put(r, c, SheetCell.Bool(d[CELL_HEADER + 2].toInt() != 0))
                null
            }
            else -> null
        }
    }

    private inline fun readMulRk(b: ByteBuffer, size: Int, put: (Int, Int, Double) -> Unit) {
        if (size < MULRK_MIN) return
        val r = row(b)
        val first = col(b)
        val count = (size - MULRK_MIN) / MULRK_ITEM
        for (i in 0 until count) {
            val off = 4 + i * MULRK_ITEM
            put(r, first + i, decodeRk(b.getInt(off + 2)))
        }
    }

    private fun readLabel(d: ByteArray, biff8: Boolean, offset: Int = CELL_HEADER): String {
        if (d.size < offset + 2) return ""
        val cch = le(d).getShort(offset).toUShort().toInt()
        return if (biff8) {
            val high = d.getOrNull(offset + 2)?.toInt()?.and(1) == 1
            readChars(d, offset + BIFF8_LABEL_HEADER, cch, high)
        } else {
            readChars(d, offset + 2, cch, high = false)
        }
    }

    /** Shared strings, which may continue across CONTINUE records (each continuation restates the char width). */
    private fun readSst(first: ByteArray, continuations: List<ByteArray>): List<String> {
        val reader = SegmentReader(listOf(first) + continuations)
        reader.skip(4)
        val unique = reader.u32()
        val out = ArrayList<String>(unique.coerceIn(0, MAX_PRESIZE))
        repeat(unique.coerceAtMost(MAX_STRINGS)) {
            if (!reader.hasMore()) return out
            out += reader.unicodeString()
        }
        return out
    }

    companion object {
        private const val BOF = 0x0809
        private const val EOF = 0x000A
        private const val FILEPASS = 0x002F
        private const val BOUNDSHEET = 0x0085
        private const val SST = 0x00FC
        private const val CONTINUE = 0x003C
        private const val LABELSST = 0x00FD
        private const val NUMBER = 0x0203
        private const val RK = 0x027E
        private const val MULRK = 0x00BD
        private const val LABEL = 0x0204
        private const val BOOLERR = 0x0205
        private const val FORMULA = 0x0006
        private const val STRING = 0x0207
        private val CELL_RECORDS = setOf(LABELSST, NUMBER, RK, LABEL, BOOLERR, FORMULA)
        private const val CELL_HEADER = 6
        private const val DOUBLE = 8
        private const val SPECIAL_MARK_OFFSET = 6
        private const val SPECIAL_MARK = 0xFFFF
        private const val MULRK_MIN = 6
        private const val MULRK_ITEM = 6
        private const val BOUNDSHEET_TYPE = 5
        private const val BOUNDSHEET_NAME = 6
        private const val WORKSHEET_TYPE = 0
        private const val MAX_STRINGS = 2_000_000
        private const val MAX_PRESIZE = 65_536
        private val CP1252: Charset = Charset.forName("windows-1252")

        private fun le(d: ByteArray): ByteBuffer = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)

        private fun row(b: ByteBuffer) = b.getShort(0).toUShort().toInt()

        private fun col(b: ByteBuffer) = b.getShort(2).toUShort().toInt()

        fun readChars(d: ByteArray, offset: Int, cch: Int, high: Boolean): String {
            val bytes = if (high) cch * 2 else cch
            val n = bytes.coerceAtMost(d.size - offset).coerceAtLeast(0)
            return if (high) String(d, offset, n - n % 2, Charsets.UTF_16LE) else String(d, offset, n, CP1252)
        }

        /** RK numbers: 30-bit integer or the high bits of a double, optionally divided by 100. */
        fun decodeRk(rk: Int): Double {
            val value = if (rk and 2 != 0) {
                (rk shr 2).toDouble()
            } else {
                java.lang.Double.longBitsToDouble((rk.toLong() and RK_MASK) shl RK_SHIFT)
            }
            return if (rk and 1 != 0) value / 100 else value
        }

        private const val RK_MASK = 0xFFFFFFFCL
        private const val RK_SHIFT = 32
        private const val BIFF8_LABEL_HEADER = 3
    }

    /** Iterates BIFF records: type (u16), length (u16), data. */
    private class BiffRecords(private val stream: ByteArray, private var pos: Int = 0) {
        class Record(val type: Int, val data: ByteArray)

        fun next(): Record? {
            if (pos + HEADER > stream.size) return null
            val type = u16At(pos)
            val len = u16At(pos + 2)
            val start = pos + HEADER
            val end = (start + len).coerceAtMost(stream.size)
            pos = end
            return Record(type, stream.copyOfRange(start, end))
        }

        /** Consumes the CONTINUE records right after the current one. */
        fun continuations(): List<ByteArray> {
            val out = mutableListOf<ByteArray>()
            while (pos + HEADER <= stream.size) {
                if (u16At(pos) != CONTINUE) break
                out += next()?.data ?: break
            }
            return out
        }

        private fun u16At(at: Int): Int = ByteBuffer.wrap(
            stream,
            at,
            2,
        ).order(ByteOrder.LITTLE_ENDIAN).short.toUShort().toInt()

        private companion object {
            const val HEADER = 4
        }
    }

    /** Reads across record boundaries the way SST strings require. */
    private class SegmentReader(private val segments: List<ByteArray>) {
        private var seg = 0
        private var pos = 0

        fun hasMore(): Boolean {
            normalize()
            return seg < segments.size
        }

        private fun normalize() {
            while (seg < segments.size && pos >= segments[seg].size) {
                seg++
                pos = 0
            }
        }

        fun u8(): Int {
            normalize()
            if (seg >= segments.size) throw OfficeFormatException("SST truncated")
            return segments[seg][pos++].toUByte().toInt()
        }

        fun u16(): Int = u8() or (u8() shl Byte.SIZE_BITS)

        fun u32(): Int = u16() or (u16() shl Short.SIZE_BITS)

        fun skip(n: Int) {
            repeat(n) { u8() }
        }

        fun unicodeString(): String {
            val cch = u16()
            val flags = u8()
            var high = flags and 1 != 0
            val runs = if (flags and RICH != 0) u16() else 0
            val ext = if (flags and EXT != 0) u32() else 0
            val sb = StringBuilder(cch)
            var charSegment = seg
            while (sb.length < cch) {
                normalize()
                if (seg >= segments.size) break
                if (seg != charSegment) {
                    // Character data continued in a CONTINUE record, which restates the character width.
                    high = u8() and 1 != 0
                    charSegment = seg
                }
                val left = segments[seg].size - pos
                val take = minOf(cch - sb.length, if (high) left / 2 else left)
                if (take <= 0) {
                    pos = segments[seg].size
                    continue
                }
                val bytes = if (high) take * 2 else take
                sb.append(readChars(segments[seg], pos, take, high))
                pos += bytes
            }
            skip(runs * RUN_SIZE + ext)
            return sb.toString()
        }

        private companion object {
            const val RICH = 0x08
            const val EXT = 0x04
            const val RUN_SIZE = 4
        }
    }
}
