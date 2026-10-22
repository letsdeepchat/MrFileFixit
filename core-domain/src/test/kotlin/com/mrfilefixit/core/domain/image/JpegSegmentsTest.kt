package com.mrfilefixit.core.domain.image

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class JpegSegmentsTest {
    private fun segment(marker: Int, payload: ByteArray): ByteArray {
        val length = payload.size + 2
        return byteArrayOf(0xFF.toByte(), marker.toByte(), (length shr 8).toByte(), length.toByte()) + payload
    }

    private val soi = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    private val app0 = segment(0xE0, "JFIF\u0000".toByteArray())
    private val exif = segment(0xE1, "Exif\u0000\u0000GPS=28.6,77.2".toByteArray())
    private val icc = segment(0xE2, "ICC_PROFILE\u0000rgb".toByteArray())
    private val comment = segment(0xFE, "made with camera X".toByteArray())
    private val dqt = segment(0xDB, ByteArray(65) { 1 })
    private val scan = byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0x00, 0x02, 0x12, 0x34, 0xFF.toByte(), 0xD9.toByte())

    private val jpeg = soi + app0 + exif + icc + comment + dqt + scan

    @Test
    fun `strips EXIF and comments, keeps JFIF, ICC and image data`() {
        val stripped = JpegSegments.stripMetadata(jpeg)
        assertArrayEquals(soi + app0 + icc + dqt + scan, stripped)
    }

    @Test
    fun `rejects non jpeg and truncated input`() {
        assertNull(JpegSegments.stripMetadata("PNG".toByteArray()))
        assertNull(JpegSegments.stripMetadata(jpeg.copyOf(10)))
        assertFalse(JpegSegments.isJpeg(byteArrayOf(1, 2, 3, 4)))
    }

    @ParameterizedTest
    @ValueSource(ints = [4, 5, 7, 100, 65_535, 65_536, 65_537, 65_538, 65_539, 200_001])
    fun `pads to the exact target size with valid comment segments`(extra: Int) {
        val target = (jpeg.size + extra).toLong()
        val padded = JpegSegments.padToSize(jpeg, target)
        assertEquals(target, padded.size.toLong())
        assertTrue(JpegSegments.isJpeg(padded))
        // Padding is metadata: stripping it gives back the original minus the original metadata.
        assertArrayEquals(JpegSegments.stripMetadata(jpeg), JpegSegments.stripMetadata(padded))
    }

    @Test
    fun `padding never shrinks and ignores non jpeg`() {
        assertArrayEquals(jpeg, JpegSegments.padToSize(jpeg, 10))
        val notJpeg = byteArrayOf(1, 2, 3)
        assertArrayEquals(notJpeg, JpegSegments.padToSize(notJpeg, 100))
    }
}
