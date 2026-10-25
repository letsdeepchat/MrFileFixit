package com.mrfilefixit.core.office.legacy

import com.mrfilefixit.core.domain.document.DocBlock
import com.mrfilefixit.core.domain.document.DocPageBreak
import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.DocTable
import com.mrfilefixit.core.domain.document.DocTableCell
import com.mrfilefixit.core.domain.document.DocTableRow
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.office.OfficeEncryptedException
import com.mrfilefixit.core.office.OfficeFormatException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * Text of a legacy Word 97-2003 (.doc) file, read through its piece table, with paragraphs, tables, line and
 * page breaks, and field results. Character and paragraph formatting is not read: the result is plain text.
 */
class DocBinaryReader {

    fun read(data: ByteArray): RichDocument {
        val cfb = CompoundFile(data)
        val word = cfb.stream("WordDocument") ?: throw OfficeFormatException("No WordDocument stream")
        val fib = fileInformationBlock(word)
        val flags = fib.getShort(FLAGS_OFFSET).toInt()
        if (flags and ENCRYPTED_FLAG != 0) throw OfficeEncryptedException()
        val tableName = if (flags and TABLE_FLAG != 0) "1Table" else "0Table"
        val ccpText = fib.getInt(CCP_TEXT_OFFSET).coerceAtLeast(0)
        val table = cfb.stream(tableName)
        val text = table?.let { pieceTableText(word, it, fib, ccpText) } ?: legacyText(word, fib)
        return RichDocument(toBlocks(text))
    }

    private fun fileInformationBlock(word: ByteArray): ByteBuffer {
        val fib = ByteBuffer.wrap(word).order(ByteOrder.LITTLE_ENDIAN)
        val valid = word.size >= FIB_MIN_SIZE && fib.getShort(0).toUShort().toInt() == WORD_IDENT
        if (!valid) throw OfficeFormatException("Not a Word document")
        return fib
    }

    private fun pieceTableText(word: ByteArray, table: ByteArray, fib: ByteBuffer, ccpText: Int): String? {
        val fcClx = fib.getInt(FC_CLX_OFFSET)
        val lcbClx = fib.getInt(LCB_CLX_OFFSET)
        if (fcClx < 0 || lcbClx <= 0 || fcClx.toLong() + lcbClx > table.size) return null
        val clx = ByteBuffer.wrap(table, fcClx, lcbClx).order(ByteOrder.LITTLE_ENDIAN)
        while (clx.hasRemaining()) {
            when (clx.get().toInt()) {
                PRC -> {
                    val skip = clx.short.toUShort().toInt()
                    if (skip > clx.remaining()) return null
                    clx.position(clx.position() + skip)
                }
                PCDT -> return readPieces(word, clx, clx.int, ccpText)
                else -> return null
            }
        }
        return null
    }

    private fun readPieces(word: ByteArray, clx: ByteBuffer, lcb: Int, ccpText: Int): String? {
        if (lcb < CP_SIZE || lcb > clx.remaining()) return null
        val count = (lcb - CP_SIZE) / (CP_SIZE + PCD_SIZE)
        val base = clx.position()
        val cps = IntArray(count + 1) { clx.getInt(base + it * CP_SIZE) }
        val pcdBase = base + (count + 1) * CP_SIZE
        val sb = StringBuilder()
        for (i in 0 until count) {
            if (sb.length >= ccpText) break
            val chars = (cps[i + 1] - cps[i]).coerceIn(0, MAX_PIECE)
            val fcRaw = clx.getInt(pcdBase + i * PCD_SIZE + 2)
            val compressed = fcRaw and COMPRESSED_BIT != 0
            val fc = fcRaw and FC_MASK
            if (compressed) {
                val start = fc / 2
                if (start + chars > word.size) return null
                sb.append(String(word, start, chars, CP1252))
            } else {
                if (fc.toLong() + chars * 2L > word.size) return null
                sb.append(String(word, fc, chars * 2, Charsets.UTF_16LE))
            }
        }
        return sb.take(ccpText).toString()
    }

