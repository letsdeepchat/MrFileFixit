package com.mrfilefixit.core.domain.image.enhance

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalContrastTest {
    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun luma(p: Int) = NaturalEnhancer.luma((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)

    /** A hazy picture: soft blobs of slightly different grey on a mid-grey ground, all low contrast. */
    private fun hazy(w: Int, h: Int) = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        val v = (128 + 18 * kotlin.math.sin(x / 9.0) * kotlin.math.cos(y / 11.0)).toInt()
        argb(v, v + 4, v + 8)
    }

    /** Every [step]th pixel of each [step]th row: a cheap stand-in for the scaled-down copy. */
    private fun shrink(pixels: IntArray, w: Int, h: Int, step: Int): Triple<IntArray, Int, Int> {
        val sw = w / step
        val sh = h / step
        return Triple(IntArray(sw * sh) { pixels[(it / sw) * step * w + (it % sw) * step] }, sw, sh)
    }

    private fun localDetail(pixels: IntArray, w: Int, h: Int): Double {
        var sum = 0.0
        var count = 0
        for (y in 4 until h - 4) for (x in 4 until w - 4) {
            sum += abs(luma(pixels[y * w + x]) - luma(pixels[y * w + x - 4]))
            count++
        }
        return sum / count
    }

    private fun clarity(pixels: IntArray, w: Int, h: Int, strength: Float): LocalContrast {
        val (small, sw, sh) = shrink(pixels, w, h, 2)
        return LocalContrast.from(small, sw, sh, w, h, strength)
    }

    @Test
    fun `zero strength changes nothing`() {
        val image = hazy(96, 80)
        val curve = clarity(image, 96, 80, 0f)
        assertTrue(curve.isIdentity)
        val out = image.copyOf().also { curve.apply(it, 0, 0, 80) }
        assertArrayEquals(image, out)
    }

    @Test
    fun `a hazy picture gets clearly more local contrast and keeps its brightness`() {
        val w = 160
        val h = 120
        val image = hazy(w, h)
        val out = image.copyOf().also { clarity(image, w, h, 0.6f).apply(it, 0, 0, h) }
        val before = localDetail(image, w, h)
        val after = localDetail(out, w, h)
        assertTrue(after > before * 1.2, "local contrast $before -> $after")
        assertEquals(image.map(::luma).average(), out.map(::luma).average(), 2.0)
    }

    @Test
    fun `rows done band by band match the whole picture`() {
        val w = 90
        val h = 70
        val image = hazy(w, h)
        val curve = clarity(image, w, h, 1f)
        val whole = image.copyOf().also { curve.apply(it, 0, 0, h) }
        val banded = image.copyOf()
        var top = 0
        while (top < h) {
            val rows = minOf(16, h - top)
            val band = banded.copyOfRange(top * w, (top + rows) * w)
            curve.apply(band, 0, top, rows)
            band.copyInto(banded, top * w)
            top += rows
        }
        assertArrayEquals(whole, banded)
    }

    @Test
    fun `hue is kept and alpha is untouched`() {
        val w = 64
        val h = 64
        val image = hazy(w, h).map { (it and 0x00FFFFFF) or (0x80 shl 24) }.toIntArray()
        val out = image.copyOf().also { clarity(image, w, h, 1f).apply(it, 0, 0, h) }
        image.indices.forEach { i ->
            assertEquals(0x80, out[i] ushr 24)
            // Only luminance moves: the differences between channels stay as they were.
            val dr = ((out[i] shr 16) and 0xFF) - ((image[i] shr 16) and 0xFF)
            val db = (out[i] and 0xFF) - (image[i] and 0xFF)
            assertEquals(dr, db)
        }
    }

    @Test
    fun `skin gets much less clarity than the rest`() {
        val w = 120
        val h = 60
        val skinTone = argb(176, 120, 90)
        val grey = argb(120, 120, 120)
        // Left half skin, right half grey, each with the same soft ripple.
        val image = IntArray(w * h) { i ->
            val x = i % w
            val ripple = (10 * kotlin.math.sin(x / 5.0)).toInt()
            val base = if (x < w / 2) skinTone else grey
            shift(base, ripple)
        }
        val out = image.copyOf().also { clarity(image, w, h, 1f).apply(it, 0, 0, h) }
        fun change(columns: IntRange) =
            (0 until w * h).filter { it % w in columns }.map { abs(luma(out[it]) - luma(image[it])) }.average()
        val skinChange = change(4 until w / 2 - 4)
        val greyChange = change(w / 2 + 4 until w - 4)
        assertTrue(skinChange < greyChange * 0.7, "skin $skinChange grey $greyChange")
        assertTrue(sqrt(greyChange) > 0.5)
    }

    private fun shift(p: Int, d: Int): Int {
        fun c(v: Int) = (v + d).coerceIn(0, 255)
        return argb(c((p shr 16) and 0xFF), c((p shr 8) and 0xFF), c(p and 0xFF))
    }
}
