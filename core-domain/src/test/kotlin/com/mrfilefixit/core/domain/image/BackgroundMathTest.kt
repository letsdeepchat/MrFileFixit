package com.mrfilefixit.core.domain.image

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BackgroundMathTest {
    private fun argb(r: Int, g: Int, b: Int, a: Int = 0xFF) = (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `confident person is opaque, confident background is gone, the edge is smooth`() {
        listOf(0f, 0.5f, 1f).forEach { edge ->
            assertEquals(1f, BackgroundMath.alpha(1f, edge))
            assertEquals(0f, BackgroundMath.alpha(0f, edge))
            assertEquals(0.5f, BackgroundMath.alpha(0.5f, edge), 1e-4f)
            var last = -1f
            for (i in 0..100) {
                val a = BackgroundMath.alpha(i / 100f, edge)
                assertTrue(a >= last, "alpha must rise with confidence")
                last = a
            }
        }
        // A softer edge feathers further from the middle.
        assertTrue(BackgroundMath.alpha(0.65f, 1f) < BackgroundMath.alpha(0.65f, 0f))
    }

    @Test
    fun `the mask is scaled up smoothly`() {
        // 2 x 1 mask: left background, right person.
        val mask = floatArrayOf(0f, 1f)
        val left = BackgroundMath.sample(mask, 2, 1, 100, 10, 0, 5)
        val middle = BackgroundMath.sample(mask, 2, 1, 100, 10, 50, 5)
        val right = BackgroundMath.sample(mask, 2, 1, 100, 10, 99, 5)
        assertEquals(0f, left, 1e-4f)
        assertEquals(1f, right, 1e-4f)
        assertTrue(middle in 0.3f..0.7f)
    }

    @Test
    fun `blend puts the person over the new background`() {
        val person = argb(200, 150, 100)
        val white = BackgroundReplace.WHITE
        assertEquals(person, BackgroundMath.blend(person, white, 1f))
        assertEquals(white, BackgroundMath.blend(person, white, 0f))
        val half = BackgroundMath.blend(argb(0, 0, 0), argb(200, 200, 200), 0.5f)
        assertEquals(argb(100, 100, 100), half)
    }

    @Test
    fun `transparent cut out keeps colour and scales alpha`() {
        val person = argb(10, 20, 30)
        assertEquals(argb(10, 20, 30, a = 0), BackgroundMath.cutOut(person, 0f))
        assertEquals(person, BackgroundMath.cutOut(person, 1f))
        assertEquals(128, BackgroundMath.cutOut(person, 0.5f) ushr 24)
    }

    @Test
    fun `an empty or near empty mask has no person`() {
        assertFalse(BackgroundMath.hasPerson(FloatArray(1000)))
        assertFalse(BackgroundMath.hasPerson(FloatArray(1000) { if (it < 5) 1f else 0f }))
        assertTrue(BackgroundMath.hasPerson(FloatArray(1000) { if (it < 300) 0.9f else 0.1f }))
    }

    @Test
    fun `background choices survive a JSON round trip for background batches`() {
        listOf(
            BackgroundFill.Transparent,
            BackgroundFill.Solid(BackgroundReplace.EXAM_BLUE),
            BackgroundFill.Picture("content://media/1"),
        ).forEach { fill ->
            val effects = ImageEffects(background = BackgroundReplace(fill, edge = 0.7f))
            val json = Json.encodeToString(ImageEffects.serializer(), effects)
            assertEquals(effects, Json.decodeFromString(ImageEffects.serializer(), json))
        }
        assertTrue(BackgroundReplace(BackgroundFill.Transparent).needsAlpha)
        assertFalse(BackgroundReplace(BackgroundFill.Solid(BackgroundReplace.WHITE)).needsAlpha)
        assertThrows<IllegalArgumentException> { BackgroundReplace(BackgroundFill.Transparent, edge = 2f) }
    }
}
