@file:Suppress("MagicNumber") // Byte offsets and marker values from the file-format specification.

package com.mrfilefixit.core.domain.image

import java.io.ByteArrayOutputStream

/**
 * Byte-level JPEG helpers that work without decoding pixels.
 *
 * - [stripMetadata] removes EXIF/XMP (APP1), ICC-free APP2..APP15 vendor blocks, comments and trailers after
 *   the image, keeping APP0 (JFIF) and the ICC profile in APP2 (with its device fields blanked) so colours
 *   stay correct.
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

    /** Marker (2) + length (2) + "ICC_PROFILE\u0000" (12) + chunk number (1) + chunk count (1). */
    private const val ICC_SEGMENT_HEADER = 18
    private const val DONE = -1
    private const val FAILED = -2

    fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes.u(0) == MARKER && bytes.u(1) == SOI

    /**
     * Returns the JPEG without metadata, or null if the stream is not a well-formed JPEG.
     *
     * Besides EXIF/XMP, vendor APPn blocks and comments, this drops everything after the end-of-image marker
     * (Samsung's SEF trailer and Motion Photo videos live there, with the phone model and capture details), and
     * blanks the device maker, model and creator fields of the ICC profile, which phones fill in too.
     */
    fun stripMetadata(bytes: ByteArray): ByteArray? {
        if (!isJpeg(bytes)) return null
        val out = ByteArrayOutputStream(bytes.size)
        out.write(bytes, 0, 2)
        var pos = 2
        while (pos >= 0 && pos + 1 < bytes.size) pos = copyNext(bytes, pos, out)
        return if (pos == DONE) out.toByteArray() else null
    }

    /** Copies what to keep of the marker at [pos]; returns where the next one starts, [DONE] or [FAILED]. */
    private fun copyNext(bytes: ByteArray, pos: Int, out: ByteArrayOutputStream): Int {
        if (bytes.u(pos) != MARKER) return FAILED
        val marker = bytes.u(pos + 1)
        return when {
            marker == MARKER -> pos + 1 // fill byte
            marker == EOI -> {
                out.write(bytes, pos, 2)
                DONE // anything after EOI is a trailer, not part of the picture
            }
            marker == TEM || marker in RST0..RST7 -> {
                out.write(bytes, pos, 2)
                pos + 2
            }
            else -> copySegment(bytes, pos, marker, out)
        }
    }

    private fun copySegment(bytes: ByteArray, pos: Int, marker: Int, out: ByteArrayOutputStream): Int {
        if (pos + 3 >= bytes.size) return FAILED
        val length = (bytes.u(pos + 2) shl 8) or bytes.u(pos + 3)
        val end = pos + 2 + length
        if (length < 2 || end > bytes.size) return FAILED
        return when {
            marker == SOS -> {
                // Scan header, then entropy-coded data up to the next real marker (progressive JPEGs have
                // several scans with tables in between, so parsing goes on from there).
                val dataEnd = entropyDataEnd(bytes, end)
                out.write(bytes, pos, dataEnd - pos)
                if (dataEnd >= bytes.size) DONE else dataEnd // truncated after the scan: keep what there is
            }
            marker == APP2 && startsWith(bytes, pos + 4, ICC_TAG) -> {
                val segment = bytes.copyOfRange(pos, end)
                IccProfiles.anonymize(segment, ICC_SEGMENT_HEADER)
                out.write(segment)
                end
            }
            else -> {
                if (keepSegment(marker)) out.write(bytes, pos, end - pos)
                end
            }
        }
    }

    /** Index of the first marker after entropy-coded data starting at [start] (stuffed 0xFF00 and RSTn skipped). */
    private fun entropyDataEnd(bytes: ByteArray, start: Int): Int {
        var i = start
        while (i + 1 < bytes.size) {
            if (bytes.u(i) == MARKER) {
                val next = bytes.u(i + 1)
                if (next != 0 && next != MARKER && next !in RST0..RST7) return i
            }
            i++
        }
        return bytes.size
    }

    private fun keepSegment(marker: Int): Boolean = when (marker) {
        APP0 -> true
        in APP1..APP15, COM -> false
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
