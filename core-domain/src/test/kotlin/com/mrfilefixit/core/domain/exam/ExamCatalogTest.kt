package com.mrfilefixit.core.domain.exam

import com.mrfilefixit.core.domain.image.ImageFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExamCatalogTest {
    private val photo = DocumentSpec(
        DocumentKind.PHOTO,
        setOf(ImageFormat.JPEG),
        20,
        50,
        IntBounds.exactly(200),
        IntBounds.exactly(230),
        200,
        230,
    )

    private fun profile(id: String, short: String, name: String, body: String, category: ExamCategory) =
        ExamProfile(
            id = id,
            shortName = short,
            name = name,
            category = category,
            conductingBody = body,
            lastVerified = null,
            verification = VerificationStatus.COMMUNITY,
            documents = listOf(photo),
        )

    private val ssc =
        profile("ssc-cgl", "SSC CGL", "Combined Graduate Level", "Staff Selection Commission", ExamCategory.SSC)
    private val sbi = profile("sbi-po", "SBI PO", "Probationary Officer", "State Bank of India", ExamCategory.BANKING)
    private val nda = profile("upsc-nda", "UPSC NDA", "National Defence Academy", "UPSC", ExamCategory.DEFENCE)
    private val all = listOf(ssc, sbi, nda)

    @Test
    fun `empty query and no category returns everything in bundled order`() {
        assertEquals(all, ExamCatalog.filter(all, "  ", null))
    }

    @Test
    fun `every word must match, ignoring case and punctuation`() {
        assertEquals(listOf(ssc), ExamCatalog.filter(all, "ssc-CGL", null))
        assertEquals(emptyList<ExamProfile>(), ExamCatalog.filter(all, "ssc po", null))
    }

    @Test
    fun `category aliases find exams whose names do not say it`() {
        assertEquals(listOf(sbi), ExamCatalog.filter(all, "banking", null))
        assertEquals(listOf(nda), ExamCatalog.filter(all, "defense", null))
    }

    @Test
    fun `category filter combines with the query`() {
        assertEquals(listOf(nda), ExamCatalog.filter(all, "", ExamCategory.DEFENCE))
        assertEquals(emptyList<ExamProfile>(), ExamCatalog.filter(all, "ssc", ExamCategory.DEFENCE))
    }

    @Test
    fun `categories are distinct and in pill order`() {
        assertEquals(
            listOf(ExamCategory.SSC, ExamCategory.BANKING, ExamCategory.DEFENCE),
            ExamCatalog.categories(listOf(nda, sbi, ssc, sbi)),
        )
    }
}
