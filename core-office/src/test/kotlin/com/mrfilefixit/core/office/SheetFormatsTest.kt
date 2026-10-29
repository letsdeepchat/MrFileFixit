package com.mrfilefixit.core.office

import com.mrfilefixit.core.domain.document.Sheet
import com.mrfilefixit.core.domain.document.SheetCell
import com.mrfilefixit.core.domain.document.Workbook
import com.mrfilefixit.core.office.legacy.XlsBinaryReader
import com.mrfilefixit.core.office.xlsx.Csv
import com.mrfilefixit.core.office.xlsx.XlsxReader
import com.mrfilefixit.core.office.xlsx.XlsxWriter
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SheetFormatsTest {
    @TempDir
    lateinit var dir: File

    private val workbook = Workbook(
        listOf(
            Sheet(
                "Fees",
                listOf(
                    listOf(SheetCell.Text("Item", bold = true), SheetCell.Text("Amount", bold = true)),
                    listOf(SheetCell.Text("Tuition, term 1"), SheetCell.Number(12_500.0)),
                    listOf(SheetCell.Empty, SheetCell.Bool(true)),
                    listOf(SheetCell.Text("पुस्तकें \"new\""), SheetCell.Number(0.25)),
                ),
            ),
            Sheet("Fees", emptyList()),
        ),
    )

    @Test
    fun `xlsx round trip keeps values, bold and unique sheet names`() {
        val file = File(dir, "out.xlsx")
        file.outputStream().use { XlsxWriter().write(workbook, it) }
        val read = XlsxReader().read(file)
        assertEquals(listOf("Fees", "Fees (2)"), read.sheets.map { it.name })
        val rows = read.sheets[0].rows
        assertEquals(SheetCell.Text("Item", bold = true), rows[0][0])
        assertEquals(12_500.0, (rows[1][1] as SheetCell.Number).value)
        assertEquals(SheetCell.Empty, rows[2][0])
        assertEquals(SheetCell.Bool(true), rows[2][1])
        assertEquals("पुस्तकें \"new\"", rows[3][0].display)
    }

    @Test
    fun `csv quotes where needed and reads back`() {
        val bytes = Csv.write(workbook.sheets[0].rows)
        val text = bytes.toString(Charsets.UTF_8)
        assertTrue(text.startsWith("﻿Item,Amount\r\n"))
        assertTrue(text.contains("\"Tuition, term 1\",12500"))
        assertTrue(text.contains("\"पुस्तकें \"\"new\"\"\""))
        val rows = Csv.read(text, "Fees").rows
        assertEquals(listOf("Tuition, term 1", "12500"), rows[1].map { it.display })
        assertEquals("पुस्तकें \"new\"", rows[3][0].display)
    }

    @Test
    fun `csv detects semicolon delimiters`() {
        val rows = Csv.read("a;b;c\n1;2;3\n", "S").rows
        assertEquals(listOf("1", "2", "3"), rows[1].map { it.display })
    }

    @Test
    fun `reads a BIFF8 xls workbook`() {
        val bytes = javaClass.getResourceAsStream("/fixtures/marks.xls")!!.readBytes()
        val book = XlsBinaryReader().read(bytes)
        assertEquals(listOf("Marks", "Empty"), book.sheets.map { it.name })
        val rows = book.sheets[0].rows
        assertEquals(listOf("Name", "Score", "Passed"), rows[0].map { it.display })
        assertEquals(91.5, (rows[1][1] as SheetCell.Number).value)
        assertEquals(SheetCell.Bool(true), rows[1][2])
        assertEquals("रवि", rows[2][0].display)
        assertEquals(78.0, (rows[2][1] as SheetCell.Number).value)
        assertTrue(book.sheets[1].rows.isEmpty())
    }

    @Test
    fun `a cell record cut short is skipped, not a crash`() {
        // marks.xls with a few bytes flipped by a fuzzer: one LABELSST record is shorter than its header.
        val bytes = javaClass.getResourceAsStream("/fixtures/damaged-labelsst.xls")!!.readBytes()
        try {
            XlsBinaryReader().read(bytes)
        } catch (_: java.io.IOException) {
            // A format error is fine too; an IndexOutOfBoundsException is not.
        }
    }

    @Test
    fun `sniffs containers`() {
        val bytes = javaClass.getResourceAsStream("/fixtures/marks.xls")!!.readBytes()
        assertEquals(OfficeContainer.COMPOUND, OfficeContainer.sniff(bytes))
        assertEquals(OfficeContainer.PDF, OfficeContainer.sniff("%PDF-1.7".toByteArray()))
    }
}
