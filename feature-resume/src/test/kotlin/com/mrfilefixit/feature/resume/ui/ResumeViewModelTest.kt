package com.mrfilefixit.feature.resume.ui

import com.mrfilefixit.core.domain.convert.DocumentFormat
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.domain.file.DescribeFileUseCase
import com.mrfilefixit.core.domain.file.DestinationUri
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.FileRepository
import com.mrfilefixit.core.domain.file.ImportFormat
import com.mrfilefixit.core.domain.file.JobIdGenerator
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.file.SaveOutputUseCase
import com.mrfilefixit.core.domain.file.ShareOutputUseCase
import com.mrfilefixit.core.domain.file.TempFileStore
import com.mrfilefixit.core.domain.image.CompositeRequest
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.ImageInfo
import com.mrfilefixit.core.domain.image.ImageOp
import com.mrfilefixit.core.domain.image.ImageProcessor
import com.mrfilefixit.core.domain.image.ImageRequest
import com.mrfilefixit.core.domain.image.ProcessedImage
import com.mrfilefixit.core.domain.image.ResizeMode
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.resume.DocumentExporter
import com.mrfilefixit.core.domain.resume.ExportResumeUseCase
import com.mrfilefixit.core.domain.resume.ImportResumeUseCase
import com.mrfilefixit.core.domain.resume.Resume
import com.mrfilefixit.core.domain.resume.ResumeRepository
import com.mrfilefixit.core.domain.resume.ResumeSummary
import com.mrfilefixit.core.domain.resume.ResumeTemplates
import com.mrfilefixit.core.domain.security.InputLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResumeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private class Repo : ResumeRepository {
        val drafts = linkedMapOf<String, Resume>()
        val photos = mutableMapOf<String, ByteArray>()

        override suspend fun list(): AppResult<List<ResumeSummary>> = success(
            drafts.values.map { ResumeSummary(it.id, it.displayName, it.templateId, it.updatedAtMillis) },
        )
        override suspend fun load(id: String): AppResult<Resume> =
            drafts[id]?.let { success(it) } ?: failure(AppError.FileNotFound)
        override suspend fun save(resume: Resume): AppResult<Unit> = success(Unit).also { drafts[resume.id] = resume }
        override suspend fun delete(id: String): AppResult<Unit> = success(Unit).also { drafts.remove(id) }
        override suspend fun savePhoto(id: String, jpegPath: String): AppResult<Unit> =
            success(Unit).also { photos[id] = byteArrayOf(1, 2) }
        override suspend fun photo(id: String): AppResult<ByteArray?> = success(photos[id])
        override suspend fun deletePhoto(id: String): AppResult<Unit> = success(Unit).also { photos.remove(id) }
    }

    private class Exporter : DocumentExporter {
        var last: RichDocument? = null

        override suspend fun export(
            document: RichDocument,
            format: DocumentFormat,
            outputDir: String,
            baseName: String,
            progress: ProgressReporter,
        ): AppResult<OutputFile> {
            last = document
            return success(
                OutputFile(
                    "$outputDir/$baseName.${format.extension}",
                    "$baseName.${format.extension}",
                    format.mimeType,
                    10,
                    pageCount = 1,
                ),
            )
        }

        override suspend fun readEmbeddedData(source: FileRef): AppResult<String?> = success(null)
    }

    private class Images : ImageProcessor {
        var request: ImageRequest? = null

        override suspend fun readInfo(source: FileRef): AppResult<ImageInfo> =
            success(ImageInfo(1000, 1000, ImageFormat.JPEG, 100))

        override suspend fun process(request: ImageRequest, progress: ProgressReporter): AppResult<ProcessedImage> {
            this.request = request
            val file = OutputFile("${request.outputDir}/photo.jpg", "photo.jpg", "image/jpeg", 50, 413, 531)
            return success(ProcessedImage(file, ImageFormat.JPEG, 90, padded = false))
        }

        override suspend fun composite(
            request: CompositeRequest,
            progress: ProgressReporter,
        ): AppResult<ProcessedImage> = failure(AppError.Unexpected("unused"))

        override fun canEncode(format: ImageFormat): Boolean = true
    }

    private class Files : FileRepository, TempFileStore {
        val deleted = mutableListOf<String>()

        override suspend fun describe(uri: String): AppResult<FileRef> = success(
            FileRef(uri, "me.jpg", "image/jpeg", 100),
        )
        override suspend fun saveTo(output: OutputFile, destination: DestinationUri): AppResult<Unit> = success(Unit)
        override suspend fun shareUri(output: OutputFile): AppResult<String> = success("content://share")
        override suspend fun importToJob(
            uri: String,
            jobDir: String,
            baseName: String,
            format: ImportFormat,
            maxBytes: Long,
        ): AppResult<OutputFile> = failure(AppError.Unexpected("unused"))
        override suspend fun createJobDir(jobId: String): AppResult<String> = success("/cache/jobs/$jobId")
        override suspend fun deleteJobDir(jobId: String) {
            deleted += jobId
        }
        override suspend fun deleteStale(maxAgeMillis: Long) = Unit
    }

    private val repo = Repo()
    private val exporter = Exporter()
    private val images = Images()
    private val files = Files()

    private fun TestScope.viewModel(): ResumeViewModel {
        var n = 0
        return ResumeViewModel(
            repository = repo,
            exportResume = ExportResumeUseCase(repo, exporter),
            importResume = ImportResumeUseCase(exporter, repo, InputLimits()),
            describeFile = DescribeFileUseCase(files),
            imageProcessor = images,
            saveOutput = SaveOutputUseCase(files),
            shareOutput = ShareOutputUseCase(files),
            tempFiles = files,
            ids = JobIdGenerator { "id-${n++}" },
            appScope = this,
        )
    }

    private fun TestScope.editorWith(templateId: String = "SOFTWARE"): ResumeViewModel {
        val vm = viewModel()
        vm.onOpenTemplates(restyle = false)
        vm.onTemplateChosen(ResumeTemplates.byId(templateId))
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `choosing a template opens a saved draft with its sample content`() = runTest(dispatcher) {
        val vm = editorWith("LEGAL")
        val state = vm.state.value
        assertEquals(ResumePage.EDITOR, state.page)
        assertEquals("LEGAL", state.resume?.templateId)
        assertEquals(state.resume?.sections, repo.drafts.values.single().sections)
    }

    @Test
    fun `edits apply at once and are saved shortly after`() = runTest(dispatcher) {
        val vm = editorWith()
        vm.edit { it.copy(contact = it.contact.copy(fullName = "Asha Rao")) }
        assertEquals("Asha Rao", vm.state.value.resume?.contact?.fullName)
        advanceUntilIdle()
        assertEquals("Asha Rao", repo.drafts.values.single().contact.fullName)
    }

    @Test
    fun `back walks from editor to list and the list shows the draft`() = runTest(dispatcher) {
        val vm = editorWith()
        assertTrue(vm.onBack())
        advanceUntilIdle()
        assertEquals(ResumePage.LIST, vm.state.value.page)
        assertNull(vm.state.value.resume)
        assertEquals(1, vm.state.value.drafts.size)
        assertFalse(vm.onBack())
    }

    @Test
    fun `changing template from the editor keeps the text`() = runTest(dispatcher) {
        val vm = editorWith()
        val sections = vm.state.value.resume?.sections
        vm.onOpenTemplates(restyle = true)
        assertEquals(ResumePage.TEMPLATES, vm.state.value.page)
        vm.onTemplateChosen(ResumeTemplates.byId("ARTS"))
        val state = vm.state.value
        assertEquals(ResumePage.EDITOR, state.page)
        assertEquals("ARTS", state.resume?.templateId)
        assertEquals(sections, state.resume?.sections)
    }

    @Test
    fun `a picked photo is cropped to the 35 by 45 frame and resized to 413 by 531`() = runTest(dispatcher) {
        val vm = editorWith()
        vm.onPhoto(PhotoEvent.Picked("content://photo/1"))
        advanceUntilIdle()
        val crop = checkNotNull(vm.state.value.photoCrop)
        assertEquals(413f / 531f, crop.rect.width / crop.rect.height, 0.01f)
        vm.onPhoto(PhotoEvent.Confirm)
        advanceUntilIdle()
        val ops = images.request?.operations.orEmpty()
        assertEquals(ImageOp.Resize(413, 531, ResizeMode.FILL), ops.last())
        assertTrue(ops.first() is ImageOp.Crop)
        assertNull(vm.state.value.photoCrop)
        assertNotNull(vm.state.value.photo)
        assertTrue(repo.drafts.values.single().hasPhoto)
        assertTrue(files.deleted.isNotEmpty())
    }

    @Test
    fun `export gives a file and any later edit clears it`() = runTest(dispatcher) {
        val vm = editorWith()
        vm.onExport(DocumentFormat.PDF)
        advanceUntilIdle()
        val output = checkNotNull(vm.state.value.exported)
        assertTrue(output.displayName.endsWith(".pdf"))
        assertNotNull(exporter.last)
        vm.edit { it.copy(contact = it.contact.copy(phone = "1")) }
        advanceUntilIdle()
        assertTrue(vm.state.value.export is JobState.Idle)
    }
}
