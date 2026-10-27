package com.mrfilefixit.feature.image.ui

import com.mrfilefixit.core.domain.image.ImageFilter
import com.mrfilefixit.core.domain.image.OverlayFont
import com.mrfilefixit.core.domain.image.OverlayPosition
import com.mrfilefixit.core.domain.image.TextOverlay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditOptionsTest {
    @Test
    fun `untouched options change nothing`() {
        assertTrue(EditOptions().toEffects().isNone)
        assertFalse(EditOptions().usesStampSize)
    }

    @Test
    fun `the stamp keeps its non-blank lines and turns on the exam size`() {
        val edit = EditOptions(stamp = true, stampName = "  Asha Rao ", stampDate = " ", stampDark = true)
        val stamp = edit.toEffects().stamp
        assertEquals(listOf("Asha Rao"), stamp?.lines)
        assertEquals(true, stamp?.dark)
        assertTrue(edit.usesStampSize)
        assertFalse(edit.copy(stampKeepSize = false).usesStampSize)
    }

    @Test
    fun `a stamp with no text is left out`() {
        val edit = EditOptions(stamp = true)
        assertNull(edit.toEffects().stamp)
        assertFalse(edit.usesStampSize)
    }

    @Test
    fun `the 20 to 50 KB window holds under both KB readings`() {
        val target = EditOptions.STAMP_TARGET
        assertTrue(target.minBytes >= 20 * 1000 && target.minBytes >= 20 * 1024)
        assertTrue(target.maxBytes <= 50 * 1000 && target.maxBytes <= 50 * 1024)
    }

    @Test
    fun `text, font, colour and look carry through, always outlined in the opposite tone`() {
        val effects = EditOptions(
            filter = ImageFilter.GRAYSCALE,
            brightness = 30,
            text = " Hello ",
            textPosition = OverlayPosition.TOP,
            textColor = EditOptions.BLACK,
            textFont = OverlayFont.SERIF,
        ).toEffects()
        assertEquals(ImageFilter.GRAYSCALE, effects.filter)
        assertEquals(30, effects.adjust.brightness)
        val overlay = effects.texts.single()
        assertEquals("Hello", overlay.text)
        assertEquals(OverlayPosition.TOP.y, overlay.y)
        assertEquals(EditOptions.BLACK, overlay.colorArgb)
        assertEquals(OverlayFont.SERIF, overlay.font)
        assertTrue(overlay.outline)
        assertFalse(overlay.isLight)
        val light = EditOptions(text = "Hi").toEffects().texts.single()
        assertTrue(light.outline && light.colorArgb == TextOverlay.WHITE && light.isLight)
        assertEquals(OverlayFont.SANS, light.font)
    }

    @Test
    fun `a custom colour is made opaque and every swatch is distinct`() {
        val half = 0x80FF0000.toInt()
        val overlay = EditOptions(text = "x", textColor = half).toEffects().texts.single()
        assertEquals(0xFFFF0000.toInt(), overlay.colorArgb)
        assertEquals(EditOptions.TEXT_SWATCHES.size, EditOptions.TEXT_SWATCHES.distinct().size)
    }

    @Test
    fun `enhance becomes a natural enhance with a safe strength and scale`() {
        val enhance = EnhanceOptions(strength = 2f, scale = 4).toEffects().enhance
        assertEquals(4, enhance?.scale)
        assertEquals(1f, enhance?.strength)
        assertEquals(1, EnhanceOptions(scale = 3).toEffects().enhance?.scale)
        assertEquals(0f, EnhanceOptions(strength = -1f).toEffects().enhance?.strength)
    }
}
