package com.mrfilefixit.feature.image.ui

import com.mrfilefixit.core.domain.exam.DocumentKind
import com.mrfilefixit.core.domain.exam.DocumentSpec
import com.mrfilefixit.core.domain.exam.ExamCategory
import com.mrfilefixit.core.domain.exam.ExamProfile
import com.mrfilefixit.core.domain.exam.IntBounds
import com.mrfilefixit.core.domain.exam.VerificationStatus
import com.mrfilefixit.core.domain.image.ImageFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResizePresetsTest {
    private fun spec(kind: DocumentKind, width: Int, height: Int) = DocumentSpec(
        kind,
        setOf(ImageFormat.JPEG),
        10,
        50,
        IntBounds.exactly(width),
        IntBounds.exactly(height),
        width,
        height,
    )

    private fun profile(id: String, vararg docs: DocumentSpec) = ExamProfile(
        id = id,
        shortName = id.uppercase(),
        name = id,
        category = ExamCategory.OTHER,
        conductingBody = "Body",
        sourceUrl = "https://example.org",
        lastVerified = null,
        verification = VerificationStatus.COMMUNITY,
        documents = docs.toList(),
    )

    private val ssc = profile("ssc-cgl", spec(DocumentKind.PHOTO, 350, 450), spec(DocumentKind.SIGNATURE, 400, 200))
    private val ibps = profile("ibps-po", spec(DocumentKind.PHOTO, 200, 230))

    @Test
    fun `linked exam uploads come first, then passport and common presets without duplicates`() {
        val ids = ResizePresets.build(listOf(ssc, ibps), linkedExamId = "ssc-cgl").map { it.id }
        assertEquals(listOf("ssc-cgl:PHOTO", "ssc-cgl:SIGNATURE", "passport", "ibps-po:PHOTO"), ids)
    }

    @Test
    fun `without a linked exam only the defaults that exist are offered`() {
        val presets = ResizePresets.build(listOf(ibps), linkedExamId = null)
        assertEquals(listOf("passport", "ibps-po:PHOTO"), presets.map { it.id })
        assertEquals(200 to 230, presets[1].width to presets[1].height)
    }

    @Test
    fun `aspect sync keeps the source ratio both ways`() {
        assertEquals(1920, AspectRatioSync.heightFor(1080, sourceWidth = 3000, sourceHeight = 5333))
        assertEquals(1080, AspectRatioSync.widthFor(1920, sourceWidth = 3000, sourceHeight = 5333))
        assertEquals(1, AspectRatioSync.heightFor(1, sourceWidth = 4000, sourceHeight = 10))
    }
}
