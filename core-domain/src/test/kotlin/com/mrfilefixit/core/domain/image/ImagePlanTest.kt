package com.mrfilefixit.core.domain.image

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ImagePlanTest {
    private val rect = NormalizedRect(0.1f, 0.2f, 0.5f, 0.9f)

    @Test
    fun `canonical order is accepted and rotations are summed`() {
        val plan = ImagePlan.from(
            listOf(
                ImageOp.Rotate(90),
                ImageOp.Rotate(270),
                ImageOp.Rotate(90),
                ImageOp.Crop(rect),
                ImageOp.Resize(10, 20),
            ),
        )
        assertEquals(ImagePlan(90, rect, ImageOp.Resize(10, 20), null), plan)
    }

    @Test
    fun `out of order operations are rejected`() {
        assertNull(ImagePlan.from(listOf(ImageOp.Crop(rect), ImageOp.Rotate(90))))
        assertNull(ImagePlan.from(listOf(ImageOp.Resize(1, 1), ImageOp.Crop(rect))))
        assertNull(ImagePlan.from(listOf(ImageOp.Resize(1, 1), ImageOp.Scale(50))))
    }

    @Test
    fun `rotating four times is identity and ccw inverts cw`() {
        var r = rect
        repeat(4) { r = RectTransforms.rotateCw(r) }
        assertRect(rect, r)
        assertRect(rect, RectTransforms.rotateCcw(RectTransforms.rotateCw(rect)))
    }

    @Test
    fun `display crop maps to raw space for a portrait phone photo`() {
        // Raw sensor image is landscape, EXIF says rotate 90. The user crops the top half of the upright photo.
        val topHalf = NormalizedRect(0f, 0f, 1f, 0.5f)
        val raw = RectTransforms.displayToRaw(topHalf, exifRotation = 90, flipped = false, userRotation = 0)
        // Top of the upright photo is the left side of the raw landscape image.
        assertRect(NormalizedRect(0f, 0f, 0.5f, 1f), raw)
    }

    @Test
    fun `mirrored orientation maps left to right`() {
        val leftHalf = NormalizedRect(0f, 0f, 0.5f, 1f)
        assertRect(NormalizedRect(0.5f, 0f, 1f, 1f), RectTransforms.displayToRaw(leftHalf, 0, true, 0))
    }

    @ParameterizedTest
    @CsvSource("4000,3000,200,230,8", "4000,3000,4000,3000,1", "1000,1000,300,300,2", "100,100,400,400,1")
    fun `sample size keeps at least the target resolution`(sw: Int, sh: Int, tw: Int, th: Int, expected: Int) {
        assertEquals(expected, sampleSizeFor(sw, sh, tw, th))
    }

    @Test
    fun `geometry for fit, fill and pad`() {
        val fit = OutputGeometry.compute(
            4000,
            2000,
            ImagePlan(0, NormalizedRect.FULL, ImageOp.Resize(1000, 1000, ResizeMode.FIT), null),
        )
        assertEquals(1000 to 500, fit.canvasWidth to fit.canvasHeight)

        val pad = OutputGeometry.compute(
            4000,
            2000,
            ImagePlan(0, NormalizedRect.FULL, ImageOp.Resize(1000, 1000, ResizeMode.PAD), null),
        )
        assertEquals(
            listOf(1000, 1000, 0, 250, 1000, 500),
            listOf(pad.canvasWidth, pad.canvasHeight, pad.drawLeft, pad.drawTop, pad.drawWidth, pad.drawHeight),
        )

        val fill = OutputGeometry.compute(
            4000,
            2000,
            ImagePlan(0, NormalizedRect.FULL, ImageOp.Resize(1000, 1000, ResizeMode.FILL), null),
        )
        assertRect(NormalizedRect(0.25f, 0f, 0.75f, 1f), fill.fillCrop)

        val scaled = OutputGeometry.compute(4000, 2000, ImagePlan(0, NormalizedRect.FULL, null, 25))
        assertEquals(1000 to 500, scaled.canvasWidth to scaled.canvasHeight)
    }

    private fun assertRect(expected: NormalizedRect, actual: NormalizedRect) {
        assertEquals(expected.left, actual.left, 1e-5f)
        assertEquals(expected.top, actual.top, 1e-5f)
        assertEquals(expected.right, actual.right, 1e-5f)
        assertEquals(expected.bottom, actual.bottom, 1e-5f)
    }
}
