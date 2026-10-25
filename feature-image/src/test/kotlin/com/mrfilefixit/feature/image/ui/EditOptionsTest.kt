package com.mrfilefixit.feature.image.ui

import com.mrfilefixit.core.domain.image.ImageFilter
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
    fun `text and look carry through, dark text without an outline`() {
        val effects = EditOptions(
            filter = ImageFilter.GRAYSCALE,
            brightness = 30,
            text = " Hello ",
            textPosition = OverlayPosition.TOP,
            textDark = true,
        ).toEffects()
        assertEquals(ImageFilter.GRAYSCALE, effects.filter)
        assertEquals(30, effects.adjust.brightness)
        val overlay = effects.texts.single()
        assertEquals("Hello", overlay.text)
        assertEquals(OverlayPosition.TOP.y, overlay.y)
        assertEquals(EditOptions.BLACK, overlay.colorArgb)
        assertFalse(overlay.outline)
        val light = EditOptions(text = "Hi").toEffects().texts.single()
        assertTrue(light.outline && light.colorArgb == TextOverlay.WHITE)
    }

    @Test
    fun `enhance becomes an upscale`() {
        val upscale = EnhanceOptions(factor = 4, sharpen = 2f).toEffects().upscale
        assertEquals(4, upscale?.factor)
        assertEquals(1f, upscale?.sharpen)
    }
}
