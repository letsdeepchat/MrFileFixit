package com.mrfilefixit.feature.resume.ui

import com.mrfilefixit.core.domain.biodata.Biodata
import com.mrfilefixit.core.domain.biodata.BiodataEdits
import com.mrfilefixit.core.domain.biodata.BiodataField
import com.mrfilefixit.core.domain.biodata.BiodataRepository
import com.mrfilefixit.core.domain.biodata.BiodataSummary
import com.mrfilefixit.core.domain.biodata.BiodataTemplates
import com.mrfilefixit.core.domain.biodata.ExportBiodataUseCase
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
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.resume.DocumentExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
class BiodataViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private class Repo : BiodataRepository {
        val drafts = linkedMapOf<String, Biodata>()
        val photos = mutableMapOf<Pair<String, String>, ByteArray>()

        override suspend fun list(): AppResult<List<BiodataSummary>> = success(
            drafts.values.map {
                BiodataSummary(it.id, it.displayName, it.templateId, it.photos.size, it.updatedAtMillis)
            },
        )
        override suspend fun load(id: String): AppResult<Biodata> =
            drafts[id]?.let { success(it) } ?: failure(AppError.FileNotFound)
        override suspend fun save(biodata: Biodata): AppResult<Unit> =
            success(Unit).also { drafts[biodata.id] = biodata }
        override suspend fun delete(id: String): AppResult<Unit> = success(Unit).also { drafts.remove(id) }
        override suspend fun savePhoto(id: String, key: String, jpegPath: String): AppResult<Unit> =
            success(Unit).also { photos[id to key] = byteArrayOf(1, 2) }
        override suspend fun photo(id: String, key: String): AppResult<ByteArray?> = success(photos[id to key])
        override suspend fun deletePhoto(id: String, key: String): AppResult<Unit> =
            success(Unit).also { photos.remove(id to key) }
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
    private var exports = 0

    private fun TestScope.viewModel(): BiodataViewModel {
        var n = 0
        val counting = object : DocumentExporter by exporter {
            override suspend fun export(
                document: RichDocument,
                format: DocumentFormat,
                outputDir: String,
                baseName: String,
                progress: ProgressReporter,
            ): AppResult<OutputFile> = exporter.export(document, format, outputDir, baseName, progress)
                .also { exports++ }
        }
        return BiodataViewModel(
            repository = repo,
            exportBiodata = ExportBiodataUseCase(repo, counting),
            describeFile = DescribeFileUseCase(files),
            imageProcessor = images,
            saveOutput = SaveOutputUseCase(files),
            shareOutput = ShareOutputUseCase(files),
            tempFiles = files,
            ids = JobIdGenerator { "id-${n++}" },
            appScope = this,
        )
    }

    private fun TestScope.editorWith(templateId: String = BiodataTemplates.CLASSIC.id): BiodataViewModel {
        val vm = viewModel()
        vm.onOpenTemplates(restyle = false)
        vm.onTemplateChosen(BiodataTemplates.byId(templateId))
        advanceUntilIdle()
        return vm
    }

    private fun TestScope.addPhoto(vm: BiodataViewModel) {
        vm.onPhoto(BiodataPhotoEvent.Picked("content://photo/${vm.state.value.biodata?.photos?.size}"))
        advanceUntilIdle()
        vm.onPhoto(BiodataPhotoEvent.Confirm)
        advanceUntilIdle()
    }

    @Test
    fun `choosing a design opens the editor and saves a draft`() = runTest(dispatcher) {
        val vm = editorWith()
        val state = vm.state.value
        assertEquals(BiodataPage.EDITOR, state.page)
        assertEquals(BiodataTemplates.CLASSIC.id, state.biodata?.templateId)
        val id = checkNotNull(state.biodata?.id)
        assertTrue(id.all(Char::isLetterOrDigit), "ids are safe file names")
        assertEquals(id, repo.drafts.keys.single())
    }

