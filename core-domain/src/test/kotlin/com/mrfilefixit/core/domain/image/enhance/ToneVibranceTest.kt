package com.mrfilefixit.core.domain.image.enhance

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToneVibranceTest {
    private fun argb(r: Int, g: Int, b: Int, a: Int = 0xFF) = (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun r(p: Int) = (p shr 16) and 0xFF

    private fun g(p: Int) = (p shr 8) and 0xFF

    private fun b(p: Int) = p and 0xFF

    private fun luma(p: Int) = NaturalEnhancer.luma(r(p), g(p), b(p))

    private fun std(pixels: IntArray): Double {
        val mean = pixels.map { luma(it).toDouble() }.average()
        return sqrt(pixels.map { (luma(it) - mean) * (luma(it) - mean) }.average())
    }

    private fun saturation(p: Int): Int = maxOf(r(p), g(p), b(p)) - minOf(r(p), g(p), b(p))

    /** A flat, dull, low-contrast landscape: greyish greens and blues in a narrow brightness band. */
    private fun dullPhoto(): IntArray {
        val random = Random(3)
        return IntArray(4000) { i ->
            val base = 100 + (i % 50) + random.nextInt(-4, 5)
            if (i % 2 == 0) argb(base - 10, base + 8, base - 6) else argb(base - 8, base, base + 12)
        }
    }

    @Test
    fun `zero strength changes nothing`() {
        val photo = dullPhoto()
        val curve = ToneVibrance.from(photo, 0f)
        assertTrue(curve.isIdentity)
        val copy = photo.copyOf().also { curve.apply(it) }
        assertTrue(photo.contentEquals(copy))
    }

    @Test
    fun `a flat dull photo gets visibly more contrast and colour`() {
        val photo = dullPhoto()
        val out = photo.copyOf().also { ToneVibrance.from(photo, 1f).apply(it) }
        assertTrue(std(out) > std(photo) * 1.3, "contrast ${std(photo)} -> ${std(out)}")
        assertTrue(out.map(::saturation).average() > photo.map(::saturation).average() * 1.15)
    }

    @Test
    fun `hue is kept on every pixel`() {
        val photo = dullPhoto()
        val out = photo.copyOf().also { ToneVibrance.from(photo, 1f).apply(it) }
        photo.indices.forEach { i ->
            val before = hue(photo[i])
            val after = hue(out[i])
            val diff = abs(before - after).let { if (it > Math.PI) 2 * Math.PI - it else it }
            assertTrue(diff < 0.12, "pixel $i hue $before -> $after")
        }
    }

    @Test
    fun `contrast pivots on the mean, so overall brightness of a normal photo stays`() {
        val photo = dullPhoto()
        val out = photo.copyOf().also { ToneVibrance.from(photo, 1f).apply(it) }
        assertEquals(photo.map(::luma).average(), out.map(::luma).average(), 3.0)
    }

    @Test
    fun `a very dark photo is lifted a little, never blown out`() {
        val random = Random(9)
        val dark = IntArray(2000) {
            val v = 20 + random.nextInt(0, 60)
            argb(v, v, v)
        }
        val out = dark.copyOf().also { ToneVibrance.from(dark, 1f).apply(it) }
        val before = dark.map(::luma).average()
        val after = out.map(::luma).average()
        assertTrue(after > before + 5 && after < before + ToneVibrance.MAX_SHIFT + 2, "$before -> $after")
    }

    @Test
    fun `skin keeps its tone`() {
        val skin = argb(141, 85, 36) // brown skin
        val photo = dullPhoto() + IntArray(500) { skin }
        val out = ToneVibrance.from(photo, 1f).applyPixel(skin)
        assertTrue(abs(luma(out) - luma(skin)) < 12, "luma ${luma(skin)} -> ${luma(out)}")
        assertTrue(saturation(out) <= saturation(skin) * 1.1 + 1)
    }

    @Test
    fun `alpha is kept and greys stay grey`() {
        val curve = ToneVibrance.from(dullPhoto(), 1f)
        val out = curve.applyPixel(argb(120, 120, 120, a = 0x40))
        assertEquals(0x40, out ushr 24)
        assertEquals(r(out), g(out))
        assertEquals(g(out), b(out))
    }

    private fun hue(p: Int): Double {
        val y = luma(p)
        return atan2((b(p) - y).toDouble(), (r(p) - y).toDouble())
    }
}
