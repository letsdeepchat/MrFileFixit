package com.mrfilefixit.core.domain.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PdfCompressionPolicyTest {
    private val a4Width = 595f
    private val a4Height = 842f

    @Test
    fun `levels get smaller and lossier from light to strong`() {
        assertEquals(listOf(150, 120, 96), CompressionLevel.entries.map { it.dpi })
        assertEquals(listOf(75, 60, 45), CompressionLevel.entries.map { it.jpegQuality })
    }

    @Test
    fun `a 300 dpi A4 scan is downsampled to the level's dpi`() {
        val (w, h) = PdfCompressionPolicy.targetSize(2480, 3508, a4Width, a4Height, dpi = 120)
        // A4 at 120 dpi is about 992 x 1403 px.
        assertTrue(w in 990..994, "width was $w")
        assertTrue(h in 1400..1405, "height was $h")
    }

    @Test
    fun `small images are never upscaled`() {
        assertEquals(400 to 300, PdfCompressionPolicy.targetSize(400, 300, a4Width, a4Height, dpi = 96))
    }

    @Test
    fun `a landscape photo on a portrait page keeps its orientation and ratio`() {
        val (w, h) = PdfCompressionPolicy.targetSize(4000, 3000, a4Width, a4Height, dpi = 96)
        assertTrue(w > h)
        assertEquals(4f / 3f, w.toFloat() / h, 0.01f)
        assertTrue(w <= 1123 && h <= 794, "was $w x $h")
    }

    @Test
    fun `degenerate sizes pass through and results are at least one pixel`() {
        assertEquals(0 to 0, PdfCompressionPolicy.targetSize(0, 0, a4Width, a4Height, dpi = 96))
        assertEquals(1 to 1, PdfCompressionPolicy.targetSize(10_000, 10_000, 1f, 1f, dpi = 1))
    }

    @Test
    fun `an image is replaced only by something strictly smaller`() {
        assertTrue(PdfCompressionPolicy.shouldReplace(originalBytes = 1000, replacementBytes = 999))
        assertFalse(PdfCompressionPolicy.shouldReplace(originalBytes = 1000, replacementBytes = 1000))
        assertFalse(PdfCompressionPolicy.shouldReplace(originalBytes = 1000, replacementBytes = 1500))
    }

    @Test
    fun `the smallest candidate below the source wins`() {
        val pick = PdfCompressionPolicy.smallestBelow(558_000, listOf("structural" to 400_000L, "raster" to 120_000L))
        assertEquals("raster", pick)
    }

    @Test
    fun `nothing is returned when every candidate is as big or bigger, so the file never grows`() {
        // The bug this guards: 558 KB in, 1,000 KB out.
        assertNull(PdfCompressionPolicy.smallestBelow(558_000, listOf("raster" to 1_000_000L)))
        assertNull(PdfCompressionPolicy.smallestBelow(558_000, listOf("structural" to 558_000L)))
        assertNull(PdfCompressionPolicy.smallestBelow(558_000, emptyList<Pair<String, Long>>()))
        // An empty (failed) write is never a valid "smaller" result.
        assertNull(PdfCompressionPolicy.smallestBelow(558_000, listOf("broken" to 0L)))
    }

    @Test
    fun `only the strong level may rasterize pages`() {
        assertEquals(
            listOf(false, false, true),
            CompressionLevel.entries.map { PdfCompressionPolicy.mayRasterize(it) },
        )
    }
}
