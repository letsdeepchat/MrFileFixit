package com.mrfilefixit.core.office.zip

import com.mrfilefixit.core.office.xml.XmlWriter
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes an OPC package. Collects content types and package relationships as parts are added and writes
 * `[Content_Types].xml` and `_rels/.rels` on [finish].
 */
class OpcWriter(output: OutputStream) {
    private val zip = ZipOutputStream(output)
    private val overrides = linkedMapOf<String, String>()
    private val defaults = linkedMapOf(
        "rels" to "application/vnd.openxmlformats-package.relationships+xml",
        "xml" to "application/xml",
    )
    private val rootRels = mutableListOf<Pair<String, String>>()

    fun addPart(name: String, contentType: String?, bytes: ByteArray) {
        if (contentType != null) overrides["/$name"] = contentType
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    fun addPart(name: String, contentType: String?, xml: XmlWriter) = addPart(name, contentType, xml.toBytes())

    /** Media parts use extension defaults instead of per-part overrides. */
    fun addMedia(name: String, extension: String, contentType: String, bytes: ByteArray) {
        defaults[extension] = contentType
        addPart(name, null, bytes)
    }

    fun addRootRelationship(type: String, target: String) {
        rootRels += type to target
    }

    fun finish() {
        val types = XmlWriter().within("Types", "xmlns" to CONTENT_TYPES_NS) {
            defaults.forEach { (ext, type) -> empty("Default", "Extension" to ext, "ContentType" to type) }
            overrides.forEach { (part, type) -> empty("Override", "PartName" to part, "ContentType" to type) }
        }
        addPart("[Content_Types].xml", null, types)
        addPart(
            "_rels/.rels",
            null,
            relationshipsXml(rootRels.mapIndexed { i, (type, target) -> RelEntry("rId${i + 1}", type, target) }),
        )
        zip.finish()
    }

    data class RelEntry(val id: String, val type: String, val target: String, val external: Boolean = false)

    companion object {
        private const val CONTENT_TYPES_NS = "http://schemas.openxmlformats.org/package/2006/content-types"
        private const val RELS_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
        const val REL_OFFICE_DOCUMENT =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
        const val REL_CORE_PROPERTIES =
            "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties"
        const val REL_EXTENDED_PROPERTIES =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties"
        const val REL_BASE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/"
        const val CT_CORE = "application/vnd.openxmlformats-package.core-properties+xml"
        const val CT_APP = "application/vnd.openxmlformats-officedocument.extended-properties+xml"

        fun relationshipsXml(rels: List<RelEntry>): XmlWriter = XmlWriter().within(
            "Relationships",
            "xmlns" to RELS_NS,
        ) {
            rels.forEach { rel ->
                empty(
                    "Relationship",
                    "Id" to rel.id,
                    "Type" to rel.type,
                    "Target" to rel.target,
                    "TargetMode" to if (rel.external) "External" else null,
                )
            }
        }

        /** docProps/core.xml with title and creator. Dates are left out: they would leak when a file was made. */
        fun coreProperties(title: String, author: String): XmlWriter = XmlWriter().within(
            "cp:coreProperties",
            "xmlns:cp" to "http://schemas.openxmlformats.org/package/2006/metadata/core-properties",
            "xmlns:dc" to "http://purl.org/dc/elements/1.1/",
        ) {
            if (title.isNotBlank()) element("dc:title", title)
            if (author.isNotBlank()) element("dc:creator", author)
        }

        fun appProperties(): XmlWriter = XmlWriter().within(
            "Properties",
            "xmlns" to "http://schemas.openxmlformats.org/officeDocument/2006/extended-properties",
        ) {
            element("Application", "Mr File Fixit")
        }
    }
}
