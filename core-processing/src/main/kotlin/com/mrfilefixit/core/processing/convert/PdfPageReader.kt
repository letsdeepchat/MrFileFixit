package com.mrfilefixit.core.processing.convert

import android.graphics.Bitmap
import com.mrfilefixit.core.office.pdftext.PdfGlyph
import com.mrfilefixit.core.office.pdftext.PdfPageText
import com.mrfilefixit.core.office.pdftext.PdfPicture
import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColorN
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColorSpace
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceCMYKColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceGrayColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceRGBColor
import com.tom_roush.pdfbox.contentstream.operator.state.Concatenate
import com.tom_roush.pdfbox.contentstream.operator.state.Restore
import com.tom_roush.pdfbox.contentstream.operator.state.Save
import com.tom_roush.pdfbox.contentstream.operator.state.SetGraphicsStateParameters
import com.tom_roush.pdfbox.contentstream.operator.state.SetMatrix
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.IOException
import kotlin.math.max

/**
 * Reads what a PDF page draws: every glyph with its position, size, weight and colour (PDFBox's text
 * stripper), and optionally the placed pictures with their on-page frames (a stream engine that follows
 * the transformation matrix into form XObjects).
 */
internal class PdfPageReader(private val maxPicturePixels: Long) {

    /** [pageIndex] is 0-based. */
    fun read(document: PDDocument, pageIndex: Int, withPictures: Boolean): PdfPageText {
        val page = document.getPage(pageIndex)
        val box = page.cropBox
        val rotated = page.rotation % HALF_TURN != 0
        val width = if (rotated) box.height else box.width
        val height = if (rotated) box.width else box.height
        val collector = GlyphCollector().apply {
            startPage = pageIndex + 1
            endPage = pageIndex + 1
        }
        collector.getText(document)
        val glyphs = collector.glyphs
        var pictures = if (withPictures && !rotated) PictureCollector(page, maxPicturePixels).collect() else emptyList()
        if (collector.invisibleText) {
            // A scanned page with an OCR text layer: the text is what matters, not the page-sized scan.
            pictures = pictures.filterNot { it.width * it.height > width * height * PAGE_SIZED }
        }
        return PdfPageText(width, height, glyphs, pictures)
    }

    private class GlyphCollector : PDFTextStripper() {
        val glyphs = mutableListOf<PdfGlyph>()
        var invisibleText = false
            private set

        init {
            addOperator(SetNonStrokingColorSpace())
            addOperator(SetNonStrokingColor())
            addOperator(SetNonStrokingColorN())
            addOperator(SetNonStrokingDeviceRGBColor())
            addOperator(SetNonStrokingDeviceGrayColor())
            addOperator(SetNonStrokingDeviceCMYKColor())
        }

        override fun processTextPosition(text: TextPosition) {
            val unicode = text.unicode ?: return
            if (unicode.isEmpty()) return
            val state = graphicsState
            if (state.textState.renderingMode == RenderingMode.NEITHER) invisibleText = true
            val color = try {
                state.nonStrokingColor?.toRGB()?.let { it or OPAQUE }
            } catch (_: IOException) {
                null
            } catch (_: UnsupportedOperationException) {
                null
            }
            val font = text.font
            glyphs += PdfGlyph(
                text = unicode,
                x = text.xDirAdj,
                y = text.yDirAdj,
                width = max(text.widthDirAdj, 0f),
                fontSize = max(text.fontSizeInPt, MIN_FONT),
                bold = isBold(font),
                italic = isItalic(font),
                colorArgb = color,
            )
        }

        private fun isBold(font: PDFont?): Boolean {
            val descriptor = font?.fontDescriptor
            val name = (descriptor?.fontName ?: font?.name).orEmpty().lowercase()
            return descriptor?.isForceBold == true || (descriptor?.fontWeight ?: 0f) >= BOLD_WEIGHT ||
                BOLD_NAMES.any { it in name }
        }

        private fun isItalic(font: PDFont?): Boolean {
            val descriptor = font?.fontDescriptor
            val name = (descriptor?.fontName ?: font?.name).orEmpty().lowercase()
            return descriptor?.isItalic == true || (descriptor?.italicAngle ?: 0f) != 0f ||
                "italic" in name || "oblique" in name
        }
    }

    private class PictureCollector(private val page: PDPage, private val maxPixels: Long) : PDFStreamEngine() {
        private val pictures = mutableListOf<PdfPicture>()

        init {
            addOperator(Concatenate())
            addOperator(SetGraphicsStateParameters())
            addOperator(Save())
            addOperator(Restore())
            addOperator(SetMatrix())
        }

        fun collect(): List<PdfPicture> {
            processPage(page)
            return pictures
        }

        override fun processOperator(operator: Operator, operands: List<COSBase>) {
            if (operator.name != DRAW_OBJECT) {
                super.processOperator(operator, operands)
                return
            }
            val name = operands.firstOrNull() as? COSName ?: return
            when (val xObject = resources?.getXObject(name)) {
                is PDImageXObject -> picture(xObject)
                is PDFormXObject -> showForm(xObject)
            }
        }

        private fun picture(image: PDImageXObject) {
            val ctm = graphicsState.currentTransformationMatrix
            val w = ctm.scalingFactorX
            val h = ctm.scalingFactorY
            if (w < MIN_PICTURE || h < MIN_PICTURE) return
            if (image.width.toLong() * image.height > maxPixels) return
            val box = page.cropBox
            val x = ctm.translateX - box.lowerLeftX
            val top = box.upperRightY - (ctm.translateY + h)
            val bitmap = try {
                image.image
            } catch (_: IOException) {
                null
            } ?: return
            try {
                val scaled = downscale(bitmap)
                val png = scaled.hasAlpha()
                val bytes = if (png) Pictures.encodePng(scaled) else Pictures.encodeJpeg(scaled, JPEG_QUALITY)
                if (scaled !== bitmap) scaled.recycle()
                pictures += PdfPicture(x, top, w, h, bytes, if (png) "image/png" else "image/jpeg")
            } finally {
                bitmap.recycle()
            }
        }

        private fun downscale(bitmap: Bitmap): Bitmap {
            val side = max(bitmap.width, bitmap.height)
            if (side <= MAX_SIDE) return bitmap
            val scale = MAX_SIDE.toFloat() / side
            val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(bitmap, w, h, true)
        }
    }

    private companion object {
        const val HALF_TURN = 180
        const val OPAQUE = -0x1000000
        const val MIN_FONT = 1f
        const val BOLD_WEIGHT = 600f
        val BOLD_NAMES = listOf("bold", "black", "heavy", "semibold", "demibold")
        const val DRAW_OBJECT = "Do"
        const val MIN_PICTURE = 8f
        const val MAX_SIDE = 2000
        const val JPEG_QUALITY = 85
        const val PAGE_SIZED = 0.7f
    }
}
