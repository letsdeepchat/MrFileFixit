package com.mrfilefixit.core.processing.convert

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mrfilefixit.core.domain.convert.DocumentFormat
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.resume.ResumeFactory
import com.mrfilefixit.core.domain.resume.ResumeJson
import com.mrfilefixit.core.domain.resume.ResumeRenderer
import com.mrfilefixit.core.domain.resume.ResumeTemplates
import com.mrfilefixit.core.domain.security.InputLimits
import com.mrfilefixit.core.office.docx.DocxWriter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Every resume template exports to a PDF with selectable text and to a DOCX that re-opens as the same draft. */
@RunWith(AndroidJUnit4::class)
class ResumeExportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private val dispatchers = object : DispatcherProvider {
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val main: CoroutineDispatcher = Dispatchers.Main
    }
    private val exporter by lazy {
        AndroidDocumentExporter(context, context.contentResolver, InputLimits.DEFAULT, dispatchers)
    }

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "resume-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun photoJpeg(): ByteArray {
        val bitmap = createBitmap(413, 531, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    @Test
    fun everyTemplateExportsAReadablePdf() = runBlocking {
        var n = 0
        val photo = photoJpeg()
        ResumeTemplates.all.forEach { template ->
            val resume = ResumeFactory.fromTemplate(template, template.id, 0L) { "e${n++}" }
            val document = ResumeRenderer.render(resume, template, photo.takeIf { template.photo })
            val result = exporter.export(document, DocumentFormat.PDF, dir.path, template.id)
            val output = (result as Result.Success).value
            assertTrue(template.id, (output.pageCount ?: 0) in 1..2)
            val text = PDDocument.load(File(output.path)).use { PDFTextStripper().getText(it) }
            assertTrue(template.id, resume.contact.fullName in text)
            resume.sections.forEach { section ->
                assertTrue(
                    "${template.id}: ${section.title}",
                    section.title in text || section.title.uppercase() in text,
                )
            }
        }
    }

    @Test
    fun docxExportReopensAsTheSameDraft() = runBlocking {
        var n = 0
        val template = ResumeTemplates.byId("DATA_SCIENCE")
        val resume = ResumeFactory.fromTemplate(template, "r1", 5L) { "e${n++}" }
        val output = (
            exporter.export(
                ResumeRenderer.render(resume, template),
                DocumentFormat.DOCX,
                dir.path,
                "cv",
            ) as Result.Success
            ).value
        val ref = FileRef(
            Uri.fromFile(File(output.path)).toString(),
            output.displayName,
            output.mimeType,
            output.sizeBytes,
        )
        val data = (exporter.readEmbeddedData(ref) as Result.Success).value
        assertEquals(resume, data?.let(ResumeJson::decode))
    }

    @Test
    fun filesWithoutADraftHaveNoEmbeddedData() = runBlocking {
        val text = File(dir, "notes.docx").apply { writeText("plain text") }
        val plainDocx = File(dir, "plain.docx")
        plainDocx.outputStream().use { DocxWriter().write(RichDocument(listOf(DocParagraph.of("Hello"))), it) }
        listOf(text, plainDocx).forEach { file ->
            val ref = FileRef(Uri.fromFile(file).toString(), file.name, DocumentFormat.DOCX.mimeType, file.length())
            assertNull(file.name, (exporter.readEmbeddedData(ref) as Result.Success).value)
        }
    }
}
