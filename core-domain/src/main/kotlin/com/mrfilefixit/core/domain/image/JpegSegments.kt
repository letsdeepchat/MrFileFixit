@file:Suppress("MagicNumber") // Byte offsets and marker values from the file-format specification.

package com.mrfilefixit.core.domain.image

import java.io.ByteArrayOutputStream

/**
 * Byte-level JPEG helpers that work without decoding pixels.
 *
 * - [stripMetadata] removes EXIF/XMP (APP1), ICC-free APP2..APP15 vendor blocks and comments, keeping
 *   APP0 (JFIF) and the ICC profile in APP2 so colours stay correct.
 * - [padToSize] appends COM (comment) segments after SOI so a file reaches a portal's minimum size.
 *   Pixels are untouched; decoders ignore COM segments.
 */
object JpegSegments {
    private const val MARKER = 0xFF
    private const val SOI = 0xD8
    private const val SOS = 0xDA
    private const val EOI = 0xD9
    private const val APP0 = 0xE0
    private const val APP1 = 0xE1
    private const val APP2 = 0xE2
    private const val APP15 = 0xEF
    private const val COM = 0xFE
    private const val TEM = 0x01
    private const val RST0 = 0xD0
    private const val RST7 = 0xD7

    /** Max payload of one segment: the 2-byte length field counts itself. */
    const val MAX_SEGMENT_PAYLOAD = 0xFFFF - 2
    private const val SEGMENT_OVERHEAD = 4
    private val ICC_TAG = "ICC_PROFILE".toByteArray(Charsets.US_ASCII)

    fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes.u(0) == MARKER && bytes.u(1) == SOI

    /** Returns the JPEG without metadata, or null if the stream is not a well-formed JPEG. */
    fun stripMetadata(bytes: ByteArray): ByteArray? {
        if (!isJpeg(bytes)) return null
        val out = ByteArrayOutputStream(bytes.size)
        out.write(bytes, 0, 2)
        var pos = 2
        while (pos + 1 < bytes.size) {
            if (bytes.u(pos) != MARKER) return null
            val marker = bytes.u(pos + 1)
            when {
                marker == MARKER -> {
                    pos++
                    continue
                } // fill byte
                marker == SOS || marker == EOI -> {
                    out.write(bytes, pos, bytes.size - pos) // entropy-coded data to the end
                    return out.toByteArray()
                }
                marker == TEM || marker in RST0..RST7 -> {
                    out.write(bytes, pos, 2)
                    pos += 2
                    continue
                }
            }
            if (pos + 3 >= bytes.size) return null
            val length = (bytes.u(pos + 2) shl 8) or bytes.u(pos + 3)
            val end = pos + 2 + length
            if (length < 2 || end > bytes.size) return null
            if (keepSegment(marker, bytes, pos + 4)) out.write(bytes, pos, end - pos)
            pos = end
        }
        return null
    }

    private fun keepSegment(marker: Int, bytes: ByteArray, payloadStart: Int): Boolean = when (marker) {
        APP0 -> true
        APP2 -> startsWith(bytes, payloadStart, ICC_TAG)
        APP1, in (APP2 + 1)..APP15, COM -> false
        else -> true
    }

    /**
     * Pads [bytes] with comment segments so the result is exactly [targetSize] bytes, if possible.
     * Returns the input unchanged when it is already at least [targetSize] or is not a JPEG.
     */
    fun padToSize(bytes: ByteArray, targetSize: Long): ByteArray {
        if (!isJpeg(bytes) || bytes.size >= targetSize) return bytes
        var missing = (targetSize - bytes.size).toInt()
        if (missing < SEGMENT_OVERHEAD) missing = SEGMENT_OVERHEAD // smallest possible segment
        val out = ByteArrayOutputStream(bytes.size + missing)
        out.write(bytes, 0, 2)
        while (missing > 0) {
            var payload = minOf(MAX_SEGMENT_PAYLOAD, missing - SEGMENT_OVERHEAD)
            // Avoid leaving a remainder too small to form another segment.
            val remainder = missing - SEGMENT_OVERHEAD - payload
            if (remainder in 1 until SEGMENT_OVERHEAD) payload -= SEGMENT_OVERHEAD - remainder
            val length = payload + 2
            out.write(MARKER)
            out.write(COM)
            out.write(length ushr 8)
            out.write(length and 0xFF)
            repeat(payload) { out.write(' '.code) }
            missing -= payload + SEGMENT_OVERHEAD
        }
        out.write(bytes, 2, bytes.size - 2)
        return out.toByteArray()
    }

    private fun startsWith(bytes: ByteArray, offset: Int, prefix: ByteArray): Boolean {
        if (offset + prefix.size > bytes.size) return false
        for (i in prefix.indices) if (bytes[offset + i] != prefix[i]) return false
        return true
    }

    private fun ByteArray.u(index: Int): Int = this[index].toInt() and 0xFF
}
