package com.mrfilefixit.core.office.legacy

import com.mrfilefixit.core.office.OfficeFormatException
import com.mrfilefixit.core.office.OfficeLimitException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reader for the Compound File Binary format (OLE2), the container of .doc, .xls and .ppt files.
 *
 * Every sector index, chain and stream size is bounds-checked and chains are cycle-checked, because
 * a malformed container is a classic way to make a parser loop forever or read out of bounds.
 */
class CompoundFile(private val data: ByteArray) {
    private val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    private val sectorSize: Int
    private val miniSectorSize: Int
    private val miniCutoff: Int
    private val fat: IntArray
    private val miniFat: IntArray
    private val entries: List<Entry>
    private val miniStream: ByteArray

    class Entry(val name: String, val type: Int, val start: Int, val size: Long)

    init {
        if (data.size < HEADER_SIZE || buf.getLong(0) != SIGNATURE) throw OfficeFormatException("Not a compound file")
        val sectorShift = buf.getShort(SECTOR_SHIFT_OFFSET).toInt()
        val miniShift = buf.getShort(MINI_SHIFT_OFFSET).toInt()
        if (sectorShift !in MIN_SHIFT..MAX_SHIFT || miniShift !in 1 until sectorShift) {
            throw OfficeFormatException("Bad sector size")
        }
        sectorSize = 1 shl sectorShift
        miniSectorSize = 1 shl miniShift
        miniCutoff = buf.getInt(MINI_CUTOFF_OFFSET)
        fat = readFat()
        entries = readDirectory(buf.getInt(DIR_START_OFFSET))
        val root = entries.firstOrNull { it.type == ROOT_TYPE } ?: throw OfficeFormatException("No root entry")
        miniFat = chainBytes(buf.getInt(MINI_FAT_START_OFFSET), Long.MAX_VALUE).toIntArray()
        miniStream = chainBytes(root.start, root.size)
    }

    /** Names of the streams directly or indirectly stored in the file. */
    val streamNames: List<String> get() = entries.filter { it.type == STREAM_TYPE }.map { it.name }

    /** Stream content by (case-insensitive) name, or null when there is no such stream. */
    fun stream(name: String): ByteArray? {
        val entry = entries.firstOrNull { it.type == STREAM_TYPE && it.name.equals(name, ignoreCase = true) }
            ?: return null
        if (entry.size > data.size.toLong() * 2) throw OfficeFormatException("Stream larger than file")
        return if (entry.size < miniCutoff) miniChain(entry.start, entry.size) else chainBytes(entry.start, entry.size)
    }

    private fun readFat(): IntArray {
        val fatSectors = mutableListOf<Int>()
        for (i in 0 until HEADER_DIFAT) {
            val s = buf.getInt(DIFAT_OFFSET + i * 4)
            if (s >= 0) fatSectors += s
        }
        var difat = buf.getInt(DIFAT_START_OFFSET)
        val seen = mutableSetOf<Int>()
        while (difat >= 0 && seen.add(difat)) {
            val off = sectorOffset(difat, full = true)
            val perSector = sectorSize / 4 - 1
            for (i in 0 until perSector) {
                val s = buf.getInt(off + i * 4)
                if (s >= 0) fatSectors += s
            }
            difat = buf.getInt(off + perSector * 4)
            if (seen.size > MAX_SECTORS) throw OfficeLimitException("DIFAT too long")
        }
        val out = IntArray(fatSectors.size * sectorSize / 4)
        if (fatSectors.size > MAX_SECTORS / (sectorSize / 4)) throw OfficeLimitException("FAT too large")
        fatSectors.forEachIndexed { i, s ->
            val off = sectorOffset(s, full = true)
            for (j in 0 until sectorSize / 4) out[i * sectorSize / 4 + j] = buf.getInt(off + j * 4)
        }
        return out
    }

