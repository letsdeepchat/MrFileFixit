package com.mrfilefixit.core.processing.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.pdf.CompressionLevel
import com.mrfilefixit.core.domain.pdf.PdfCompressionPolicy
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.security.InputLimits
import com.mrfilefixit.core.processing.image.use
import com.mrfilefixit.core.processing.io.InputRejectedException
import com.mrfilefixit.core.processing.io.throwIfFailure
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Makes a PDF smaller without turning text into pictures.
 *
 * 1. Structural pass: every embedded image larger than the level's dpi is downsampled and re-encoded as
 *    JPEG at the level's quality, but only where the new stream is smaller than the old one. Page content
 *    streams stored without compression are Flate-compressed, and metadata, thumbnails and piece info
 *    are dropped.
 * 2. Strong level only, and only if step 1 did not shrink the file: pages are rendered to JPEG one at a
 *    time (the old approach, now a last resort).
 * 3. Guardrail: the smallest candidate that is strictly smaller than the source wins. If none is, every
 *    candidate is deleted and the job fails with [AppError.AlreadyOptimized], so a "compressed" file is
 *    never bigger than the original.
 */
internal class PdfCompressor(private val limits: InputLimits, private val openDocument: (File) -> PDDocument) {

    suspend fun compress(
        source: File,
        level: CompressionLevel,
        grayscale: Boolean,
        output: File,
        progress: ProgressReporter,
    ): File {
        val structural = File(output.parentFile, "${output.name}.structural.tmp")
        val raster = File(output.parentFile, "${output.name}.raster.tmp")
        try {
            optimizeStructure(source, structural, level, grayscale, progress)
            val candidates = mutableListOf(structural to structural.length())
            if (structural.length() >= source.length() && PdfCompressionPolicy.mayRasterize(level)) {
                rasterize(source, raster, level, grayscale, progress)
                candidates += raster to raster.length()
            }
            val winner = PdfCompressionPolicy.smallestBelow(source.length(), candidates)
                ?: throw InputRejectedException(AppError.AlreadyOptimized)
            if (!winner.renameTo(output)) {
                winner.copyTo(output, overwrite = true)
            }
            return output
        } finally {
            structural.delete()
            raster.delete()
        }
    }

    private suspend fun optimizeStructure(
        source: File,
        target: File,
        level: CompressionLevel,
        grayscale: Boolean,
        progress: ProgressReporter,
    ) {
        openDocument(source).use { doc ->
            stripMetadata(doc)
            val replaced = HashMap<COSStream, PDImageXObject?>()
            val pageCount = doc.numberOfPages
            doc.pages.forEachIndexed { index, page ->
                coroutineContext.ensureActive()
                page.cosObject.removeItem(COSName.THUMB)
                page.cosObject.removeItem(COSName.PIECE_INFO)
                compressContentStreams(page)
                val box = page.mediaBox
                page.resources?.let { downsampleImages(doc, it, box, level, grayscale, replaced, depth = 0) }
                progress.report(STRUCTURE_SHARE * (index + 1) / pageCount)
            }
            doc.save(target)
        }
    }

    private fun stripMetadata(doc: PDDocument) {
        doc.documentInformation = PDDocumentInformation()
        doc.documentCatalog.metadata = null
        doc.documentCatalog.cosObject.removeItem(COSName.PIECE_INFO)
    }

    /** Content streams written without a filter are stored raw; Flate usually shrinks them by 70-80%. */
    private fun compressContentStreams(page: PDPage) {
        val streams = page.contentStreams
        while (streams.hasNext()) {
            val stream = streams.next().cosObject
            if (stream.filters != null) continue
            val raw = stream.createRawInputStream().use { it.readBytes() }
            stream.createOutputStream(COSName.FLATE_DECODE).use { it.write(raw) }
        }
    }

    /**
     * Replaces heavy images in [resources] (and in nested form XObjects). [replaced] remembers each image
     * stream's outcome, so an image shared by many pages is decoded once and replaced everywhere.
     */
    private suspend fun downsampleImages(
        doc: PDDocument,
        resources: PDResources,
        page: PDRectangle,
        level: CompressionLevel,
        grayscale: Boolean,
        replaced: MutableMap<COSStream, PDImageXObject?>,
        depth: Int,
    ) {
        for (name in resources.xObjectNames.toList()) {
            coroutineContext.ensureActive()
            val xObject = try {
                resources.getXObject(name)
            } catch (_: IOException) {
                continue // Unreadable object: leave it exactly as it was.
            }
            when (xObject) {
                is PDImageXObject -> {
                    val replacement = replaced.getOrPut(xObject.cosObject) {
                        recompressImage(doc, xObject, page, level, grayscale)
                    }
                    if (replacement != null) resources.put(name, replacement)
                }
                is PDFormXObject -> if (depth < MAX_FORM_DEPTH) {
                    xObject.resources?.let { downsampleImages(doc, it, page, level, grayscale, replaced, depth + 1) }
                }
                else -> Unit
            }
        }
    }

