package com.mrfilefixit.core.domain.image

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PersonMatteTest {
    private val red = argb(200, 60, 50)
    private val blue = argb(30, 90, 200)

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun r(p: Int) = (p shr 16) and 0xFF

    private fun b(p: Int) = p and 0xFF

    private fun inside(x: Int, y: Int, cx: Double, cy: Double, radius: Double) = hypot(x - cx, y - cy) < radius

    /** A red "person" disc on a blue background. */
    private fun photo(w: Int, h: Int) = IntArray(w * h) { i ->
        if (inside(i % w, i / w, w / 2.0, h / 2.0, w / 4.0)) red else blue
    }

    /** What a coarse model returns: the disc a little too big and off-centre, with a soft, blurry rim. */
    private fun coarseMask(w: Int, h: Int) = FloatArray(w * h) { i ->
        val d = hypot(i % w - (w / 2.0 + 2), i / w - (h / 2.0 - 1)) - (w / 4.0 + 2)
        (0.5 - d / 8).coerceIn(0.0, 1.0).toFloat()
    }

    @Test
    fun `the refined outline follows the real edge much better than the coarse mask`() {
        val w = 320
        val h = 320
        val photo = photo(w, h)
        val coarse = coarseMask(w, h)
        val matte = PersonMatte.from(photo, w, h, coarse, w, h, BackgroundReplace.DEFAULT_EDGE)
        var coarseError = 0.0
        var refinedError = 0.0
        for (y in 0 until h) for (x in 0 until w) {
            val truth = if (inside(x, y, w / 2.0, h / 2.0, w / 4.0)) 1.0 else 0.0
            coarseError += abs(BackgroundMath.alpha(coarse[y * w + x], BackgroundReplace.DEFAULT_EDGE) - truth)
            refinedError += abs(matte.alpha(photo[y * w + x], x, y) - truth)
        }
        assertTrue(refinedError < coarseError * 0.5, "error $coarseError -> $refinedError")
        assertTrue(matte.hasPerson)
    }

    @Test
    fun `full-size reading of a small matte keeps the edge sharp`() {
        val w = 320
        val h = 320
        val photo = photo(w, h)
        val matte = PersonMatte.from(photo, w, h, coarseMask(w, h), w * 4, h * 4, BackgroundReplace.DEFAULT_EDGE)
        // At four times the size, pixels just inside and just outside the true edge are still told apart.
        val cx = w * 2.0
        val edgeX = (cx + w).toInt()
        val bigRed = red
        assertTrue(matte.alpha(bigRed, edgeX - 16, (h * 2)) > 0.9f, "inside ${matte.alpha(bigRed, edgeX - 16, h * 2)}")
        assertTrue(matte.alpha(blue, edgeX + 16, (h * 2)) < 0.1f, "outside ${matte.alpha(blue, edgeX + 16, h * 2)}")
    }

    @Test
    fun `an edge pixel loses the old background's colour`() {
        val w = 320
        val h = 320
        val photo = photo(w, h)
        val matte = PersonMatte.from(photo, w, h, coarseMask(w, h), w, h, BackgroundReplace.DEFAULT_EDGE)
        // A see-through pixel at the rim: half person, half background.
        val mixed = argb((r(red) + r(blue)) / 2, 75, (b(red) + b(blue)) / 2)
        val x = w / 2 + w / 4 - 1
        val y = h / 2
        val clean = matte.foreground(mixed, 0.5f, x, y)
        assertTrue(r(clean) > r(mixed) + 30, "red ${r(mixed)} -> ${r(clean)}")
        assertTrue(b(clean) < b(mixed) - 30, "blue ${b(mixed)} -> ${b(clean)}")
        // Opaque pixels are left exactly as they are.
        assertEquals(red, matte.foreground(red, 1f, w / 2, h / 2))
    }

    @Test
    fun `cutting rows band by band matches pixel by pixel reads`() {
        val w = 90
        val h = 70
        val photo = photo(w, h)
        val matte = PersonMatte.from(photo, w, h, coarseMask(w, h), w, h, BackgroundReplace.DEFAULT_EDGE)
        val pixels = photo.copyOfRange(20 * w, 50 * w)
        val alpha = FloatArray(pixels.size)
        matte.cut(pixels, 0, 20, 30, alpha)
        for (row in 0 until 30) for (x in 0 until w) {
            val i = row * w + x
            val a = matte.alpha(photo[(20 + row) * w + x], x, 20 + row)
            assertEquals(a, alpha[i], 1e-6f)
            assertEquals(matte.foreground(photo[(20 + row) * w + x], a, x, 20 + row), pixels[i])
        }
    }

    @Test
    fun `an empty mask finds no person`() {
        val w = 60
        val h = 40
        val matte = PersonMatte.from(photo(w, h), w, h, FloatArray(w * h), w, h, 0.4f)
        assertFalse(matte.hasPerson)
    }

    @Test
    fun `running-sum box mean matches a direct window mean`() {
        val w = 37
        val h = 23
        val random = Random(5)
        val src = FloatArray(w * h) { random.nextFloat() }
        val radius = 4
        val fast = BoxSum.mean(src, w, h, radius)
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0.0
            var count = 0
            for (yy in maxOf(0, y - radius)..minOf(h - 1, y + radius)) {
                for (xx in maxOf(0, x - radius)..minOf(w - 1, x + radius)) {
                    sum += src[yy * w + xx]
                    count++
                }
            }
            assertEquals(sum / count, fast[y * w + x].toDouble(), 1e-4)
        }
    }
}
