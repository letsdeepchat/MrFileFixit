package com.mrfilefixit.core.domain.scan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScanFilterMathTest {
    private fun gray(level: Int): Int = (0xFF shl 24) or (level shl 16) or (level shl 8) or level

    private fun level(argb: Int): Int = ScanFilterMath.luminance(argb)

    /**
     * A 64 x 64 "page": paper brightness falls from 230 on the left to 120 on the right (a shadow), with a
     * dark ink stroke at 45% of the local paper brightness running down column 32.
     */
    private val width = 64
    private val height = 64
    private fun paperAt(x: Int): Int = 230 - (110 * x) / (width - 1)
    private val page = IntArray(width * height) { i ->
        val x = i % width
        val paper = paperAt(x)
        if (x in 31..33) (paper * 45) / 100 else paper
    }

    @Test
    fun `illumination map ignores thin ink and follows the shadow`() {
        val map = ScanFilterMath.illuminationMap(page, width, height, radius = 3)
        val middle = 32 * width
        // Under the ink stroke the map reports paper, not ink.
        assertTrue(map[middle + 32] > 150, "map under ink was ${map[middle + 32]}")
        // Left (lit) is brighter than right (shadow).
        assertTrue(map[middle + 2] > map[middle + 60] + 60)
    }

    @Test
    fun `document clean turns lit and shadowed paper white and keeps ink dark`() {
        val map = ScanFilterMath.illuminationMap(page, width, height, radius = 3)
        fun cleaned(x: Int, y: Int = 20): Int {
            val bg = ScanFilterMath.sampleMap(map, width, height, x, y, width, height)
            return level(ScanFilterMath.apply(ScanFilter.DOCUMENT_CLEAN, gray(page[y * width + x]), bg))
        }
        assertTrue(cleaned(5) >= 250, "lit paper was ${cleaned(5)}")
        assertTrue(cleaned(58) >= 250, "shadowed paper was ${cleaned(58)}")
        assertTrue(cleaned(32) <= 20, "ink was ${cleaned(32)}")
    }

    @Test
    fun `grayscale and magic colour also flatten the shadow`() {
        val map = ScanFilterMath.illuminationMap(page, width, height, radius = 3)
        listOf(ScanFilter.GRAYSCALE, ScanFilter.MAGIC_COLOR).forEach { filter ->
            val lit = ScanFilterMath.sampleMap(map, width, height, 5, 20, width, height)
            val dark = ScanFilterMath.sampleMap(map, width, height, 58, 20, width, height)
            val litOut = level(ScanFilterMath.apply(filter, gray(paperAt(5)), lit))
            val darkOut = level(ScanFilterMath.apply(filter, gray(paperAt(58)), dark))
            assertTrue(kotlin.math.abs(litOut - darkOut) <= 12, "$filter: $litOut vs $darkOut")
            assertTrue(darkOut >= 235, "$filter: shadowed paper was $darkOut")
        }
    }

    @Test
    fun `magic colour keeps colour and boosts it`() {
        val red = (0xFF shl 24) or (180 shl 16) or (60 shl 8) or 60
        val out = ScanFilterMath.apply(ScanFilter.MAGIC_COLOR, red, background = 220)
        val r = (out shr 16) and 0xFF
        val g = (out shr 8) and 0xFF
        assertTrue(r - g > 120, "red $r vs green $g")
    }

    @Test
    fun `original only makes the pixel opaque`() {
        val translucent = (0x10 shl 24) or 0x123456
        assertEquals(0xFF123456.toInt(), ScanFilterMath.apply(ScanFilter.ORIGINAL, translucent, background = 200))
    }

    @Test
    fun `luminance uses the usual weights`() {
        assertEquals(255, ScanFilterMath.luminance(0xFFFFFFFF.toInt()))
        assertEquals(0, ScanFilterMath.luminance(0xFF000000.toInt()))
        assertEquals(149, ScanFilterMath.luminance(0xFF00FF00.toInt()))
    }

    @Test
    fun `sampling a uniform map returns its value everywhere`() {
        val map = IntArray(4 * 3) { 200 }
        listOf(0 to 0, 99 to 0, 50 to 74, 99 to 74).forEach { (x, y) ->
            assertEquals(200, ScanFilterMath.sampleMap(map, 4, 3, x, y, 100, 75))
        }
    }
}
