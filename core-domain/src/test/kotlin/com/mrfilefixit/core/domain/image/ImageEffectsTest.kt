package com.mrfilefixit.core.domain.image

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ImageEffectsTest {
    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun channels(argb: Int) = Triple((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    @Test
    fun `the neutral matrix leaves pixels unchanged`() {
        val matrix = ImageEffectMath.colorMatrix(ColorAdjust(), grayscale = false)
        val pixel = argb(12, 130, 250)
        assertEquals(pixel, ImageEffectMath.applyMatrix(matrix, pixel))
    }

    @Test
    fun `grayscale gives equal channels at the pixel's luminance`() {
        val matrix = ImageEffectMath.colorMatrix(ColorAdjust(), grayscale = true)
        val (r, g, b) = channels(ImageEffectMath.applyMatrix(matrix, argb(255, 0, 0)))
        assertEquals(r, g)
        assertEquals(g, b)
        assertEquals(76, r) // 0.299 * 255
    }

    @Test
    fun `brightness lifts, contrast spreads around mid grey, saturation zero is grey`() {
        val bright = ImageEffectMath.colorMatrix(ColorAdjust(brightness = 50), grayscale = false)
        assertEquals(150, channels(ImageEffectMath.applyMatrix(bright, argb(100, 100, 100))).first)
        val contrast = ImageEffectMath.colorMatrix(ColorAdjust(contrast = 100), grayscale = false)
        assertEquals(0, channels(ImageEffectMath.applyMatrix(contrast, argb(40, 40, 40))).first)
        assertEquals(255, channels(ImageEffectMath.applyMatrix(contrast, argb(220, 220, 220))).first)
        val flat = ImageEffectMath.colorMatrix(ColorAdjust(saturation = -100), grayscale = false)
        val (r, g, b) = channels(ImageEffectMath.applyMatrix(flat, argb(200, 50, 50)))
        assertTrue(r == g && g == b)
    }

    @Test
    fun `enhance upscale multiplies the size but keeps the long side under the cap`() {
        assertEquals(1200 to 800, ImageEffectMath.enhancedSize(600, 400, NaturalEnhance(scale = 2)))
        assertEquals(
            NaturalEnhance.MAX_SIDE to NaturalEnhance.MAX_SIDE / 2,
            ImageEffectMath.enhancedSize(1000, 500, NaturalEnhance(scale = 4)),
        )
        assertEquals(600 to 400, ImageEffectMath.enhancedSize(600, 400, NaturalEnhance(scale = 1)))
        // Already above the cap: never shrinks.
        assertEquals(4000 to 3000, ImageEffectMath.enhancedSize(4000, 3000, NaturalEnhance(scale = 2)))
        assertThrows<IllegalArgumentException> { NaturalEnhance(scale = 3) }
        assertThrows<IllegalArgumentException> { NaturalEnhance(strength = 1.5f) }
    }

    @Test
    fun `stamp band sits at the bottom and its lines fit inside`() {
        val stamp = PhotoStamp(listOf("ASHA RAO", "25-10-2026"), dark = true, heightFraction = 0.2f)
        val layout = ImageEffectMath.stampLayout(350, 450, stamp)
        assertEquals(90, layout.bandHeight)
        assertEquals(360, layout.bandTop)
        assertEquals(2, layout.baselines.size)
        assertTrue(layout.baselines.all { it > 360 && it < 450 })
        assertTrue(layout.textSizePx * 1.2f <= 45f)
        assertThrows<IllegalArgumentException> { PhotoStamp(emptyList()) }
    }

    @Test
    fun `effects survive a JSON round trip for background batches`() {
        val effects = ImageEffects(
            adjust = ColorAdjust(10, -20, 30),
            filter = ImageFilter.DOCUMENT,
            stamp = PhotoStamp(listOf("Name"), dark = false),
            texts = listOf(TextOverlay("Hello", y = OverlayPosition.TOP.y)),
            enhance = NaturalEnhance(0.5f, 2),
        )
        val json = Json.encodeToString(ImageEffects.serializer(), effects)
        assertEquals(effects, Json.decodeFromString(ImageEffects.serializer(), json))
        assertTrue(ImageEffects.NONE.isNone)
    }
}
