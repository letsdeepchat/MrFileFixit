package com.mrfilefixit.core.domain.exam

import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.ImageInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SpecCheckerTest {
    private val photo = DocumentSpec(
        kind = DocumentKind.PHOTO,
        formats = setOf(ImageFormat.JPEG),
        minKb = 20,
        maxKb = 50,
        width = IntBounds.exactly(200),
        height = IntBounds.exactly(230),
        targetWidth = 200,
        targetHeight = 230,
    )

    @Test
    fun `passing image passes every check`() {
        val report = SpecChecker.check(photo, ImageInfo(200, 230, ImageFormat.JPEG, 30 * 1024L))
        assertTrue(report.passed)
        assertEquals(4, report.checks.size)
    }

    @Test
    fun `each failing rule is reported separately`() {
        val report = SpecChecker.check(photo, ImageInfo(201, 230, ImageFormat.PNG, 51 * 1024L + 1))
        assertFalse(report.passed)
        assertEquals(
            listOf(SpecCheck.Format::class, SpecCheck.FileSize::class, SpecCheck.Width::class),
            report.failures.map { it::class },
        )
    }

    @Test
    fun `KB bounds are inclusive in KiB`() {
        assertTrue(SpecChecker.check(photo, ImageInfo(200, 230, ImageFormat.JPEG, 20 * 1024L)).passed)
        assertTrue(SpecChecker.check(photo, ImageInfo(200, 230, ImageFormat.JPEG, 50 * 1024L)).passed)
        assertFalse(SpecChecker.check(photo, ImageInfo(200, 230, ImageFormat.JPEG, 20 * 1024L - 1)).passed)
    }

    @Test
    fun `target bytes satisfy both KB readings`() {
        val range = photo.targetBytes
        assertTrue(range.first >= 20 * 1024)
        assertTrue(range.last <= 50 * 1000)
        assertTrue(range.first < range.last)
    }

    @Test
    fun `open bounds accept any value on that side`() {
        val bounds = IntBounds(min = 350, max = null)
        assertTrue(10_000 in bounds)
        assertFalse(349 in bounds)
    }

    @Test
    fun `invalid specs are rejected at construction`() {
        assertThrows<IllegalArgumentException> { photo.copy(minKb = 60) }
        assertThrows<IllegalArgumentException> { photo.copy(targetWidth = 199) }
        assertThrows<IllegalArgumentException> { photo.copy(formats = emptySet()) }
    }
}
