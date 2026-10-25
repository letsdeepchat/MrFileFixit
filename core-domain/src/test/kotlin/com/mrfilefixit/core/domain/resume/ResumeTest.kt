package com.mrfilefixit.core.domain.resume

import com.mrfilefixit.core.domain.convert.DocumentFormat
import com.mrfilefixit.core.domain.document.DocImage
import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.DocTable
import com.mrfilefixit.core.domain.document.ListKind
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.security.InputLimits
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResumeTest {
    private var counter = 0
    private val newId: () -> String = { "id${counter++}" }

    private fun draft(field: ResumeField = ResumeField.SOFTWARE): Resume =
        ResumeFactory.fromTemplate(ResumeTemplates.byId(field.name), "r1", 1_000L, newId)

    private fun paragraphs(doc: RichDocument): List<DocParagraph> = doc.blocks.flatMap { block ->
        when (block) {
            is DocParagraph -> listOf(block)
            is DocTable -> block.rows.flatMap { it.cells }.flatMap { it.blocks.filterIsInstance<DocParagraph>() }
            else -> emptyList()
        }
    }

    @Test
    fun `there is one template per field, each with contact details and a field-specific order`() {
        val templates = ResumeTemplates.all
        assertEquals(15, templates.size)
        assertEquals(ResumeField.entries.map { it.name }, templates.map { it.id })
        templates.forEach { t ->
            assertTrue(t.sampleContact.fullName.isNotBlank(), t.id)
            assertTrue(t.sections.any { it.kind == SectionKind.EDUCATION }, t.id)
            assertTrue(t.sections.any { it.kind == SectionKind.EXPERIENCE }, t.id)
        }
        assertEquals(SectionKind.EDUCATION, ResumeTemplates.byId("FRESH_GRADUATE").sections[1].kind)
        assertEquals(ResumeTemplates.all.first(), ResumeTemplates.byId("unknown"))
    }

    @Test
    fun `a draft from a template has unique ids and the template's look`() {
        val resume = draft(ResumeField.LEGAL)
        val ids = resume.sections.map { it.id } + resume.sections.flatMap { s -> s.entries.map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it.isEmpty() })
        assertEquals(ResumeFont.SERIF, resume.style.font)
        assertEquals("LEGAL", resume.templateId)
    }

    @Test
    fun `JSON keeps every field and rejects other text`() {
        val resume = draft().copy(hasPhoto = true, style = ResumeStyle(bodySizePt = 12f, paper = ResumePaper.LETTER))
        assertEquals(resume, ResumeJson.decode(ResumeJson.encode(resume)))
        assertNull(ResumeJson.decode("{\"hello\": 1}"))
        assertNull(ResumeJson.decode("not json"))
        // Fields added by a newer version are ignored rather than failing.
        val withExtra = ResumeJson.encode(resume).replaceFirst("{", "{\"futureField\":true,")
        assertEquals(resume, ResumeJson.decode(withExtra))
    }

    @Test
    fun `sections and entries move, add and remove`() {
        var resume = draft()
        val first = resume.sections[0].id
        val second = resume.sections[1].id
        resume = ResumeEdits.moveSection(resume, first, 1)
        assertEquals(listOf(second, first), resume.sections.take(2).map { it.id })
        assertEquals(resume, ResumeEdits.moveSection(resume, second, -1))

        resume = ResumeEdits.addSection(resume, SectionKind.CUSTOM, "Languages", newId)
        val custom = resume.sections.last()
        assertEquals("Languages", custom.title)
        assertEquals(1, custom.entries.size)

        val experience = resume.sections.first { it.kind == SectionKind.EXPERIENCE }
        val (a, b) = experience.entries.map { it.id }
        resume = ResumeEdits.moveEntry(resume, experience.id, b, -1)
        assertEquals(listOf(b, a), resume.sections.first { it.id == experience.id }.entries.map { it.id })
        resume = ResumeEdits.updateEntry(resume, experience.id, a) { it.copy(title = "Lead") }
        assertEquals("Lead", resume.sections.first { it.id == experience.id }.entries.last().title)
        resume = ResumeEdits.removeEntry(resume, experience.id, a)
        assertEquals(listOf(b), resume.sections.first { it.id == experience.id }.entries.map { it.id })
        resume = ResumeEdits.removeSection(resume, custom.id)
        assertFalse(resume.sections.any { it.id == custom.id })
    }

    @Test
    fun `typed bullets and skills are split and cleaned`() {
        assertEquals(
            listOf("Led a team", "Shipped v2"),
            ResumeEdits.bulletsFromText("• Led a team\n\n- Shipped v2  \n"),
        )
        assertEquals(listOf("Kotlin", "SQL", "Git"), ResumeEdits.skillsFromText("Kotlin, SQL;\nGit,"))
    }

    @Test
    fun `changing template keeps content and order but takes the new look`() {
        val resume = ResumeEdits.moveSection(draft(), draft().sections[0].id, 2)
        val moved = ResumeEdits.applyTemplate(
            resume.copy(style = resume.style.copy(bodySizePt = 12f)),
            ResumeTemplates.byId("ARTS"),
        )
        assertEquals("ARTS", moved.templateId)
        assertEquals(ResumeFont.SERIF, moved.style.font)
        assertEquals(12f, moved.style.bodySizePt)
        assertEquals(resume.sections, moved.sections)
    }

    @Test
    fun `render gives ATS-friendly text in reading order`() {
        val resume = draft()
        val doc = ResumeRenderer.render(resume, ResumeTemplates.byId(resume.templateId))
        val paragraphs = paragraphs(doc)
        assertEquals("Aarav Mehta", paragraphs.first().plainText)
        val headings = paragraphs.filter { it.style.heading == 2 }.map { it.plainText }
        assertEquals(resume.sections.map { it.title }, headings)
        assertTrue(paragraphs.any { it.style.list == ListKind.BULLET })
        assertTrue(paragraphs.any { it.plainText == "Languages: Kotlin, Java, Python, SQL" })
        assertTrue(paragraphs[2].runs.any { it.link == "mailto:aarav.mehta@example.com" })
        assertTrue(paragraphs[2].runs.any { it.link == "https://github.com/aaravmehta" })
        assertTrue(doc.blocks.none { it is DocTable || it is DocImage })
        assertEquals(resume, doc.appData?.let(ResumeJson::decode))
    }

    @Test
    fun `hidden and empty sections are left out, caps headings are upper case`() {
        var resume = draft(ResumeField.LEGAL)
        val hidden = resume.sections[1]
        resume = ResumeEdits.updateSection(resume, hidden.id) { it.copy(visible = false) }
        resume = ResumeEdits.addSection(resume, SectionKind.PROJECTS, "Empty", newId)
        val headings = paragraphs(ResumeRenderer.render(resume, ResumeTemplates.byId("LEGAL")))
            .filter { it.style.heading == 2 }.map { it.plainText }
        assertFalse(hidden.title.uppercase() in headings)
        assertFalse("EMPTY" in headings)
        assertTrue("PROFESSIONAL SUMMARY" in headings)
    }

    @Test
    fun `a photo goes beside the name in a borderless row`() {
        val doc = ResumeRenderer.render(draft(), ResumeTemplates.byId("ARTS"), byteArrayOf(1, 2, 3))
        val header = doc.blocks.first() as DocTable
        assertNull(header.borderArgb)
        val image = header.rows.single().cells[1].blocks.single() as DocImage
        assertEquals(531f / 413f, image.heightPt / image.widthPt, 0.001f)
    }

    @Test
    fun `tint mixes with white`() {
        assertEquals(-0x1, ResumeRenderer.tint(-0x1000000, 0f))
        assertEquals(-0x1000000, ResumeRenderer.tint(-0x1000000, 1f))
    }

    private class FakeExporter(val embedded: String?) : DocumentExporter {
        var exported: RichDocument? = null

        override suspend fun export(
            document: RichDocument,
            format: DocumentFormat,
            outputDir: String,
            baseName: String,
            progress: ProgressReporter,
        ): AppResult<OutputFile> {
            exported = document
            return success(OutputFile("$outputDir/$baseName.${format.extension}", baseName, format.mimeType, 1))
        }

        override suspend fun readEmbeddedData(source: FileRef): AppResult<String?> = success(embedded)
    }

    private class FakeRepository : ResumeRepository {
        val saved = mutableListOf<Resume>()
        var photo: ByteArray? = byteArrayOf(9)

        override suspend fun list(): AppResult<List<ResumeSummary>> = success(emptyList())
        override suspend fun load(id: String): AppResult<Resume> = success(saved.first { it.id == id })
        override suspend fun save(resume: Resume): AppResult<Unit> = success(Unit).also { saved += resume }
        override suspend fun delete(id: String): AppResult<Unit> = success(Unit)
        override suspend fun savePhoto(id: String, jpegPath: String): AppResult<Unit> = success(Unit)
        override suspend fun photo(id: String): AppResult<ByteArray?> = success(photo)
        override suspend fun deletePhoto(id: String): AppResult<Unit> = success(Unit)
    }

    private val docx = FileRef("content://x/1", "cv.docx", DocumentFormat.DOCX.mimeType, 1_000)

    @Test
    fun `export uses the stored photo and names the file after the person`() = runTest {
        val exporter = FakeExporter(null)
        val result = ExportResumeUseCase(FakeRepository(), exporter)(
            draft().copy(hasPhoto = true),
            DocumentFormat.PDF,
            "/cache/jobs/1",
            ProgressReporter.NONE,
        )
        assertEquals("Aarav Mehta Resume", (result as Result.Success).value.displayName)
        assertTrue(exporter.exported!!.blocks.first() is DocTable)
        val xlsx = ExportResumeUseCase(
            FakeRepository(),
            exporter,
        )(draft(), DocumentFormat.XLSX, "/x", ProgressReporter.NONE)
        assertTrue((xlsx as Result.Failure).error is AppError.UnsupportedFormat)
    }

    @Test
    fun `import makes a new draft from an exported DOCX and rejects other files`() = runTest {
        val original = draft().copy(hasPhoto = true)
        val repository = FakeRepository()
        val imported = ImportResumeUseCase(FakeExporter(ResumeJson.encode(original)), repository, InputLimits())(
            docx,
            "new",
            5L,
        )
        val resume = (imported as Result.Success).value
        assertEquals("new", resume.id)
        assertFalse(resume.hasPhoto)
        assertEquals(original.sections, resume.sections)
        assertNotNull(repository.saved.singleOrNull())

        val plain = ImportResumeUseCase(FakeExporter(null), repository, InputLimits())(docx, "n2", 5L)
        assertEquals(AppError.NotAResume, (plain as Result.Failure).error)
        val pdf = ImportResumeUseCase(FakeExporter(null), repository, InputLimits())(
            docx.copy(displayName = "cv.pdf", mimeType = "application/pdf"),
            "n3",
            5L,
        )
        assertTrue((pdf as Result.Failure).error is AppError.UnsupportedFormat)
    }
}
