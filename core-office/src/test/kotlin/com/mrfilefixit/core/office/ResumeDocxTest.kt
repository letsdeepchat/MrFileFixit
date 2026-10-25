package com.mrfilefixit.core.office

import com.mrfilefixit.core.domain.document.DocImage
import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.DocTable
import com.mrfilefixit.core.domain.document.ListKind
import com.mrfilefixit.core.domain.resume.ResumeFactory
import com.mrfilefixit.core.domain.resume.ResumeJson
import com.mrfilefixit.core.domain.resume.ResumeRenderer
import com.mrfilefixit.core.domain.resume.ResumeTemplates
import com.mrfilefixit.core.domain.resume.SectionKind
import com.mrfilefixit.core.office.docx.DocxReader
import com.mrfilefixit.core.office.docx.DocxWriter
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Every template renders to a DOCX that reads back with its text, headings, bullets and embedded draft. */
class ResumeDocxTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `every template exports to DOCX and can be re-opened`() {
        var n = 0
        ResumeTemplates.all.forEach { template ->
            val resume = ResumeFactory.fromTemplate(template, template.id, 0L) { "e${n++}" }
            val file = File(dir, "${template.id}.docx")
            file.outputStream().use { DocxWriter().write(ResumeRenderer.render(resume, template), it) }
            val read = DocxReader().read(file)
            assertEquals(resume, read.appData?.let(ResumeJson::decode), template.id)
            val paragraphs = read.blocks.filterIsInstance<DocParagraph>()
            assertEquals(resume.contact.fullName, paragraphs.first().plainText, template.id)
            val headings = paragraphs.filter { it.style.heading == 2 }.map { it.plainText.lowercase() }
            assertEquals(resume.sections.map { it.title.lowercase() }, headings, template.id)
            val hasBullets = resume.sections
                .filter { it.kind != SectionKind.SKILLS }
                .any { s -> s.entries.any { it.bullets.isNotEmpty() } }
            assertEquals(hasBullets, paragraphs.any { it.style.list == ListKind.BULLET }, template.id)
        }
    }

    @Test
    fun `a photo header survives as a picture beside the name`() {
        val template = ResumeTemplates.byId("HEALTHCARE")
        var n = 0
        val resume = ResumeFactory.fromTemplate(template, "p", 0L) { "x${n++}" }
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10)
        val file = File(dir, "photo.docx")
        file.outputStream().use { DocxWriter().write(ResumeRenderer.render(resume, template, jpeg), it) }
        val read = DocxReader().read(file)
        val header = read.blocks.first() as DocTable
        val image = header.rows.single().cells[1].blocks.filterIsInstance<DocImage>().single()
        assertTrue(image.bytes.contentEquals(jpeg))
    }
}
