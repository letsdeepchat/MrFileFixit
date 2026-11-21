@file:Suppress("MagicNumber") // Byte offsets from the ICC.1 profile specification.

package com.mrfilefixit.core.domain.image

/**
 * Removes what identifies the phone from an embedded ICC colour profile while keeping the colour data, so
 * pictures look the same after "Remove metadata".
 *
 * Phones write their maker and model into the profile header (device manufacturer, device model, profile
 * creator) and sometimes into the `dmnd` / `dmdd` description tags. The header fields and the profile ID (an
 * MD5 that would no longer match) are zeroed, which the specification allows; the text of the two device tags
 * is blanked in place, so offsets and sizes stay valid.
 */
object IccProfiles {
    private const val HEADER_SIZE = 128
    private const val SIGNATURE_OFFSET = 36
    private val SIGNATURE = "acsp".toByteArray(Charsets.US_ASCII)
    private val ZEROED = listOf(48 until 56, 80 until 100) // manufacturer + model, creator + profile ID
    private const val TAG_COUNT_OFFSET = 128
    private const val TAG_ENTRY_SIZE = 12
    private const val TAG_TYPE_HEADER = 8 // type signature + reserved bytes
    private const val MAX_TAGS = 200
    private val DEVICE_TAGS = setOf("dmnd", "dmdd")

    /**
     * Anonymizes the profile that starts at [offset] in [data], in place. Only the first chunk of a profile split
     * over several JPEG segments has a header and tag table; later chunks, short or foreign data are left alone.
     */
    fun anonymize(data: ByteArray, offset: Int) {
        if (offset < 2 || offset + HEADER_SIZE + 4 > data.size) return
        if (data[offset - 2].toInt() != 1) return // JPEG chunk number: only chunk 1 carries the header
        if (!SIGNATURE.indices.all { data[offset + SIGNATURE_OFFSET + it] == SIGNATURE[it] }) return
        ZEROED.forEach { range -> range.forEach { data[offset + it] = 0 } }
        val count = readInt(data, offset + TAG_COUNT_OFFSET).coerceIn(0, MAX_TAGS)
        for (i in 0 until count) {
            val entry = offset + TAG_COUNT_OFFSET + 4 + i * TAG_ENTRY_SIZE
            if (entry + TAG_ENTRY_SIZE > data.size) return
            val tag = String(data, entry, 4, Charsets.US_ASCII)
            if (tag !in DEVICE_TAGS) continue
            val start = offset.toLong() + readInt(data, entry + 4).toLong() + TAG_TYPE_HEADER
            val end = offset.toLong() + readInt(data, entry + 4).toLong() + readInt(data, entry + 8).toLong()
            if (start < 0 || end > data.size || start >= end) continue
            for (b in start.toInt() until end.toInt()) data[b] = 0
        }
    }

    private fun readInt(bytes: ByteArray, pos: Int): Int =
        ((bytes[pos].toInt() and 0xFF) shl 24) or
            ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
            ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
            (bytes[pos + 3].toInt() and 0xFF)
}
