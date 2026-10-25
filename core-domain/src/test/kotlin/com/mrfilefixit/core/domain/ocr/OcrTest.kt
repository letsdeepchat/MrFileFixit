package com.mrfilefixit.core.domain.ocr

import com.mrfilefixit.core.domain.convert.DocumentFormat
import com.mrfilefixit.core.domain.document.DocPageBreak
import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.resume.DocumentExporter
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScriptDetectorTest {
    private val english = "The quick brown fox jumps over the lazy dog. Invoice number 2045."

    @Test
    fun `plain English stays Latin even though every recognizer reads some of it`() {
        val samples = OcrScript.recognizable.associateWith { english }
        assertEquals(OcrScript.LATIN, ScriptDetector.pick(samples))
    }

    @Test
    fun `Hindi text picks Devanagari`() {
        val samples = mapOf(
            OcrScript.LATIN to "Hl ah",
            OcrScript.DEVANAGARI to "भारत एक विशाल देश है। यहाँ अनेक भाषाएँ बोली जाती हैं।",
            OcrScript.CHINESE to "",
            OcrScript.JAPANESE to "",
            OcrScript.KOREAN to "",
        )
        assertEquals(OcrScript.DEVANAGARI, ScriptDetector.pick(samples))
    }

    @Test
    fun `Japanese needs kana, so Han-only text is Chinese`() {
        val chinese = "我们今天在北京开会讨论新的计划"
        val japanese = "私たちは今日東京で会議を開きました"
        val chineseSamples = mapOf(OcrScript.CHINESE to chinese, OcrScript.JAPANESE to chinese)
        assertEquals(OcrScript.CHINESE, ScriptDetector.pick(chineseSamples))
        val japaneseSamples = mapOf(OcrScript.CHINESE to "今日東京会議", OcrScript.JAPANESE to japanese)
        assertEquals(OcrScript.JAPANESE, ScriptDetector.pick(japaneseSamples))
    }

    @Test
    fun `Korean picks Korean`() {
        val samples = mapOf(OcrScript.LATIN to "", OcrScript.KOREAN to "대한민국의 수도는 서울입니다", OcrScript.CHINESE to "")
        assertEquals(OcrScript.KOREAN, ScriptDetector.pick(samples))
    }

    @Test
    fun `a few stray native characters in a Latin page do not win`() {
        val samples = mapOf(OcrScript.LATIN to english, OcrScript.DEVANAGARI to "$english क ख ग घ ङ च छ")
        assertEquals(OcrScript.LATIN, ScriptDetector.pick(samples))
    }

    @Test
    fun `no samples falls back to Latin`() {
        assertEquals(OcrScript.LATIN, ScriptDetector.pick(emptyMap()))
    }
}

class ExportOcrUseCaseTest {
    private val output = OutputFile("/jobs/1/out", "out", "application/pdf", 1)
    private val calls = mutableListOf<String>()
    private var document: RichDocument? = null
    private val ocrExporter = object : OcrExporter {
        override suspend fun writeText(text: String, outputDir: String, baseName: String): AppResult<OutputFile> {
            calls += "text:$baseName:$text"
            return success(output)
        }

        override suspend fun searchablePdf(
            sources: List<FileRef>,
            result: OcrResult,
            outputDir: String,
            baseName: String,
            progress: ProgressReporter,
        ): AppResult<OutputFile> {
            calls += "searchable:$baseName:${sources.size}:${result.pages.size}"
            return success(output)
        }
    }
    private val documents = object : DocumentExporter {
        override suspend fun export(
            document: RichDocument,
            format: DocumentFormat,
            outputDir: String,
            baseName: String,
            progress: ProgressReporter,
        ): AppResult<OutputFile> {
            this@ExportOcrUseCaseTest.document = document
            calls += "document:$format:$baseName"
            return success(output)
        }

        override suspend fun readEmbeddedData(source: FileRef): AppResult<String?> = success(null)
    }
    private val export = ExportOcrUseCase(ocrExporter, documents)
    private val source = FileRef("content://x/1", "Bill.jpg", "image/jpeg", 10)
    private val page = OcrPage(0, 0, 100, 100, "Line one\nLine two\n\nNext block", emptyList())
    private val result = OcrResult(page.text, 2, listOf(page, page.copy(pageIndex = 1, text = "Page two")))

    @Test
    fun `searchable PDF goes to the OCR exporter with a name from the first file`() = runTest {
        assertEquals(success(output), export(listOf(source), result, OcrExportFormat.SEARCHABLE_PDF, "/jobs/1"))
        assertEquals(listOf("searchable:Bill text:1:2"), calls)
    }

    @Test
    fun `Word export builds one paragraph per line and a page break per page`() = runTest {
        export(listOf(source), result, OcrExportFormat.DOCX, "/jobs/1")
        assertEquals(listOf("document:DOCX:Bill text"), calls)
        val blocks = checkNotNull(document).blocks
        assertEquals(
            listOf("Line one", "Line two", "Next block", "Page two"),
            blocks.filterIsInstance<DocParagraph>().map { it.plainText },
        )
        assertEquals(1, blocks.count { it == DocPageBreak })
        val (first, second) = blocks.filterIsInstance<DocParagraph>()
        assertTrue(second.style.spaceAfterPt > first.style.spaceAfterPt)
    }

    @Test
    fun `text PDF uses the document exporter as a PDF`() = runTest {
        export(listOf(source), result, OcrExportFormat.TEXT_PDF, "/jobs/1")
        assertEquals(listOf("document:PDF:Bill text"), calls)
    }

    @Test
    fun `text export writes the whole text`() = runTest {
        export(listOf(source), result, OcrExportFormat.TXT, "/jobs/1")
        assertEquals(listOf("text:Bill text:${result.text}"), calls)
    }

    @Test
    fun `blank text or missing pages cannot be exported`() = runTest {
        assertEquals(
            failure(AppError.EmptyFile),
            export(listOf(source), result.copy(text = " "), OcrExportFormat.TXT, "/jobs/1"),
        )
        val noPages = export(listOf(source), result.copy(pages = emptyList()), OcrExportFormat.SEARCHABLE_PDF, "/d")
        assertTrue(noPages is Result.Failure)
        assertTrue(calls.isEmpty())
    }
}
