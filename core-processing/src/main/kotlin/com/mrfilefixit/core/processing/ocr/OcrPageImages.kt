package com.mrfilefixit.core.processing.ocr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.security.InputLimits
import com.mrfilefixit.core.processing.image.ImageDecoding
import com.mrfilefixit.core.processing.image.use
import com.mrfilefixit.core.processing.io.InputRejectedException
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * One page as an upright bitmap of at most [OcrPageImages.MAX_SIDE] px on the long side. [sizePt] is the
 * page size of a PDF page in points, and null for images.
 */
internal class PageImage(
    val index: Int,
    val count: Int,
    val bitmap: Bitmap,
    val sizePt: Pair<Float, Float>?,
)

/**
 * Turns OCR inputs into page bitmaps one at a time, so OCR and the searchable PDF writer see exactly the same
 * pixels: images are decoded with EXIF rotation applied, PDFs are rendered page by page on white.
 */
internal class OcrPageImages @Inject constructor(
    private val resolver: ContentResolver,
    private val decoding: ImageDecoding,
    private val limits: InputLimits,
) {
    /** Calls [block] for each page of [source]; the bitmap is recycled as soon as [block] returns. */
    suspend fun <T> forEachPage(source: FileRef, block: suspend (PageImage) -> T): List<T> =
        if (source.mimeType == PDF_MIME) pdfPages(source, block) else listOf(imagePage(source, block))

    private suspend fun <T> imagePage(source: FileRef, block: suspend (PageImage) -> T): T {
        val raw = decoding.readRawInfo(source.uri)
        // At most MAX_SIDE on the long side: enough for ML Kit, small enough for low-memory phones.
        var sample = 1
        while (maxOf(raw.width, raw.height) / sample > MAX_SIDE) sample *= 2
        val decoded = decoding.decodeRegion(source.uri, raw, NormalizedRect.FULL, sample)
        val upright = if (raw.exifRotation == 0 && !raw.flipped) {
            decoded
        } else {
            val matrix = Matrix().apply {
                postRotate(raw.exifRotation.toFloat())
                if (raw.flipped) postScale(-1f, 1f)
            }
            decoded.use { Bitmap.createBitmap(it, 0, 0, it.width, it.height, matrix, true) }
        }
        return upright.use { block(PageImage(0, 1, it, null)) }
    }

    private suspend fun <T> pdfPages(source: FileRef, block: suspend (PageImage) -> T): List<T> {
        val descriptor = resolver.openFileDescriptor(source.uri.toUri(), "r") ?: throw FileNotFoundException()
        return descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                val pageCount = renderer.pageCount
                val check = limits.checkPageCount(pageCount)
                if (check is Result.Failure) throw InputRejectedException(check.error)
                if (pageCount == 0) throw InputRejectedException(AppError.EmptyFile)
                (0 until pageCount).map { index ->
                    coroutineContext.ensureActive()
                    renderer.openPage(index).use { page ->
                        val scale = MAX_SIDE.toFloat() / maxOf(page.width, page.height)
                        val bitmap = createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                        )
                        bitmap.use {
                            it.eraseColor(Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            block(PageImage(index, pageCount, it, page.width.toFloat() to page.height.toFloat()))
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val MAX_SIDE = 2048
        const val PDF_MIME = "application/pdf"
    }
}
