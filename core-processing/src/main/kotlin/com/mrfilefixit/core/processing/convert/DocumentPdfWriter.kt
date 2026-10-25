package com.mrfilefixit.core.processing.convert

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.graphics.withClip
import com.mrfilefixit.core.domain.document.DocBlock
import com.mrfilefixit.core.domain.document.DocImage
import com.mrfilefixit.core.domain.document.DocPageBreak
import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.DocTable
import com.mrfilefixit.core.domain.document.DocTableRow
import com.mrfilefixit.core.domain.document.PageSetup
import com.mrfilefixit.core.domain.document.ParagraphStyle
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.domain.document.TextAlign
import com.mrfilefixit.core.office.OfficeLimitException
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.ensureActive

/**
 * Lays a [RichDocument] out on pages with Android's own PDF writer: paragraphs break between lines,
 * headings stay with the next paragraph, table rows move to the next page as a whole (with the header row
 * repeated), and rows taller than a page continue across pages.
 */
internal class DocumentPdfWriter(private val maxPages: Int) {

    suspend fun write(document: RichDocument, out: File, progress: (Float) -> Unit = {}): Int {
        val pdf = PdfDocument()
        try {
            val cursor = Cursor(pdf, document.page, maxPages)
            val painter = ParagraphPainter(document.defaultFont, document.textColor)
            val flow = Flow(cursor, painter, document.baseSizePt)
            document.blocks.forEachIndexed { index, block ->
                if (index % PROGRESS_EVERY == 0) {
                    coroutineContext.ensureActive()
                    progress(index.toFloat() / document.blocks.size)
                }
                flow.block(block, document.blocks.getOrNull(index + 1))
            }
            cursor.finish()
            out.outputStream().use { pdf.writeTo(it) }
            return cursor.pageCount
        } finally {
            pdf.close()
        }
    }

    /** The current page and the vertical position on it, in points from the page's top edge. */
    private class Cursor(private val pdf: PdfDocument, val setup: PageSetup, private val maxPages: Int) {
        private var page: PdfDocument.Page? = null
        var pageCount = 0
            private set
        var y = 0f
        val left: Float get() = setup.marginStartPt
        val width: Float get() = setup.contentWidthPt
        val top: Float get() = setup.marginTopPt
        val bottom: Float get() = setup.heightPt - setup.marginBottomPt
        val remaining: Float get() = bottom - y
        val atTop: Boolean get() = y <= top + EPSILON
        val canvas: Canvas get() = (page ?: newPage()).canvas

        fun newPage(): PdfDocument.Page {
            page?.let(pdf::finishPage)
            if (pageCount >= maxPages) throw OfficeLimitException("Too many pages")
            pageCount++
            val info = PdfDocument.PageInfo.Builder(
                setup.widthPt.roundToInt(),
                setup.heightPt.roundToInt(),
                pageCount,
            ).create()
            return pdf.startPage(info).also {
                page = it
                y = top
            }
        }

        fun ensurePage() {
            if (page == null) newPage()
        }

        fun finish() {
            ensurePage()
            page?.let(pdf::finishPage)
            page = null
        }
    }

    private class Flow(private val cursor: Cursor, private val painter: ParagraphPainter, private val base: Float) {
        private val lists = ListCounter()

        fun block(block: DocBlock, next: DocBlock?) {
            cursor.ensurePage()
            when (block) {
                is DocParagraph -> paragraph(block, next)
                is DocTable -> {
                    lists.label(NO_LIST)
                    table(block)
                }
                is DocImage -> {
                    lists.label(NO_LIST)
                    image(block)
                }
                DocPageBreak -> {
                    lists.label(NO_LIST)
                    cursor.newPage()
                }
            }
        }

        private fun paragraph(p: DocParagraph, next: DocBlock?) {
            if (!cursor.atTop) cursor.y += p.style.spaceBeforePt
            val measured = painter.measure(p, cursor.width, base, lists.label(p.style))
            if (p.style.keepWithNext || p.style.heading > 0) keepWithNext(measured, next)
            drawParagraph(measured, cursor.left, cursor.width)
            cursor.y += p.style.spaceAfterPt
        }

