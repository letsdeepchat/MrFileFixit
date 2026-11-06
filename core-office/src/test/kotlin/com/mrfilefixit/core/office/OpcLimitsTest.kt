package com.mrfilefixit.core.office

import com.mrfilefixit.core.office.zip.OpcPackage
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class OpcLimitsTest {
    @TempDir
    lateinit var dir: File

    private fun packageWith(name: String, bytes: Long): File = File(dir, "p.zip").also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            val chunk = ByteArray(CHUNK) { 'a'.code.toByte() }
            var left = bytes
            while (left > 0) {
                val n = minOf(left, CHUNK.toLong()).toInt()
                zip.write(chunk, 0, n)
                left -= n
            }
            zip.closeEntry()
        }
    }

    @Test
    fun `an xml part above the xml cap is refused before it fills the heap`() {
        val limits = OfficeLimits(maxXmlPartBytes = OfficeLimits.MIB)
        // A few kilobytes on disk that would expand to 2 MiB of XML.
        val file = packageWith("word/document.xml", 2 * OfficeLimits.MIB)
        OpcPackage(file, limits).use { pkg ->
            assertThrows<OfficeLimitException> { pkg.read("word/document.xml") }
        }
    }

    @Test
    fun `media parts use the larger per-entry cap`() {
        val limits = OfficeLimits(maxXmlPartBytes = OfficeLimits.MIB)
        val file = packageWith("word/media/image1.png", 2 * OfficeLimits.MIB)
        OpcPackage(file, limits).use { pkg ->
            assertEquals(2 * OfficeLimits.MIB, pkg.read("word/media/image1.png")?.size?.toLong())
        }
    }

    private companion object {
        const val CHUNK = 64 * 1024
    }
}
