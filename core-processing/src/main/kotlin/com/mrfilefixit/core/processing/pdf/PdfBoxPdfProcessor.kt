package com.mrfilefixit.core.processing.pdf

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.ImagePlan
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.domain.image.OutputGeometry
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.job.step
import com.mrfilefixit.core.domain.pdf.PdfInfo
import com.mrfilefixit.core.domain.pdf.PdfOperation
import com.mrfilefixit.core.domain.pdf.PdfProcessor
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.runCatchingAppFlat
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.security.InputLimits
import com.mrfilefixit.core.processing.image.ImageDecoding
import com.mrfilefixit.core.processing.image.ImageEncoding
import com.mrfilefixit.core.processing.image.ImageRendering
import com.mrfilefixit.core.processing.image.use
import com.mrfilefixit.core.processing.io.InputRejectedException
import com.mrfilefixit.core.processing.io.mapEngineError
import com.mrfilefixit.core.processing.io.newOutputFile
import com.mrfilefixit.core.processing.io.openLimited
import com.mrfilefixit.core.processing.io.throwIfFailure
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * PDF engine. Structure operations (merge, split, pages, rotate, encrypt, compress) use pdfbox-android
 * with temp-file-backed memory; rendering (to-images) uses the platform [PdfRenderer], one page at a time.
 *
 * Every source is first copied into the job directory (bounded by [InputLimits.maxFileBytes]) so both
 * libraries get a seekable file, and so the user's original is never opened for writing.
 */