        /** Moves a heading to the next page when it would otherwise end the page alone. */
        private fun keepWithNext(measured: MeasuredParagraph, next: DocBlock?) {
            val follow = if (next == null || next == DocPageBreak) 0f else base * KEEP_LINES
            if (!cursor.atTop && measured.height + follow > cursor.remaining) cursor.newPage()
        }

        private fun drawParagraph(measured: MeasuredParagraph, x: Float, width: Float) {
            val count = measured.layout.lineCount
            var line = 0
            while (line < count) {
                var end = line
                val start = measured.lineTop(line)
                while (end < count && measured.lineBottom(end) - start <= cursor.remaining) end++
                if (end == line) {
                    if (!cursor.atTop) {
                        cursor.newPage()
                        continue
                    }
                    end = line + 1 // a single line taller than the page: draw it anyway
                }
                measured.drawLines(cursor.canvas, x, cursor.y, width, line, end)
                cursor.y += measured.lineBottom(end - 1) - start
                line = end
                if (line < count) cursor.newPage()
            }
        }

        private fun image(image: DocImage) {
            val (w, h) = fit(image.widthPt, image.heightPt, cursor.width, cursor.setup.contentHeightPt)
            if (h > cursor.remaining && !cursor.atTop) cursor.newPage()
            val x = when (image.align) {
                TextAlign.CENTER -> cursor.left + (cursor.width - w) / 2
                TextAlign.END -> cursor.left + cursor.width - w
                else -> cursor.left
            }
            Pictures.draw(cursor.canvas, image.bytes, RectF(x, cursor.y, x + w, cursor.y + h))
            cursor.y += h + IMAGE_GAP
        }

        // region tables

        private class MeasuredRow(val row: DocTableRow, val cells: List<List<CellItem>>, val height: Float)

        private sealed interface CellItem {
            val height: Float

            class Text(val paragraph: MeasuredParagraph, val spaceAfter: Float) : CellItem {
                override val height: Float get() = paragraph.height + spaceAfter
            }

            class Picture(val image: DocImage, val width: Float, override val height: Float) : CellItem
        }

        private fun table(table: DocTable) {
            val columns = table.columnCount
            if (columns == 0) return
            val widths = columnWidths(table, columns, cursor.width)
            val measured = table.rows.map { measureRow(it, widths) }
            val headers = measured.takeWhile { it.row.header }
            measured.forEachIndexed { index, row ->
                val isHeader = index < headers.size
                val needsMove = row.height > cursor.remaining && !cursor.atTop &&
                    row.height <= cursor.setup.contentHeightPt
                if (needsMove) {
                    cursor.newPage()
                    if (!isHeader) headers.forEach { drawRow(it, widths, table.borderArgb) }
                }
                drawRow(row, widths, table.borderArgb)
            }
            cursor.y += TABLE_GAP
        }

        private fun columnWidths(table: DocTable, columns: Int, total: Float): List<Float> {
            val weights = (0 until columns).map { table.columnWeights.getOrNull(it)?.takeIf { w -> w > 0f } ?: 1f }
            val sum = weights.sum()
            return weights.map { it / sum * total }
        }

        private fun measureRow(row: DocTableRow, widths: List<Float>): MeasuredRow {
            val counter = ListCounter()
            val cells = widths.indices.map { c ->
                val cell = row.cells.getOrNull(c) ?: return@map emptyList()
                val inner = (widths[c] - 2 * cell.paddingPt).coerceAtLeast(1f)
                cellItems(cell.blocks, inner, counter)
            }
            val height = widths.indices.maxOf { c ->
                val padding = row.cells.getOrNull(c)?.paddingPt ?: 0f
                cells[c].sumOf { it.height.toDouble() }.toFloat() + 2 * padding
            }
            return MeasuredRow(row, cells, height.coerceAtLeast(MIN_ROW))
        }

