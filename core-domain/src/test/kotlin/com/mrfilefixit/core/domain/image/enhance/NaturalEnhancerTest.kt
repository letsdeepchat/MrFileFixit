package com.mrfilefixit.core.domain.image.enhance

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class NaturalEnhancerTest {

    @Test
    fun `zero strength leaves every pixel as it was`() {
        val image = portrait(64, 64)
        assertArrayEquals(image, NaturalEnhancer.enhance(image, 64, 64, 0f))
    }

    @Test
    fun `enhance really changes a soft photo and makes it sharper`() {
        val w = 96
        val h = 96
        val soft = blur(texture(w, h), w, h)
        val out = NaturalEnhancer.enhance(soft, w, h, 1f)
        val changed = soft.indices.count { soft[it] != out[it] }
        assertTrue(changed > soft.size / 4, "only $changed of ${soft.size} pixels changed")
        assertTrue(
            laplacianEnergy(out, w, h) > laplacianEnergy(soft, w, h) * 1.15,
            "sharpness did not increase",
        )
    }

    @ParameterizedTest
    @ValueSource(floats = [0.3f, 0.6f, 1f])
    fun `colour and skin tone are preserved at any strength`(strength: Float) {
        val w = 80
        val h = 80
        val image = portrait(w, h)
        val out = NaturalEnhancer.enhance(image, w, h, strength)
        // Chroma (Cb, Cr) means: the colour of the picture, including every skin tone, does not drift.
        assertEquals(meanOf(image) { cb(it) }, meanOf(out) { cb(it) }, 0.35)
        assertEquals(meanOf(image) { cr(it) }, meanOf(out) { cr(it) }, 0.35)
        // Brightness is not lifted overall: no whitening or bleaching.
        assertEquals(meanOf(image) { luma(it) }, meanOf(out) { luma(it) }, 1.5)
        // Per pixel, only luminance moves (up to rounding), unless a channel had to be clamped.
        image.indices.filter { unclamped(out[it]) }.forEach { i ->
            assertEquals(cb(image[i]), cb(out[i]), 1.01)
            assertEquals(cr(image[i]), cr(out[i]), 1.01)
        }
    }

    @Test
    fun `dark skin is not lightened`() {
        val w = 48
        val h = 48
        val darkSkin = noisy(IntArray(w * h) { argb(92, 60, 44) }, seed = 7, amount = 6)
        val out = NaturalEnhancer.enhance(darkSkin, w, h, 1f)
        assertEquals(meanOf(darkSkin) { luma(it) }, meanOf(out) { luma(it) }, 1.5)
    }

    @Test
    fun `sensor noise on skin is reduced but texture is not wiped out`() {
        val w = 64
        val h = 64
        val flat = IntArray(w * h) { argb(196, 148, 112) }
        val noisy = noisy(flat, seed = 3, amount = 14)
        val out = NaturalEnhancer.enhance(noisy, w, h, 0.8f)
        val before = stdDev(noisy) { luma(it) }
        val after = stdDev(out) { luma(it) }
        assertTrue(after < before * 0.9, "noise $before -> $after")
        assertTrue(after > before * 0.2, "texture flattened: $before -> $after")
    }

    @Test
    fun `a small dark spot on skin is softened`() {
        val w = 40
        val h = 40
        val skin = argb(170, 120, 90)
        val image = IntArray(w * h) { skin }
        val spot = listOf(20 * w + 20, 20 * w + 21, 21 * w + 20, 21 * w + 21)
        spot.forEach { image[it] = argb(120, 80, 58) }
        val out = NaturalEnhancer.enhance(image, w, h, 1f)
        val depthBefore = luma(skin) - luma(image[spot[0]])
        val depthAfter = luma(skin) - luma(out[spot[0]])
        assertTrue(depthAfter < depthBefore * 0.85, "spot depth $depthBefore -> $depthAfter")
        // Skin far from the spot is untouched.
        assertEquals(skin, out[5 * w + 5])
    }

    @Test
    fun `dark text on paper is never treated as a spot`() {
        val w = 60
        val h = 30
        val image =
            IntArray(
                w * h,
            ) { i -> if ((i % w) in 20..22 && (i / w) in 8..20) argb(25, 25, 25) else argb(245, 245, 245) }
        val out = NaturalEnhancer.enhance(image, w, h, 1f)
        val strokeBefore = luma(image[14 * w + 21])
        val strokeAfter = luma(out[14 * w + 21])
        assertTrue(strokeAfter <= strokeBefore + 1, "text stroke lifted from $strokeBefore to $strokeAfter")
    }

    @Test
    fun `alpha is kept`() {
        val image = IntArray(16 * 16) { argb(150, 110, 90, a = 0x7F) }
        val out = NaturalEnhancer.enhance(noisy(image, 1, 10), 16, 16, 1f)
        assertTrue(out.all { it ushr 24 == 0x7F })
    }

    @Test
    fun `banded processing gives exactly the whole-image result`() = runTest {
        val w = 37
        val h = 101
        val image = blur(texture(w, h), w, h)
        val whole = NaturalEnhancer.enhance(image, w, h, 0.7f)
        listOf(1, 7, 16, 50, 200).forEach { band ->
            val canvas = image.copyOf()
            NaturalEnhancer.enhanceInBands(
                w,
                h,
                0.7f,
                band,
                read = { top, count, into, offset -> canvas.copyInto(into, offset, top * w, (top + count) * w) },
                write = { top, count, from, offset -> from.copyInto(canvas, top * w, offset, offset + count * w) },
            )
            assertArrayEquals(whole, canvas, "band size $band")
        }
    }

    @Test
    fun `skin detection covers light to dark skin and skips greys and sky`() {
        listOf(argb(232, 192, 172), argb(200, 150, 110), argb(140, 95, 70), argb(90, 60, 45)).forEach {
            assertTrue(NaturalEnhancer.skinWeight(red(it), green(it), blue(it)) > 0.5f)
        }
        listOf(argb(128, 128, 128), argb(100, 150, 230), argb(20, 20, 20), argb(60, 160, 60)).forEach {
            assertEquals(0f, NaturalEnhancer.skinWeight(red(it), green(it), blue(it)))
        }
    }

    // region helpers

    private fun luma(p: Int) = NaturalEnhancer.luma(red(p), green(p), blue(p)).toDouble()

    private fun cb(p: Int) = NaturalEnhancer.chromaBlue(red(p), green(p), blue(p)).toDouble()

    private fun cr(p: Int) = NaturalEnhancer.chromaRed(red(p), green(p), blue(p)).toDouble()

    private fun unclamped(p: Int) = listOf(red(p), green(p), blue(p)).all { it in 1..254 }

    private fun meanOf(pixels: IntArray, f: (Int) -> Double) = pixels.sumOf(f) / pixels.size

    private fun stdDev(pixels: IntArray, f: (Int) -> Double): Double {
        val mean = meanOf(pixels, f)
        return sqrt(pixels.sumOf { (f(it) - mean) * (f(it) - mean) } / pixels.size)
    }

    private fun laplacianEnergy(pixels: IntArray, w: Int, h: Int): Double {
        var sum = 0.0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val c = luma(pixels[y * w + x])
            val l = 4 * c - luma(pixels[y * w + x - 1]) - luma(pixels[y * w + x + 1]) -
                luma(pixels[(y - 1) * w + x]) - luma(pixels[(y + 1) * w + x])
            sum += abs(l)
        }
        return sum
    }

    /** Stripes and a gradient: plenty of edges at different scales. */
    private fun texture(w: Int, h: Int) = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        val v = if ((x / 4 + y / 6) % 2 == 0) 70 else 180
        argb((v + x).coerceAtMost(255), (v * 0.8).toInt(), (v * 0.6).toInt())
    }

    /** A face-like picture: skin tones of several depths on a coloured background, with noise. */
    private fun portrait(w: Int, h: Int): IntArray {
        val tones = listOf(argb(232, 192, 172), argb(198, 146, 108), argb(140, 95, 70), argb(88, 58, 42))
        val image = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            when {
                y < h / 5 -> argb(40, 90, 160)
                x < w / 4 -> tones[0]
                x < w / 2 -> tones[1]
                x < 3 * w / 4 -> tones[2]
                else -> tones[3]
            }
        }
        return noisy(image, seed = 11, amount = 8)
    }

    private fun noisy(pixels: IntArray, seed: Int, amount: Int): IntArray {
        val random = Random(seed)
        return IntArray(pixels.size) { i ->
            val p = pixels[i]
            val d = random.nextInt(-amount, amount + 1)
            argb(
                (red(p) + d).coerceIn(0, 255),
                (green(p) + d).coerceIn(0, 255),
                (blue(p) + d).coerceIn(0, 255),
                a = p ushr 24,
            )
        }
    }

    /** A 3x3 box blur, to simulate a soft photo. */
    private fun blur(pixels: IntArray, w: Int, h: Int) = IntArray(pixels.size) { i ->
        val x = i % w
        val y = i / w
        var r = 0
        var g = 0
        var b = 0
        for (dy in -1..1) for (dx in -1..1) {
            val p = pixels[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
            r += red(p)
            g += green(p)
            b += blue(p)
        }
        argb(r / 9, g / 9, b / 9)
    }

    // endregion
}
