package com.mrfilefixit.core.domain.biodata

import com.mrfilefixit.core.domain.convert.DocumentFormat
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.resume.DocumentExporter
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BiodataModelTest {
    private val today = LocalDate.of(2026, 10, 27)

    @Test
    fun `photos are capped at five and must be at least one`() {
        var b = Biodata("a", BiodataTemplates.TRADITIONAL.id)
        assertFalse(b.hasValidPhotos)
        repeat(7) { b = BiodataEdits.addPhoto(b, BiodataPhoto("p$it", 10, 10)) }
        assertEquals(Biodata.MAX_PHOTOS, b.photos.size)
        assertTrue(b.hasValidPhotos)
        assertFalse(b.canAddPhoto)
        // The same photo twice is ignored.
        val one = BiodataEdits.addPhoto(Biodata("b", "x"), BiodataPhoto("k", 1, 1))
        assertEquals(1, BiodataEdits.addPhoto(one, BiodataPhoto("k", 1, 1)).photos.size)
        assertThrows<IllegalArgumentException> { BiodataPhoto("bad", 0, 10) }
    }

    @Test
    fun `photos can be reordered, replaced and removed`() {
        var b =
            Biodata(
                "a",
                "x",
                photos = listOf(BiodataPhoto("a", 1, 1), BiodataPhoto("b", 1, 1), BiodataPhoto("c", 1, 1)),
            )
        b = BiodataEdits.makePrimary(b, "c")
        assertEquals(listOf("c", "a", "b"), b.photos.map { it.key })
        b = BiodataEdits.movePhoto(b, 2, 1)
        assertEquals(listOf("c", "b", "a"), b.photos.map { it.key })
        assertEquals(b, BiodataEdits.movePhoto(b, 0, 9))
        b = BiodataEdits.replacePhoto(b, BiodataPhoto("b", 300, 400))
        assertEquals(400, b.photos[1].heightPx)
        b = BiodataEdits.removePhoto(b, "c")
        assertEquals(listOf("b", "a"), b.photos.map { it.key })
    }

    @Test
    fun `text is capped and cleaned, blank removes the value`() {
        var b = Biodata("a", "x")
        b = BiodataEdits.setValue(b, BiodataField.HEIGHT, "5 ft\u0007 9 in\nextra")
        assertEquals("5 ft 9 inextra", b.value(BiodataField.HEIGHT))
        b = BiodataEdits.setValue(b, BiodataField.ADDRESS, "Line 1\nLine 2")
        assertEquals("Line 1\nLine 2", b.value(BiodataField.ADDRESS))
        b = BiodataEdits.setValue(b, BiodataField.HEIGHT, "   ")
        assertFalse(BiodataField.HEIGHT in b.values)
        b = BiodataEdits.setName(b, "x".repeat(500))
        assertEquals(Biodata.MAX_LINE, b.name.length)
    }

    @Test
    fun `cultural section toggles as a whole`() {
        var b = Biodata("a", "x")
        assertTrue(BiodataEdits.culturalShown(b))
        b = BiodataEdits.setCulturalShown(b, false)
        assertFalse(BiodataEdits.culturalShown(b))
        assertTrue(BiodataField.entries.filter { it.toggleable }.all { it in b.hidden })
        b = BiodataEdits.setCulturalShown(b, true)
        assertTrue(b.hidden.isEmpty())
    }

    @Test
    fun `restyling keeps to the same kind of biodata`() {
        val b = Biodata("a", BiodataTemplates.TRADITIONAL.id)
        assertEquals(BiodataTemplates.MODERN.id, BiodataEdits.applyTemplate(b, BiodataTemplates.MODERN).templateId)
        assertEquals(b, BiodataEdits.applyTemplate(b, BiodataTemplates.PHOTOGRAPHER))
    }

    @Test
    fun `every field belongs to a section of its kind`() {
        BiodataField.entries.forEach { field ->
            assertTrue(field.kinds.isNotEmpty())
            field.kinds.forEach { kind -> assertTrue(field in field.section.fields(kind)) }
        }
        assertEquals(9, BiodataSection.CULTURAL.fields(BiodataKind.MARRIAGE).size)
        assertTrue(BiodataSection.forKind(BiodataKind.PORTFOLIO).none { it == BiodataSection.FAMILY })
    }

    @Test
    fun `json round trip and garbage is rejected`() {
        val b = BiodataEdits.setValue(
            Biodata("id-1", BiodataTemplates.CLASSIC.id, name = "Asha", photos = listOf(BiodataPhoto("p", 3, 4))),
            BiodataField.GOTRA,
            "Kashyap",
        ).copy(hidden = setOf(BiodataField.CASTE))
        assertEquals(b, BiodataJson.decode(BiodataJson.encode(b)))
        assertNull(BiodataJson.decode("{not json"))
        assertNull(BiodataJson.decode("""{"id":"x","templateId":"y","values":{"NOPE":"1"}}"""))
    }

    @Test
    fun `age from date of birth`() {
        assertEquals(28, BiodataAge.years("14-02-1998", today))
        assertEquals(28, BiodataAge.years("14/02/1998", today))
        assertEquals(28, BiodataAge.years("1998-02-14", today))
        assertEquals(27, BiodataAge.years("28.10.1998", today))
        assertNull(BiodataAge.years("31-02-1998", today))
        assertNull(BiodataAge.years("01-01-2030", today))
        assertNull(BiodataAge.years("soon", today))
        assertEquals("05-03-2001", BiodataAge.format(LocalDate.of(2001, 3, 5)))
    }

    // region export use case

    private class FakeRepository(private val missing: Set<String> = emptySet()) : BiodataRepository {
        override suspend fun list() = success(emptyList<BiodataSummary>())
        override suspend fun load(id: String): AppResult<Biodata> = Result.Failure(AppError.FileNotFound)
        override suspend fun save(biodata: Biodata) = success(Unit)
        override suspend fun delete(id: String) = success(Unit)
        override suspend fun savePhoto(id: String, key: String, jpegPath: String) = success(Unit)
        override suspend fun photo(id: String, key: String): AppResult<ByteArray?> =
            success(if (key in missing) null else byteArrayOf(1))
        override suspend fun deletePhoto(id: String, key: String) = success(Unit)
    }

    private class FakeExporter(private val size: Long = 2048) : DocumentExporter {
        var document: RichDocument? = null

        override suspend fun export(
            document: RichDocument,
            format: DocumentFormat,
            outputDir: String,
            baseName: String,
            progress: ProgressReporter,
        ): AppResult<OutputFile> {
            this.document = document
            return success(
                OutputFile("$outputDir/$baseName.pdf", "$baseName.pdf", format.mimeType, size, pageCount = 1),
            )
        }

        override suspend fun readEmbeddedData(source: FileRef): AppResult<String?> = success(null)
    }

    private fun draft(photos: Int) = BiodataRendererTest.filled(BiodataTemplates.MODERN)
        .copy(photos = BiodataRendererTest.photoList(photos))

    @Test
    fun `export makes a pdf named after the person`() = runTest {
        val exporter = FakeExporter()
        val result = ExportBiodataUseCase(FakeRepository(), exporter)(
            draft(3),
            BiodataLabels.English,
            DocumentFormat.PDF,
            "/out",
            today,
        )
        assertTrue(result is Result.Success)
        assertEquals("Asha Verma Biodata.pdf", (result as Result.Success).value.displayName)
        assertEquals(3, BiodataRendererTest.images(exporter.document?.blocks.orEmpty()).size)
    }

    @Test
    fun `export needs at least one photo and at most five`() = runTest {
        val useCase = ExportBiodataUseCase(FakeRepository(), FakeExporter())
        assertEquals(
            Result.Failure(AppError.PhotoRequired),
            useCase(draft(0), BiodataLabels.English, DocumentFormat.PDF, "/out", today),
        )
        assertEquals(
            Result.Failure(AppError.TooManyFiles(6, 5)),
            useCase(draft(6), BiodataLabels.English, DocumentFormat.PDF, "/out", today),
        )
        val allMissing = ExportBiodataUseCase(FakeRepository(missing = setOf("p1")), FakeExporter())
        assertEquals(
            Result.Failure(AppError.PhotoRequired),
            allMissing(draft(1), BiodataLabels.English, DocumentFormat.PDF, "/out", today),
        )
    }

    @Test
    fun `an empty output file is reported, never offered`() = runTest {
        val useCase = ExportBiodataUseCase(FakeRepository(), FakeExporter(size = 0))
        assertEquals(
            Result.Failure(AppError.EmptyOutput),
            useCase(draft(1), BiodataLabels.English, DocumentFormat.DOCX, "/out", today),
        )
    }

    @Test
    fun `only pdf and word are offered`() = runTest {
        val useCase = ExportBiodataUseCase(FakeRepository(), FakeExporter())
        val result = useCase(draft(1), BiodataLabels.English, DocumentFormat.XLSX, "/out", today)
        assertTrue((result as Result.Failure).error is AppError.UnsupportedFormat)
    }

    // endregion
}
