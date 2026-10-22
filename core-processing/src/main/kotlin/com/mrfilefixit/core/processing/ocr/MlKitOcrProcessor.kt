package com.mrfilefixit.core.processing.ocr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.job.step
import com.mrfilefixit.core.domain.ocr.OcrProcessor
import com.mrfilefixit.core.domain.ocr.OcrResult
import com.mrfilefixit.core.domain.ocr.OcrScript
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.runCatchingAppFlat
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.security.InputLimits
import com.mrfilefixit.core.processing.image.ImageDecoding
import com.mrfilefixit.core.processing.image.use
import com.mrfilefixit.core.processing.io.InputRejectedException
import com.mrfilefixit.core.processing.io.mapEngineError
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * On-device OCR with ML Kit's bundled models (no model download, works offline).
 * Images are decoded at most [MAX_SIDE] px on the long side; PDFs are rendered one page at a time.
 */
internal class MlKitOcrProcessor @Inject constructor(
    private val resolver: ContentResolver,
    private val decoding: ImageDecoding,
    private val limits: InputLimits,
    private val dispatchers: DispatcherProvider,
) : OcrProcessor {

    override suspend fun recognize(
        sources: List<FileRef>,
        script: OcrScript,
        progress: ProgressReporter,
    ): AppResult<OcrResult> = withContext(dispatchers.default) {
        runCatchingAppFlat(::mapEngineError) {
            if (sources.isEmpty()) return@runCatchingAppFlat failure(AppError.EmptyFile)
            val recognizer = client(script)
            try {
                val pages = mutableListOf<String>()
                sources.forEachIndexed { index, source ->
                    coroutineContext.ensureActive()
                    val inner = ProgressReporter { p -> progress.step(index, sources.size, p) }
                    if (source.mimeType == PDF_MIME) {
                        pages += recognizePdf(recognizer, source, inner)
                    } else {
                        pages += recognizeImage(recognizer, source)
                        inner.report(1f)
                    }
                }
                success(OcrResult(pages.joinToString(PAGE_SEPARATOR).trim(), pages.size))
            } finally {
                recognizer.close()
            }
        }
    }

    private fun client(script: OcrScript): TextRecognizer = when (script) {
        OcrScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        OcrScript.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    private suspend fun recognizeImage(recognizer: TextRecognizer, source: FileRef): String {
        val raw = decoding.readRawInfo(source.uri)
        // At most MAX_SIDE on the long side: enough for ML Kit, small enough for low-memory phones.
        var sample = 1
        while (maxOf(raw.width, raw.height) / sample > MAX_SIDE) sample *= 2
        return decoding.decodeRegion(source.uri, raw, NormalizedRect.FULL, sample).use { bitmap ->
            recognizer.process(InputImage.fromBitmap(bitmap, raw.exifRotation)).await().text
        }
    }

    private suspend fun recognizePdf(recognizer: TextRecognizer, source: FileRef, progress: ProgressReporter): List<String> {
        val descriptor = resolver.openFileDescriptor(Uri.parse(source.uri), "r") ?: return emptyList()
        return descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                val pageCount = renderer.pageCount
                val check = limits.checkPageCount(pageCount)
                if (check is Result.Failure) throw InputRejectedException(check.error)
                (0 until pageCount).map { index ->
                    coroutineContext.ensureActive()
                    val text = renderer.openPage(index).use { page ->
                        val scale = MAX_SIDE.toFloat() / maxOf(page.width, page.height)
                        val bitmap = Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888,
                        )
                        bitmap.use {
                            it.eraseColor(Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            recognizer.process(InputImage.fromBitmap(it, 0)).await().text
                        }
                    }
                    progress.step(index, pageCount)
                    text
                }
            }
        }
    }

    private companion object {
        const val MAX_SIDE = 2048
        const val PDF_MIME = "application/pdf"
        const val PAGE_SEPARATOR = "\n\n"
    }
}