internal class PdfBoxPdfProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: ContentResolver,
    private val decoding: ImageDecoding,
    private val encoding: ImageEncoding,
    private val limits: InputLimits,
    private val dispatchers: DispatcherProvider,
) : PdfProcessor {

    private val pdfBoxReady by lazy { PDFBoxResourceLoader.init(context) }

    override suspend fun info(source: FileRef): AppResult<PdfInfo> = withContext(dispatchers.io) {
        runCatchingAppFlat(::mapEngineError) {
            limits.checkFileSize(source.sizeBytes).throwIfFailure()
            resolver.openFileDescriptor(source.uri.toUri(), "r")?.use { fd ->
                try {
                    PdfRenderer(fd).use {
                        success(
                            PdfInfo(
                                it.pageCount,
                                encrypted = false,
                                sizeBytes =
                                source.sizeBytes ?: 0,
                            ),
                        )
                    }
                } catch (_: SecurityException) {
                    success(PdfInfo(pageCount = 0, encrypted = true, sizeBytes = source.sizeBytes ?: 0))
                }
            } ?: failure(AppError.FileNotFound)
        }
    }

    override suspend fun run(operation: PdfOperation, progress: ProgressReporter): AppResult<List<OutputFile>> =
        withContext(dispatchers.default) {
            runCatchingAppFlat(::mapPdfError) {
                pdfBoxReady
                File(operation.outputDir).mkdirs()
                val processor = this@PdfBoxPdfProcessor
                val outputs = when (operation) {
                    is PdfOperation.Merge -> listOf(merge(operation, progress))
                    is PdfOperation.Split -> split(operation, progress)
                    is PdfOperation.SelectPages -> listOf(selectPages(operation))
                    is PdfOperation.Rotate -> listOf(rotate(operation))
                    is PdfOperation.Compress -> listOf(compress(operation, progress))
                    is PdfOperation.ImagesToPdf -> listOf(imagesToPdf(operation, progress))
                    is PdfOperation.ToImages -> toImages(operation, progress)
                    is PdfOperation.Watermark -> listOf(PdfStamping.watermark(processor, operation, progress))
                    is PdfOperation.PageNumbers -> listOf(PdfStamping.pageNumbers(processor, operation, progress))
                    is PdfOperation.Protect -> listOf(protect(operation))
                    is PdfOperation.Unlock -> listOf(unlock(operation))
                }
                progress.report(1f)
                success(outputs)
            }
        }

    // region structure operations

    private suspend fun merge(op: PdfOperation.Merge, progress: ProgressReporter): OutputFile {
        limits.checkFileCount(op.sources.size).throwIfFailure()
        val copies = op.sources.mapIndexed { index, source ->
            coroutineContext.ensureActive()
            progress.step(index, op.sources.size * 2)
            copyToJob(source, op.outputDir, "merge-src-$index")
        }
        var totalPages = 0
        copies.forEach { file -> openDocument(file, null).use { totalPages += it.numberOfPages } }
        limits.checkPageCount(totalPages).throwIfFailure()
        val out = newOutputFile(op.outputDir, op.outputBaseName, PDF_EXT)
        PDFMergerUtility().apply {
            copies.forEach { addSource(it) }
            destinationFileName = out.absolutePath
        }.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        copies.forEach { it.delete() }
        return out.toOutput(totalPages)
    }

    private suspend fun split(op: PdfOperation.Split, progress: ProgressReporter): List<OutputFile> {
        val src = copyToJob(op.source, op.outputDir, "split-src")
        return openDocument(src, null).use { doc ->
            val pageCount = doc.numberOfPages
            if (op.ranges.any {
                    it.first < 1 || it.last > pageCount
                }
            ) {
                throw InputRejectedException(AppError.InvalidPageSelection(pageCount))
            }
            op.ranges.mapIndexed { index, range ->
                coroutineContext.ensureActive()
                progress.step(index, op.ranges.size)
                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { part ->
                    range.forEach { page -> part.importPage(doc.getPage(page - 1)) }
                    val out = newOutputFile(op.outputDir, "${op.outputBaseName}-${range.first}-${range.last}", PDF_EXT)
                    part.save(out)
                    out.toOutput(range.count())
                }
            }
        }.also { src.delete() }
    }

    /** Extract, reorder and delete are all "keep these pages in this order". Done in place on a copy. */
    private fun selectPages(op: PdfOperation.SelectPages): OutputFile {
        val src = copyToJob(op.source, op.outputDir, "select-src")
        return openDocument(src, null).use { doc ->
            val pageCount = doc.numberOfPages
            if (op.pages.isEmpty() || op.pages.any { it !in 1..pageCount }) {
                throw InputRejectedException(AppError.InvalidPageSelection(pageCount))
            }
            val original = (0 until pageCount).map { doc.getPage(it) }
            val keep = op.pages.distinct().map { original[it - 1] }
            original.forEach { doc.removePage(it) }
            keep.forEach { doc.addPage(it) }
            val out = newOutputFile(op.outputDir, op.outputBaseName, PDF_EXT)
            doc.save(out)
            out.toOutput(keep.size)
        }.also { src.delete() }
    }

    private fun rotate(op: PdfOperation.Rotate): OutputFile {
        val src = copyToJob(op.source, op.outputDir, "rotate-src")
        return openDocument(src, null).use { doc ->
            val targets = op.pages.ifEmpty { (1..doc.numberOfPages).toSet() }
            targets.forEach { n ->
                val page = doc.getPage(n - 1)
                page.rotation = ((page.rotation + op.degrees) % FULL_TURN + FULL_TURN) % FULL_TURN
            }
            val out = newOutputFile(op.outputDir, op.outputBaseName, PDF_EXT)
            doc.save(out)
            out.toOutput(doc.numberOfPages)
        }.also { src.delete() }
    }

    private fun protect(op: PdfOperation.Protect): OutputFile {
        val src = copyToJob(op.source, op.outputDir, "protect-src")
        return openDocument(src, null).use { doc ->
            // PDFBox needs Strings; they are dropped as soon as the document is written. Nothing is stored.
            val policy = StandardProtectionPolicy(String(op.ownerPassword), String(op.userPassword), AccessPermission())
            policy.encryptionKeyLength = AES_256
            doc.protect(policy)
            val out = newOutputFile(op.outputDir, op.outputBaseName, PDF_EXT)
            doc.save(out)
            out.toOutput(doc.numberOfPages)
        }.also { src.delete() }
    }

    private fun unlock(op: PdfOperation.Unlock): OutputFile {
        val src = copyToJob(op.source, op.outputDir, "unlock-src")
        return openDocument(src, String(op.password)).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            val out = newOutputFile(op.outputDir, op.outputBaseName, PDF_EXT)
            doc.save(out)
            out.toOutput(doc.numberOfPages)
        }.also { src.delete() }
    }

    // endregion

    // region rendering operations (PdfRenderer, one page at a time)

    /** See [PdfCompressor]: images are downsampled, text stays text, and the result is always smaller. */
    private suspend fun compress(op: PdfOperation.Compress, progress: ProgressReporter): OutputFile {
        val src = copyToJob(op.source, op.outputDir, "compress-src")
        try {
            val out = newOutputFile(op.outputDir, op.outputBaseName, PDF_EXT)
            PdfCompressor(limits) { openDocument(it, null) }.compress(src, op.level, op.grayscale, out, progress)
            val pages = ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { it.pageCount }
            }
            return out.toOutput(pages)
        } finally {
            src.delete()
        }
    }

    private suspend fun toImages(op: PdfOperation.ToImages, progress: ProgressReporter): List<OutputFile> {
        val src = copyToJob(op.source, op.outputDir, "render-src")
        val wanted = op.pages.toSet()
        val outputs = mutableListOf<OutputFile>()
        forEachRenderedPage(src, op.dpi, progress, include = {
            it + 1 in wanted || wanted.isEmpty()
        }) { index, bitmap, _, _ ->
            val bytes = encoding.encode(bitmap, op.format, IMAGE_QUALITY, File(op.outputDir))
            val file = newOutputFile(op.outputDir, "${op.outputBaseName}-${index + 1}", op.format.extension)
            file.writeBytes(bytes)
            outputs +=
                OutputFile(file.absolutePath, file.name, op.format.mimeType, file.length(), bitmap.width, bitmap.height)
        }
        src.delete()
        return outputs
    }

    private suspend fun imagesToPdf(op: PdfOperation.ImagesToPdf, progress: ProgressReporter): OutputFile {
        limits.checkFileCount(op.images.size).throwIfFailure()
        val out = newOutputFile(op.outputDir, op.outputBaseName, PDF_EXT)
        PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            op.images.forEachIndexed { index, source ->
                coroutineContext.ensureActive()
                limits.checkFileSize(source.sizeBytes).throwIfFailure()
                val raw = decoding.readRawInfo(source.uri)
                var sample = 1
                while (maxOf(raw.width, raw.height) / sample > MAX_IMAGE_SIDE) sample *= 2
                val upright = decoding.decodeRegion(source.uri, raw, NormalizedRect.FULL, sample).use { region ->
                    val turned = raw.exifRotation % HALF_TURN != 0
                    val geometry = OutputGeometry.compute(
                        if (turned) region.height else region.width,
                        if (turned) region.width else region.height,
                        ImagePlan(0, NormalizedRect.FULL, null, null),
                    )
                    ImageRendering.render(
                        region,
                        raw.exifRotation,
                        raw.flipped,
                        0,
                        geometry,
                        ImageFormat.JPEG,
                        Color.WHITE,
                    )
                }
                upright.use { bitmap ->
                    val pageBox = PdfLayout.pageBox(op.pageSize, bitmap.width, bitmap.height, op.orientation)
                    val page = PDPage(PDRectangle(pageBox.width, pageBox.height))
                    doc.addPage(page)
                    val draw = PdfLayout.fit(bitmap.width, bitmap.height, pageBox.width, pageBox.height, op.marginPt)
                    val image = JPEGFactory.createFromImage(doc, bitmap, IMAGE_TO_PDF_QUALITY)
                    PDPageContentStream(doc, page).use { it.drawImage(image, draw.x, draw.y, draw.width, draw.height) }
                }
                progress.step(index, op.images.size)
            }
            doc.save(out)
            return out.toOutput(doc.numberOfPages)
        }
    }

    private suspend fun forEachRenderedPage(
        file: File,
        dpi: Int,
        progress: ProgressReporter,
        include: (Int) -> Boolean = { true },
        block: (index: Int, bitmap: Bitmap, widthPt: Float, heightPt: Float) -> Unit,
    ) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val renderer = try {
                PdfRenderer(fd)
            } catch (_: SecurityException) {
                throw InputRejectedException(AppError.PasswordRequired)
            }
            renderer.use {
                limits.checkPageCount(it.pageCount).throwIfFailure()
                for (index in 0 until it.pageCount) {
                    coroutineContext.ensureActive()
                    if (!include(index)) continue
                    it.openPage(index).use { page ->
                        val scale = dpi / POINTS_PER_INCH
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        limits.checkDimensions(w, h).throwIfFailure()
                        createBitmap(w, h).use { bitmap ->
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            block(index, bitmap, page.width.toFloat(), page.height.toFloat())
                        }
                    }
                    progress.step(index, it.pageCount)
                }
            }
        }
    }

    // endregion

    internal fun copyToJob(source: FileRef, dir: String, name: String): File {
        limits.checkFileSize(source.sizeBytes).throwIfFailure()
        val target = File(dir, "$name.tmp")
        resolver.openLimited(source.uri, limits.maxFileBytes).use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        return target
    }

    internal fun openDocument(file: File, password: String?): PDDocument {
        val doc = try {
            PDDocument.load(file, password.orEmpty(), MemoryUsageSetting.setupTempFileOnly())
        } catch (e: InvalidPasswordException) {
            throw if (password == null) InputRejectedException(AppError.PasswordRequired) else e
        }
        if (password == null && doc.isEncrypted) {
            doc.close()
            throw InputRejectedException(AppError.PasswordRequired)
        }
        val check = limits.checkPageCount(doc.numberOfPages)
        if (check is Result.Failure) {
            doc.close()
            throw InputRejectedException(check.error)
        }
        return doc
    }

    private fun mapPdfError(throwable: Throwable): AppError =
        if (throwable is InvalidPasswordException) AppError.WrongPassword else mapEngineError(throwable)

    private fun File.toOutput(pages: Int) = OutputFile(absolutePath, name, PDF_MIME, length(), pageCount = pages)

    internal companion object {
        const val PDF_EXT = "pdf"
        const val PDF_MIME = "application/pdf"
        private const val FULL_TURN = 360
        private const val HALF_TURN = 180
        private const val AES_256 = 256
        private const val POINTS_PER_INCH = 72f
        private const val IMAGE_QUALITY = 90
        private const val IMAGE_TO_PDF_QUALITY = 0.85f
        private const val MAX_IMAGE_SIDE = 2480 // A4 at 300 dpi
    }
}
