package com.mrfilefixit.core.office.zip

import com.mrfilefixit.core.office.OfficeFormatException
import com.mrfilefixit.core.office.OfficeLimitException
import com.mrfilefixit.core.office.OfficeLimits
import com.mrfilefixit.core.office.xml.XmlReader
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/** One relationship from a `.rels` part. [target] is resolved to an absolute part name unless [external]. */
data class Relationship(val id: String, val type: String, val target: String, val external: Boolean)

/**
 * Read access to an Open Packaging Conventions container (DOCX, PPTX, XLSX), with zip-bomb limits:
 * entry count, bytes per entry and total expanded bytes are capped while reading, not trusted from headers.
 */
class OpcPackage(file: File, private val limits: OfficeLimits = OfficeLimits.DEFAULT) : Closeable {
    private val zip = ZipFile(file)
    private val names: Set<String>
    private var expandedBytes = 0L

    init {
        val entries = zip.entries().asSequence().take(limits.maxZipEntries + 1).toList()
        if (entries.size > limits.maxZipEntries) {
            zip.close()
            throw OfficeLimitException("Too many zip entries")
        }
        names = entries.map { it.name.trimStart('/') }.toSet()
    }

    fun has(partName: String): Boolean = partName.trimStart('/') in names

    /** Bytes of a part, or null when it does not exist. */
    fun read(partName: String): ByteArray? {
        val name = partName.trimStart('/')
        if (name !in names) return null
        val entry = zip.getEntry(name) ?: return null
        return zip.getInputStream(entry).use { readBounded(it) }
    }

    fun readXml(partName: String): XmlReader? = read(partName)?.let { XmlReader(decodeXml(it)) }

    /** Relationships of [partName] (from `_rels/<name>.rels` next to it). Empty when there are none. */
    fun relationships(partName: String): Map<String, Relationship> {
        val name = partName.trimStart('/')
        val dir = name.substringBeforeLast('/', "")
        val relsName = (if (dir.isEmpty()) "" else "$dir/") + "_rels/" + name.substringAfterLast('/') + ".rels"
        val xml = readXml(relsName) ?: return emptyMap()
        val result = mutableMapOf<String, Relationship>()
        while (xml.next() != XmlReader.Event.EOF) {
            if (xml.event == XmlReader.Event.START && xml.localName == "Relationship") {
                val id = xml.attr("Id") ?: continue
                val target = xml.attr("Target") ?: continue
                val external = xml.attr("TargetMode") == "External"
                result[id] = Relationship(
                    id = id,
                    type = xml.attr("Type").orEmpty(),
                    target = if (external) target else resolve(dir, target),
                    external = external,
                )
            }
        }
        return result
    }

    /** The main part of the given relationship type from the package root (for example the main document). */
    fun rootPart(typeSuffix: String): String? =
        relationships("").values.firstOrNull { it.type.endsWith(typeSuffix) && !it.external }?.target

    override fun close() = zip.close()

    private fun readBounded(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        var entryBytes = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            entryBytes += n
            expandedBytes += n
            if (entryBytes > limits.maxEntryBytes || expandedBytes > limits.maxTotalBytes) {
                throw OfficeLimitException("Archive expands beyond the safety limit")
            }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        private const val BUFFER = 16 * 1024

        /** Resolves a relationship target against the source part's directory. */
        fun resolve(baseDir: String, target: String): String {
            if (target.startsWith("/")) return normalize(target.trimStart('/'))
            val joined = if (baseDir.isEmpty()) target else "$baseDir/$target"
            return normalize(joined)
        }

        private fun normalize(path: String): String {
            val parts = ArrayDeque<String>()
            for (segment in path.split('/')) {
                when (segment) {
                    "", "." -> Unit
                    ".." -> parts.removeLastOrNull() ?: throw OfficeFormatException("Part path escapes package")
                    else -> parts.addLast(segment)
                }
            }
            return parts.joinToString("/")
        }

        /** XML parts are UTF-8 or UTF-16 with a BOM. */
        fun decodeXml(bytes: ByteArray): String = when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            bytes.size >= UTF8_BOM && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte() -> String(bytes, UTF8_BOM, bytes.size - UTF8_BOM, Charsets.UTF_8)
            else -> String(bytes, Charsets.UTF_8)
        }

        private const val UTF8_BOM = 3
    }
}
