@file:Suppress("MagicNumber") // Byte offsets and marker values from the file-format specification.

package com.mrfilefixit.core.domain.image

import java.io.ByteArrayOutputStream

/**
 * Removes metadata chunks from a PNG without touching image data:
 * eXIf (EXIF), tEXt/zTXt/iTXt (text, often XMP), and tIME. Colour chunks (gAMA, cHRM, sRGB, iCCP) stay.
 */
object PngChunks {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val METADATA = setOf("eXIf", "tEXt", "zTXt", "iTXt", "tIME")
    private const val LENGTH_BYTES = 4
    private const val TYPE_BYTES = 4
    private const val CRC_BYTES = 4

    fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= SIGNATURE.size && SIGNATURE.indices.all { bytes[it] == SIGNATURE[it] }

    /** Returns the PNG without metadata chunks, or null when the stream is not a well-formed PNG. */
    fun stripMetadata(bytes: ByteArray): ByteArray? {
        if (!isPng(bytes)) return null
        val out = ByteArrayOutputStream(bytes.size)
        out.write(SIGNATURE)
        var pos = SIGNATURE.size
        while (pos + LENGTH_BYTES + TYPE_BYTES <= bytes.size) {
            val length = readInt(bytes, pos)
            if (length < 0) return null
            val total = LENGTH_BYTES.toLong() + TYPE_BYTES + length + CRC_BYTES
            if (pos + total > bytes.size) return null
            val type = String(bytes, pos + LENGTH_BYTES, TYPE_BYTES, Charsets.US_ASCII)
            if (type !in METADATA) out.write(bytes, pos, total.toInt())
            pos += total.toInt()
            if (type == "IEND") return out.toByteArray()
        }
        return null
    }

    private fun readInt(bytes: ByteArray, pos: Int): Int =
        ((bytes[pos].toInt() and 0xFF) shl 24) or
            ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
            ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
            (bytes[pos + 3].toInt() and 0xFF)
}