    private fun readDirectory(start: Int): List<Entry> {
        val bytes = chainBytes(start, Long.MAX_VALUE)
        val dir = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return (0 until bytes.size / DIR_ENTRY_SIZE).mapNotNull { i ->
            val off = i * DIR_ENTRY_SIZE
            val nameLength = dir.getShort(off + NAME_LENGTH_OFFSET).toInt().coerceIn(0, MAX_NAME_BYTES)
            val type = dir.get(off + TYPE_OFFSET).toInt()
            if (type == 0) return@mapNotNull null
            val name = String(bytes, off, (nameLength - 2).coerceAtLeast(0), Charsets.UTF_16LE)
            val size = dir.getLong(off + SIZE_OFFSET).let { if (sectorSize == SMALL_SECTOR) it and LOW_32 else it }
            Entry(name, type, dir.getInt(off + START_OFFSET), size)
        }
    }

    /** Bytes of a regular-sector chain, truncated to [size]. */
    private fun chainBytes(start: Int, size: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var sector = start
        val seen = HashSet<Int>()
        while (sector >= 0 && out.size() < size) {
            if (!seen.add(sector) || seen.size > MAX_SECTORS) throw OfficeFormatException("Sector chain loops")
            val off = sectorOffset(sector)
            out.write(data, off, minOf(sectorSize.toLong(), size - out.size()).toInt().coerceAtMost(data.size - off))
            sector = fat.getOrElse(sector) { END_OF_CHAIN }
        }
        return out.toByteArray()
    }

    private fun miniChain(start: Int, size: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var sector = start
        val seen = HashSet<Int>()
        while (sector >= 0 && out.size() < size) {
            if (!seen.add(sector)) throw OfficeFormatException("Mini chain loops")
            val off = sector * miniSectorSize
            if (off + miniSectorSize > miniStream.size) throw OfficeFormatException("Mini sector out of range")
            out.write(miniStream, off, minOf(miniSectorSize.toLong(), size - out.size()).toInt())
            sector = miniFat.getOrElse(sector) { END_OF_CHAIN }
        }
        return out.toByteArray()
    }

    /** Byte offset of [sector]; with [full] the whole sector must be inside the file (FAT and DIFAT sectors). */
    private fun sectorOffset(sector: Int, full: Boolean = false): Int {
        val off = (sector.toLong() + 1) * sectorSize
        val end = if (full) off + sectorSize else off + 1
        if (sector < 0 || end > data.size) throw OfficeFormatException("Sector out of range")
        return off.toInt()
    }

    private fun ByteArray.toIntArray(): IntArray {
        val b = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(size / 4) { b.getInt(it * 4) }
    }

    companion object {
        private const val SIGNATURE = -0x1ee54e5e1fee3030L // D0 CF 11 E0 A1 B1 1A E1, little-endian
        private const val HEADER_SIZE = 512
        private const val SECTOR_SHIFT_OFFSET = 0x1E
        private const val MINI_SHIFT_OFFSET = 0x20
        private const val DIR_START_OFFSET = 0x30
        private const val MINI_CUTOFF_OFFSET = 0x38
        private const val MINI_FAT_START_OFFSET = 0x3C
        private const val DIFAT_START_OFFSET = 0x44
        private const val DIFAT_OFFSET = 0x4C
        private const val HEADER_DIFAT = 109
        private const val MIN_SHIFT = 7
        private const val MAX_SHIFT = 16
        private const val SMALL_SECTOR = 512
        private const val DIR_ENTRY_SIZE = 128
        private const val NAME_LENGTH_OFFSET = 64
        private const val MAX_NAME_BYTES = 64
        private const val TYPE_OFFSET = 66
        private const val START_OFFSET = 116
        private const val SIZE_OFFSET = 120
        private const val STREAM_TYPE = 2
        private const val ROOT_TYPE = 5
        private const val END_OF_CHAIN = -2
        private const val MAX_SECTORS = 1 shl 20
        private const val LOW_32 = 0xFFFFFFFFL
    }
}