    @Test
    fun `a marriage biodata starts with a portrait crop and takes up to five photos`() = runTest(dispatcher) {
        val vm = editorWith()
        vm.onPhoto(BiodataPhotoEvent.Picked("content://photo/first"))
        advanceUntilIdle()
        assertEquals(PhotoAspect.PORTRAIT, vm.state.value.photoCrop?.aspect)
        vm.onPhoto(BiodataPhotoEvent.Confirm)
        advanceUntilIdle()
        val ops = images.request?.operations.orEmpty()
        assertTrue(ops.first() is ImageOp.Crop)
        repeat(4) { addPhoto(vm) }
        assertEquals(5, vm.state.value.biodata?.photos?.size)
        assertEquals(5, vm.state.value.photos.size)
        assertFalse(checkNotNull(vm.state.value.biodata).canAddPhoto)
        // A sixth pick is ignored.
        vm.onPhoto(BiodataPhotoEvent.Picked("content://photo/6"))
        advanceUntilIdle()
        assertNull(vm.state.value.photoCrop)
        assertEquals(5, repo.drafts.values.single().photos.size)
    }

    @Test
    fun `a portfolio starts with a square profile photo`() = runTest(dispatcher) {
        val vm = editorWith(BiodataTemplates.PHOTOGRAPHER.id)
        vm.onPhoto(BiodataPhotoEvent.Picked("content://photo/1"))
        advanceUntilIdle()
        assertEquals(PhotoAspect.SQUARE, vm.state.value.photoCrop?.aspect)
    }

    @Test
    fun `export needs a photo, then gives a file that any edit clears`() = runTest(dispatcher) {
        val vm = editorWith()
        vm.onExport(DocumentFormat.PDF)
        advanceUntilIdle()
        assertEquals(AppError.PhotoRequired, (vm.state.value.export as JobState.Failed).error)
        addPhoto(vm)
        vm.onExport(DocumentFormat.DOCX)
        advanceUntilIdle()
        val output = checkNotNull(vm.state.value.exported)
        assertTrue(output.displayName.endsWith(".docx"))
        vm.edit { BiodataEdits.setValue(it, BiodataField.PHONE, "98765") }
        assertTrue(vm.state.value.export is JobState.Idle)
    }

    @Test
    fun `the live preview follows edits`() = runTest(dispatcher) {
        val vm = editorWith()
        addPhoto(vm)
        vm.onPreview(true)
        advanceUntilIdle()
        assertNotNull(vm.state.value.previewFile)
        val before = exports
        vm.edit { BiodataEdits.setValue(it, BiodataField.HEIGHT, "5 ft 6 in") }
        advanceTimeBy(BiodataViewModel.PREVIEW_DELAY_MS / 2)
        assertEquals(before, exports, "a preview waits for typing to pause")
        advanceUntilIdle()
        assertEquals(before + 1, exports)
        assertNotNull(vm.state.value.previewFile)
    }

    @Test
    fun `back walks preview, editor, designs, then leaves`() = runTest(dispatcher) {
        val vm = editorWith()
        vm.onPreview(true)
        assertTrue(vm.onBack())
        assertFalse(vm.state.value.previewing)
        assertTrue(vm.onBack())
        advanceUntilIdle()
        assertEquals(BiodataPage.NONE, vm.state.value.page)
        assertEquals(1, vm.state.value.drafts.size)
        assertFalse(vm.onBack())
        vm.onOpenTemplates(restyle = false)
        assertTrue(vm.onBack())
        assertEquals(BiodataPage.NONE, vm.state.value.page)
    }

    @Test
    fun `changing the design keeps every field`() = runTest(dispatcher) {
        val vm = editorWith()
        vm.edit { BiodataEdits.setValue(it, BiodataField.GOTRA, "Kashyap") }
        vm.onOpenTemplates(restyle = true)
        vm.onTemplateChosen(BiodataTemplates.MODERN)
        val biodata = checkNotNull(vm.state.value.biodata)
        assertEquals(BiodataTemplates.MODERN.id, biodata.templateId)
        assertEquals("Kashyap", biodata.value(BiodataField.GOTRA))
        assertEquals(BiodataPage.EDITOR, vm.state.value.page)
    }
}
