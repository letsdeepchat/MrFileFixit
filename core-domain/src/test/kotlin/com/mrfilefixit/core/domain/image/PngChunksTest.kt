package com.mrfilefixit.core.domain.image

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.zip.CRC32

class PngChunksTest {
    private val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(typeBytes); update(data) }.value.toInt()
        return ByteBuffer.allocate(12 + data.size).putInt(data.size).put(typeBytes).put(data).putInt(crc).array()
    }

    private val ihdr = chunk("IHDR", ByteArray(13) { 1 })
    private val srgb = chunk("sRGB", byteArrayOf(0))
    private val text = chunk("tEXt", "Author\u0000Someone".toByteArray())
    private val exif = chunk("eXIf", "MM\u0000*GPS".toByteArray())
    private val idat = chunk("IDAT", ByteArray(20) { 7 })
    private val iend = chunk("IEND", ByteArray(0))

    @Test
    fun `removes text and exif chunks but keeps image and colour chunks`() {
        val png = signature + ihdr + srgb + text + exif + idat + iend
        assertArrayEquals(signature + ihdr + srgb + idat + iend, PngChunks.stripMetadata(png))
    }

    @Test
    fun `rejects truncated or non png input`() {
        assertNull(PngChunks.stripMetadata(signature + ihdr + idat))
        assertNull(PngChunks.stripMetadata(byteArrayOf(1, 2, 3)))
    }
}