        private fun cellItems(blocks: List<DocBlock>, width: Float, counter: ListCounter): List<CellItem> =
            blocks.flatMap { block ->
                when (block) {
                    is DocParagraph -> listOf(
                        CellItem.Text(
                            painter.measure(block, width, base, counter.label(block.style)),
                            block.style.spaceAfterPt,
                        ),
                    )
                    is DocImage -> {
                        val (w, h) = fit(block.widthPt, block.heightPt, width, cursor.setup.contentHeightPt / 2)
                        listOf(CellItem.Picture(block, w, h))
                    }
                    // Nested tables are flattened to their text, one paragraph per cell.
                    is DocTable -> cellItems(block.rows.flatMap { r -> r.cells.flatMap { it.blocks } }, width, counter)
                    DocPageBreak -> emptyList()
                }
            }

        /** Draws [row] at the cursor; a row taller than the space left continues on the next pages. */
        private fun drawRow(row: MeasuredRow, widths: List<Float>, border: Int?) {
            var consumed = 0f
            while (consumed < row.height - EPSILON) {
                val slice = min(cursor.remaining, row.height - consumed)
                cursor.canvas.withClip(
                    cursor.left - BORDER,
                    cursor.y - BORDER,
                    cursor.left + cursor.width + BORDER,
                    cursor.y + slice + BORDER,
                ) {
                    paintRow(this, row, widths, border, cursor.y - consumed)
                }
                consumed += slice
                cursor.y += slice
                if (consumed < row.height - EPSILON) cursor.newPage()
            }
        }

        private fun paintRow(canvas: Canvas, row: MeasuredRow, widths: List<Float>, border: Int?, top: Float) {
            var x = cursor.left
            widths.forEachIndexed { c, w ->
                val cell = row.row.cells.getOrNull(c)
                cell?.shadingArgb?.let {
                    canvas.drawRect(
                        x,
                        top,
                        x + w,
                        top + row.height,
                        Paint().apply { color = it },
                    )
                }
                var y = top + (cell?.paddingPt ?: 0f)
                val innerX = x + (cell?.paddingPt ?: 0f)
                val innerW = w - 2 * (cell?.paddingPt ?: 0f)
                row.cells[c].forEach { item ->
                    when (item) {
                        is CellItem.Text -> item.paragraph.drawLines(
                            canvas,
                            innerX,
                            y,
                            innerW,
                            0,
                            item.paragraph.layout.lineCount,
                        )
                        is CellItem.Picture -> Pictures.draw(
                            canvas,
                            item.image.bytes,
                            RectF(innerX, y, innerX + item.width, y + item.height),
                        )
                    }
                    y += item.height
                }
                if (border != null) {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = border
                        style = Paint.Style.STROKE
                        strokeWidth = BORDER
                    }
                    canvas.drawRect(x, top, x + w, top + row.height, paint)
                }
                x += w
            }
        }

        // endregion

        private companion object {
            /** Any non-list style: passing it to [ListCounter.label] restarts numbering. */
            val NO_LIST = ParagraphStyle()
        }
    }

    companion object {
        private const val PROGRESS_EVERY = 25

        /** Lines of the following block a heading wants on the same page. */
        private const val KEEP_LINES = 3f
        private const val EPSILON = 0.5f
        private const val IMAGE_GAP = 6f
        private const val TABLE_GAP = 8f
        private const val MIN_ROW = 6f
        private const val BORDER = 0.5f

        /** Scales (w, h) down, keeping its aspect ratio, to fit inside (maxW, maxH). */
        fun fit(w: Float, h: Float, maxW: Float, maxH: Float): Pair<Float, Float> {
            val safeW = w.coerceAtLeast(1f)
            val safeH = h.coerceAtLeast(1f)
            val scale = min(1f, min(maxW / safeW, maxH / safeH))
            return safeW * scale to safeH * scale
        }
    }
}
