package com.mrfilefixit.core.processing.convert

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mrfilefixit.core.domain.biodata.Biodata
import com.mrfilefixit.core.domain.biodata.BiodataEdits
import com.mrfilefixit.core.domain.biodata.BiodataField
import com.mrfilefixit.core.domain.biodata.BiodataKind
import com.mrfilefixit.core.domain.biodata.BiodataLabels
import com.mrfilefixit.core.domain.biodata.BiodataPhoto
import com.mrfilefixit.core.domain.biodata.BiodataRenderer
import com.mrfilefixit.core.domain.biodata.BiodataTemplate
import com.mrfilefixit.core.domain.biodata.BiodataTemplates
import com.mrfilefixit.core.domain.convert.DocumentFormat
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.security.InputLimits
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** All five biodata designs export with one to five photos, as a PDF with selectable text and as a DOCX. */
@RunWith(AndroidJUnit4::class)
class BiodataExportTest {
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
    private val today = LocalDate.of(2026, 10, 27)

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "biodata-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun jpeg(width: Int, height: Int, color: Int): ByteArray {
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    private fun filled(template: BiodataTemplate, photos: Int): Biodata {
        var b = Biodata("b1", template.id, name = "Asha Verma")
        val values = if (template.kind == BiodataKind.MARRIAGE) {
            mapOf(
                BiodataField.DATE_OF_BIRTH to "14-02-1998",
                BiodataField.PLACE_OF_BIRTH to "Jaipur",
                BiodataField.RELIGION to "Hindu",
                BiodataField.GOTRA to "Kashyap",
                BiodataField.QUALIFICATION to "M.Tech",
                BiodataField.FATHER_NAME to "Suresh Verma",
                BiodataField.PARTNER_EXPECTATIONS to "Kind and family oriented.",
                BiodataField.PHONE to "+91 98765 43210",
            )
        } else {
            mapOf(
                BiodataField.BIO to "Ten years of telling stories with light.",
                BiodataField.SERVICES to "Weddings\nPortraits",
                BiodataField.CLIENTS to "Taj Hotels",
                BiodataField.EMAIL to "asha@example.com",
            )
        }
        values.forEach { (field, value) -> b = BiodataEdits.setValue(b, field, value) }
        // Mixed shapes: portrait, square and landscape photos all have to fit their frames.
        val shapes = listOf(900 to 1200, 1000 to 1000, 1600 to 900)
        val list = (1..photos).map { i ->
            val (width, height) = shapes[i % shapes.size]
            BiodataPhoto("p$i", width, height)
        }
        return b.copy(photos = list)
    }

    @Test
    fun everyDesignExportsWithOneToFivePhotos() = runBlocking {
        BiodataTemplates.all.forEach { template ->
            (Biodata.MIN_PHOTOS..Biodata.MAX_PHOTOS).forEach { count ->
                val biodata = filled(template, count)
                val bytes = biodata.photos.mapIndexed { i, p -> jpeg(p.widthPx / 4, p.heightPx / 4, PHOTO_COLORS[i]) }
                val document = BiodataRenderer.render(biodata, template, bytes, BiodataLabels.English, today)
                val name = "${template.id}-$count"

                val pdf = (exporter.export(document, DocumentFormat.PDF, dir.path, name) as Result.Success).value
                assertTrue(name, File(pdf.path).length() > 0 && pdf.sizeBytes > 0)
                val text = PDDocument.load(File(pdf.path)).use { doc ->
                    assertTrue(name, doc.numberOfPages >= 1)
                    PDFTextStripper().getText(doc)
                }
                assertTrue(name, "Asha Verma" in text)

                val docx = (exporter.export(document, DocumentFormat.DOCX, dir.path, name) as Result.Success).value
                assertTrue(name, docx.sizeBytes > 0)
                ZipFile(docx.path).use { zip ->
                    val media = zip.entries().toList().count { it.name.startsWith("word/media/") }
                    assertEquals(name, count, media)
                    val body = zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().readText()
                    assertTrue(name, "Asha Verma" in body)
                }
            }
        }
    }

    private companion object {
        val PHOTO_COLORS = listOf(
            Color.LTGRAY,
            Color.rgb(200, 150, 120),
            Color.DKGRAY,
            Color.rgb(90, 120, 160),
            Color.GRAY,
        )
    }
}
