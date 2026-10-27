package com.mrfilefixit.core.domain.image.enhance

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LanczosResamplerTest {
    @Test
    fun `same size is an exact copy`() {
        val pixels = IntArray(7 * 5) { argb(it * 7 % 256, it * 13 % 256, it * 29 % 256) }
        assertArrayEquals(pixels, LanczosResampler(7, 5, 7, 5).resize(pixels))
    }

    @Test
    fun `flat colour stays flat when scaling up and down`() {
        val colour = argb(110, 70, 50)
        val pixels = IntArray(10 * 6) { colour }
        listOf(20 to 12, 40 to 24, 5 to 3, 13 to 7).forEach { (w, h) ->
            val out = LanczosResampler(10, 6, w, h).resize(pixels)
            assertEquals(w * h, out.size)
            assertTrue(out.all { it == colour }, "flat colour changed at $w x $h")
        }
    }

    @Test
    fun `upscaled edge stays inside the colour range and keeps its position`() {
        val w = 16
        val pixels = IntArray(w * 4) { i -> if (i % w < w / 2) argb(20, 20, 20) else argb(230, 230, 230) }
        val out = LanczosResampler(w, 4, w * 2, 8).resize(pixels)
        val row = (0 until w * 2).map { red(out[2 * w * 2 + it]) }
        assertTrue(row.all { it in 0..255 })
        assertTrue(row.first() < 40 && row.last() > 210)
        // The edge sits in the middle: left half dark, right half light.
        assertTrue(row[w - 3] < 128 && row[w + 2] > 128)
    }

    @Test
    fun `a band of rows matches the same rows of the full resize`() {
        val pixels = IntArray(9 * 9) { argb(it * 31 % 256, it * 17 % 256, it * 3 % 256) }
        val resampler = LanczosResampler(9, 9, 27, 27)
        val horizontal = resampler.horizontal(pixels)
        val full = resampler.vertical(horizontal, 0, 27)
        val band = resampler.vertical(horizontal, 10, 19)
        assertArrayEquals(full.copyOfRange(10 * 27, 19 * 27), band)
    }

    @Test
    fun `alpha is resampled like the other channels`() {
        val pixels = IntArray(4 * 4) { 0x80FFFFFF.toInt() }
        val out = LanczosResampler(4, 4, 8, 8).resize(pixels)
        assertTrue(out.all { it ushr 24 == 0x80 })
    }

    @Test
    fun `invalid sizes are rejected`() {
        assertThrows<IllegalArgumentException> { LanczosResampler(0, 4, 8, 8) }
        assertThrows<IllegalArgumentException> { LanczosResampler(4, 4, 8, 8).horizontal(IntArray(3)) }
    }
}

internal fun argb(r: Int, g: Int, b: Int, a: Int = 255): Int = (a shl 24) or (r shl 16) or (g shl 8) or b

internal fun red(p: Int) = (p shr 16) and 0xFF

internal fun green(p: Int) = (p shr 8) and 0xFF

internal fun blue(p: Int) = p and 0xFF