    /** Returns a smaller JPEG version of [image], or null to keep the original. */
    private fun recompressImage(
        doc: PDDocument,
        image: PDImageXObject,
        page: PDRectangle,
        level: CompressionLevel,
        grayscale: Boolean,
    ): PDImageXObject? {
        // Stencils and 1-bit scans (CCITT/JBIG2) are already compact; JPEG would blur or grow them.
        if (image.isStencil || image.bitsPerComponent <= 1) return null
        if (limits.checkDimensions(image.width, image.height) is Result.Failure) return null
        if (hasVisibleMask(image)) return null
        val (width, height) =
            PdfCompressionPolicy.targetSize(image.width, image.height, page.width, page.height, level.dpi)
        val decoded = try {
            image.image
        } catch (_: IOException) {
            null // Codec not supported on Android (for example JPX): keep the original.
        } ?: return null
        val candidate = decoded.use { flatten(it, width, height, grayscale) }.use { bitmap ->
            JPEGFactory.createFromImage(doc, bitmap, level.jpegQuality / PERCENT)
        }
        val originalBytes = image.cosObject.length
        return candidate.takeIf { PdfCompressionPolicy.shouldReplace(originalBytes, it.cosObject.length) }
    }

    /**
     * True when [image] has a mask that hides part of it. Android bitmaps carry an alpha channel by default,
     * so many generated PDFs attach a soft mask that is fully opaque; such a mask is dropped with the
     * re-encode (the image is flattened onto white), while real transparency keeps the original.
     */
    private fun hasVisibleMask(image: PDImageXObject): Boolean {
        if (image.cosObject.containsKey(COSName.MASK)) return true
        if (!image.cosObject.containsKey(COSName.SMASK)) return false
        val mask = try {
            image.softMask
        } catch (_: IOException) {
            null
        } ?: return true
        if (limits.checkDimensions(mask.width, mask.height) is Result.Failure) return true
        val bitmap = try {
            mask.image
        } catch (_: IOException) {
            null
        } ?: return true
        return bitmap.use { !isOpaqueMask(it) }
    }

    /**
     * Mask samples are alpha in an ALPHA_8 bitmap and grey (red) otherwise. JPEG noise just below white
     * still counts as opaque.
     */
    private fun isOpaqueMask(mask: Bitmap): Boolean {
        val alphaOnly = mask.config == Bitmap.Config.ALPHA_8
        val row = IntArray(mask.width)
        for (y in 0 until mask.height) {
            mask.getPixels(row, 0, mask.width, 0, y, mask.width, 1)
            for (pixel in row) {
                val value = if (alphaOnly) Color.alpha(pixel) else Color.red(pixel)
                if (value < OPAQUE_MASK_MIN) return false
            }
        }
        return true
    }

    /** Last resort for the strong level: one JPEG per page, rendered one page at a time. */
    private suspend fun rasterize(
        source: File,
        target: File,
        level: CompressionLevel,
        grayscale: Boolean,
        progress: ProgressReporter,
    ) {
        PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { out ->
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val count = renderer.pageCount
                    for (index in 0 until count) {
                        coroutineContext.ensureActive()
                        renderer.openPage(index).use { addRenderedPage(out, it, level, grayscale) }
                        progress.report(STRUCTURE_SHARE + (1f - STRUCTURE_SHARE) * (index + 1) / count)
                    }
                }
            }
            out.save(target)
        }
    }

    private fun addRenderedPage(
        out: PDDocument,
        pdfPage: PdfRenderer.Page,
        level: CompressionLevel,
        grayscale: Boolean,
    ) {
        val scale = level.dpi / POINTS_PER_INCH
        val w = (pdfPage.width * scale).toInt().coerceAtLeast(1)
        val h = (pdfPage.height * scale).toInt().coerceAtLeast(1)
        limits.checkDimensions(w, h).throwIfFailure()
        val rendered = createBitmap(w, h).use { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            flatten(bitmap, w, h, grayscale)
        }
        val widthPt = pdfPage.width.toFloat()
        val heightPt = pdfPage.height.toFloat()
        val page = PDPage(PDRectangle(widthPt, heightPt))
        out.addPage(page)
        val jpeg = rendered.use { JPEGFactory.createFromImage(out, it, level.jpegQuality / PERCENT) }
        PDPageContentStream(out, page).use { it.drawImage(jpeg, 0f, 0f, widthPt, heightPt) }
    }

    /**
     * Draws [source] onto an opaque white bitmap of [width] x [height] (scaling with filtering, and to grey
     * when asked). Opaque output keeps JPEGFactory from adding an alpha mask that would cost bytes.
     */
    private fun flatten(source: Bitmap, width: Int, height: Int, grayscale: Boolean): Bitmap {
        val output = createBitmap(width, height)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        if (grayscale) paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        Canvas(output).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, null, Rect(0, 0, width, height), paint)
        }
        output.setHasAlpha(false)
        return output
    }

    private companion object {
        const val MAX_FORM_DEPTH = 8
        const val OPAQUE_MASK_MIN = 250
        const val PERCENT = 100f
        const val POINTS_PER_INCH = 72f

        /** Share of the progress bar for the structural pass; the raster fallback (if any) fills the rest. */
        const val STRUCTURE_SHARE = 0.6f
    }
}
