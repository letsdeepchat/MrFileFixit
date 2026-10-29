package com.mrfilefixit.core.office

import java.io.IOException

/** The file is not a well-formed document of the expected type. Messages never contain file content. */
class OfficeFormatException(message: String) : IOException(message)

/** The document is password-protected (encrypted), which the converters do not open. */
class OfficeEncryptedException : IOException("Encrypted document")

/** The document is valid but exceeds a safety limit (entries, expanded size, rows, slides). */
class OfficeLimitException(message: String) : IOException(message)

/**
 * Safety limits for reading office files. Defaults suit a 50 MB input cap and a phone's ~256 MB heap: an XML part is
 * held as bytes and then as a UTF-16 string (about 3x its size), and pictures stay in memory until the PDF is drawn.
 */
data class OfficeLimits(
    val maxZipEntries: Int = 4_000,
    val maxEntryBytes: Long = DEFAULT_ENTRY_BYTES,
    val maxXmlPartBytes: Long = DEFAULT_XML_PART_BYTES,
    val maxTotalBytes: Long = DEFAULT_TOTAL_BYTES,
    val maxSlides: Int = 500,
    val maxSheetRows: Int = 100_000,
    val maxSheetColumns: Int = 1_000,
    val maxBlocks: Int = 200_000,
) {
    companion object {
        const val MIB = 1024L * 1024L
        private const val DEFAULT_ENTRY_BYTES = 48L * MIB
        private const val DEFAULT_XML_PART_BYTES = 24L * MIB
        private const val DEFAULT_TOTAL_BYTES = 128L * MIB
        val DEFAULT = OfficeLimits()
    }
}

/** Detected container of an office file, from its first bytes. */
enum class OfficeContainer {
    ZIP,
    COMPOUND,
    PDF,
    UNKNOWN,
    ;

    companion object {
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        private val CFB_MAGIC = byteArrayOf(
            0xD0.toByte(),
            0xCF.toByte(),
            0x11,
            0xE0.toByte(),
            0xA1.toByte(),
            0xB1.toByte(),
            0x1A,
            0xE1.toByte(),
        )
        private val PDF_MAGIC = "%PDF".toByteArray(Charsets.US_ASCII)

        fun sniff(header: ByteArray): OfficeContainer = when {
            header.startsWith(ZIP_MAGIC) -> ZIP
            header.startsWith(CFB_MAGIC) -> COMPOUND
            header.startsWith(PDF_MAGIC) -> PDF
            else -> UNKNOWN
        }

        /** Reads just the first bytes of [file]. */
        fun sniff(file: java.io.File): OfficeContainer {
            val header = ByteArray(CFB_MAGIC.size)
            val read = file.inputStream().use { it.read(header) }
            return sniff(if (read in 0 until header.size) header.copyOf(read) else header)
        }

        private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
            size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
    }
}