    /** Word 6/95 files store text as one 8-bit run between fcMin and fcMac. */
    private fun legacyText(word: ByteArray, fib: ByteBuffer): String {
        val fcMin = fib.getInt(FC_MIN_OFFSET)
        val fcMac = fib.getInt(FC_MAC_OFFSET)
        if (fcMin < 0 || fcMac <= fcMin || fcMac > word.size) throw OfficeFormatException("No readable text")
        return String(word, fcMin, fcMac - fcMin, CP1252)
    }

    companion object {
        private val CP1252: Charset = Charset.forName("windows-1252")
        private const val WORD_IDENT = 0xA5EC
        private const val FIB_MIN_SIZE = 0x1AA
        private const val FLAGS_OFFSET = 0x0A
        private const val ENCRYPTED_FLAG = 0x0100
        private const val TABLE_FLAG = 0x0200
        private const val FC_MIN_OFFSET = 0x18
        private const val FC_MAC_OFFSET = 0x1C
        private const val CCP_TEXT_OFFSET = 0x4C
        private const val FC_CLX_OFFSET = 0x1A2
        private const val LCB_CLX_OFFSET = 0x1A6
        private const val PRC = 1
        private const val PCDT = 2
        private const val CP_SIZE = 4
        private const val PCD_SIZE = 8
        private const val COMPRESSED_BIT = 0x40000000
        private const val FC_MASK = 0x3FFFFFFF
        private const val MAX_PIECE = 50_000_000

        private const val CELL_MARK = '\u0007'
        private const val FIELD_BEGIN = '\u0013'
        private const val FIELD_SEPARATOR = '\u0014'
        private const val FIELD_END = '\u0015'

        /**
         * Turns Word's special characters into blocks: \r ends a paragraph, \u0007 ends a table cell (a
         * paragraph that is only \u0007 ends a row), \u000C is a page break, fields keep only their result.
         */
        fun toBlocks(raw: String): List<DocBlock> {
            val text = stripFields(raw)
            val blocks = mutableListOf<DocBlock>()
            val rows = mutableListOf<DocTableRow>()
            val cells = mutableListOf<DocTableCell>()
            val current = StringBuilder()
            fun flushTable() {
                if (rows.isNotEmpty()) blocks += DocTable(rows.toList())
                rows.clear()
            }
            for (c in text) {
                when (c) {
                    // Inside a table row, \r separates paragraphs of the same cell.
                    '\r' -> if (cells.isNotEmpty()) {
                        current.append('\n')
                    } else {
                        flushTable()
                        blocks += DocParagraph.of(current.toString())
                        current.clear()
                    }
                    CELL_MARK -> {
                        if (current.isEmpty() && cells.isNotEmpty()) {
                            rows += DocTableRow(cells.toList())
                            cells.clear()
                        } else {
                            cells += DocTableCell(listOf(DocParagraph.of(current.toString())))
                            current.clear()
                        }
                    }
                    '\u000C' -> {
                        flushTable()
                        if (current.isNotEmpty()) blocks += DocParagraph.of(current.toString())
                        current.clear()
                        blocks += DocPageBreak
                    }
                    '\u000B', '\u000E' -> current.append('\n')
                    '\u001E' -> current.append('-')
                    '\u0001', '\u0002', '\u0005', '\u0008', '\u001F' -> Unit
                    else -> if (c >= ' ' || c == '\t') current.append(c)
                }
            }
            flushTable()
            if (current.isNotBlank()) blocks += DocParagraph.of(current.toString())
            return blocks
        }

        /** Keeps field results (between separator and end) and drops field codes, including nested fields. */
        private fun stripFields(text: String): String {
            if (FIELD_BEGIN !in text) return text
            val sb = StringBuilder(text.length)
            // One entry per open field: true while inside its code (before the separator).
            val stack = ArrayDeque<Boolean>()
            for (c in text) {
                when (c) {
                    FIELD_BEGIN -> stack.addLast(true)
                    FIELD_SEPARATOR -> if (stack.isNotEmpty()) stack[stack.size - 1] = false
                    FIELD_END -> stack.removeLastOrNull()
                    else -> if (stack.none { it }) sb.append(c)
                }
            }
            return sb.toString()
        }
    }
}
