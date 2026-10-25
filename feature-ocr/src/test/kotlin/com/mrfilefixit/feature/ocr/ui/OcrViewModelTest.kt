package com.mrfilefixit.feature.ocr.ui

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
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.ocr.ExportOcrUseCase
import com.mrfilefixit.core.domain.ocr.OcrExportFormat
import com.mrfilefixit.core.domain.ocr.OcrExporter
import com.mrfilefixit.core.domain.ocr.OcrPage
import com.mrfilefixit.core.domain.ocr.OcrProcessor
import com.mrfilefixit.core.domain.ocr.OcrResult
import com.mrfilefixit.core.domain.ocr.OcrScript
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.resume.DocumentExporter
import com.mrfilefixit.core.domain.scan.RecognizeTextUseCase
import com.mrfilefixit.core.domain.security.InputLimits
import java.io.File
import kotlin.io.path.createTempDirectory
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OcrViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private class Ocr : OcrProcessor {
        var asked: OcrScript? = null
        var sources: List<FileRef> = emptyList()

        override suspend fun recognize(
            sources: List<FileRef>,
            script: OcrScript,
            progress: ProgressReporter,
        ): AppResult<OcrResult> {
            asked = script
            this.sources = sources
            val page = OcrPage(0, 0, 100, 100, "नमस्ते दुनिया")
            return success(OcrResult(page.text, 1, listOf(page), OcrScript.DEVANAGARI))
        }
    }

    private class Exports : OcrExporter, DocumentExporter {
        val formats = mutableListOf<String>()

        private fun out(dir: String, name: String, ext: String, mime: String) =
            success(OutputFile("$dir/$name.$ext", "$name.$ext", mime, 10))

        override suspend fun writeText(text: String, outputDir: String, baseName: String): AppResult<OutputFile> =
            out(outputDir, baseName, "txt", "text/plain").also { formats += "txt" }

        override suspend fun searchablePdf(
            sources: List<FileRef>,
            result: OcrResult,
            outputDir: String,
            baseName: String,
            progress: ProgressReporter,
        ): AppResult<OutputFile> = out(outputDir, baseName, "pdf", "application/pdf").also { formats += "searchable" }

        override suspend fun export(
            document: RichDocument,
            format: DocumentFormat,
            outputDir: String,
            baseName: String,
            progress: ProgressReporter,
        ): AppResult<OutputFile> =
            out(outputDir, baseName, format.extension, format.mimeType).also { formats += format.extension }

        override suspend fun readEmbeddedData(source: FileRef): AppResult<String?> = success(null)
    }

    private class Files : FileRepository, TempFileStore {
        val deleted = mutableListOf<String>()
        val saved = mutableListOf<String>()

        override suspend fun describe(uri: String): AppResult<FileRef> = if (uri.startsWith("content://")) {
            success(FileRef(uri, "letter.jpg", "image/jpeg", 100))
        } else {
            failure(AppError.FileNotFound)
        }
        override suspend fun saveTo(output: OutputFile, destination: DestinationUri): AppResult<Unit> =
            success(Unit).also { saved += destination.value }
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

    private val ocr = Ocr()
    private val exports = Exports()
    private val files = Files()

    private fun TestScope.viewModel(uris: List<String> = emptyList()): OcrViewModel {
        var n = 0
        return OcrViewModel(
            initialUris = uris,
            describeFile = DescribeFileUseCase(files),
            recognizeText = RecognizeTextUseCase(ocr, InputLimits()),
            exportOcr = ExportOcrUseCase(exports, exports),
            saveOutput = SaveOutputUseCase(files),
            shareOutput = ShareOutputUseCase(files),
            tempFiles = files,
            ids = JobIdGenerator { "job-${n++}" },
            appScope = this,
        )
    }

    @Test
    fun `picked files are recognized with auto-detect by default and report the script used`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onFilesPicked(listOf("content://picked/1"))
            advanceUntilIdle()
            vm.onRecognize()
            advanceUntilIdle()
            assertEquals(OcrScript.AUTO, ocr.asked)
            assertEquals(OcrScript.DEVANAGARI, vm.state.value.result?.script)
        }

    @Test
    fun `scan pages handed over as job files load without the picker`() = runTest(dispatcher) {
        val root = createTempDirectory("cache").toFile()
        val dir = root.resolve("cache/jobs/scan").apply { mkdirs() }
        val page = File(dir, "scan-1.jpg").apply { writeBytes(ByteArray(5)) }
        val vm = viewModel(listOf("file://${page.path}", "file:///etc/passwd"))
        advanceUntilIdle()
        // A file outside the job cache is refused, so nothing loads.
        assertEquals(AppError.FileNotFound, vm.state.value.inputError)

        val ok = viewModel(listOf("file://${page.path}"))
        advanceUntilIdle()
        val file = ok.state.value.files.single()
        assertEquals("image/jpeg", file.mimeType)
        assertEquals(5L, file.sizeBytes)
        root.deleteRecursively()
    }

    @Test
    fun `each export replaces the last and its job folder is removed`() = runTest(dispatcher) {
        val vm = viewModel(listOf("content://picked/1"))
        advanceUntilIdle()
        vm.onRecognize()
        advanceUntilIdle()
        vm.onExport(OcrExportFormat.SEARCHABLE_PDF)
        advanceUntilIdle()
        assertEquals("letter text.pdf", vm.state.value.exported?.displayName)
        vm.onExport(OcrExportFormat.DOCX)
        advanceUntilIdle()
        assertEquals(listOf("searchable", "docx"), exports.formats)
        assertEquals(OcrExportFormat.DOCX, vm.state.value.exportFormat)
        assertTrue("job-0" in files.deleted)
        vm.onSave("content://dest/1")
        advanceUntilIdle()
        assertEquals(listOf("content://dest/1"), files.saved)
    }

    @Test
    fun `changing the language drops the old result`() = runTest(dispatcher) {
        val vm = viewModel(listOf("content://picked/1"))
        advanceUntilIdle()
        vm.onRecognize()
        advanceUntilIdle()
        vm.onScriptChanged(OcrScript.KOREAN)
        advanceUntilIdle()
        assertNull(vm.state.value.result)
        assertEquals(JobState.Idle, vm.state.value.ocr)
        assertEquals(OcrScript.KOREAN, vm.state.value.script)
    }
}
