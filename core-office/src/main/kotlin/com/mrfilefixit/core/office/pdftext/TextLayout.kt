package com.mrfilefixit.core.office.pdftext

import com.mrfilefixit.core.domain.document.DocBlock
import com.mrfilefixit.core.domain.document.DocImage
import com.mrfilefixit.core.domain.document.DocPageBreak
import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.DocRun
import com.mrfilefixit.core.domain.document.ListKind
import com.mrfilefixit.core.domain.document.PageSetup
import com.mrfilefixit.core.domain.document.ParagraphStyle
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.domain.document.TextAlign
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Rebuilds reading structure from positioned PDF glyphs: lines, paragraphs (with headings and bullet
 * lists), reading order across two columns, and pictures in place. Pure geometry, so it is unit-tested.
 */
object TextLayout {
    /** A line splits where glyphs are further apart than this many font sizes (columns, table cells). */
    const val TEXT_SPLIT = 2.0f
    const val TABLE_SPLIT = 0.9f
    private const val SPACE_GAP = 0.18f
    private const val SAME_BASELINE = 0.35f
    private const val PARAGRAPH_GAP = 0.65f
    private const val SIZE_TOLERANCE = 1.15f
    private const val H1 = 1.8f
    private const val H2 = 1.4f
    private const val H3 = 1.2f
    private const val H3_LEVEL = 3
    private const val MINOR_HEADING = 4
    private const val SHORT_LINE = 90
    private const val CENTER_TOLERANCE = 0.06f
    private const val MIN_SIDE_MARGIN = 0.15f
    private const val NARROW = 0.5f
    private const val TWO_COLUMN_SHARE = 0.6f
    private const val DEDUPE = 0.6f
    private val BULLETS = setOf("•", "●", "▪", "■", "◦", "○", "‣", "–", "-", "*", "", "", "", "")

    fun joinGlyphs(glyphs: List<PdfGlyph>): String {
        val sb = StringBuilder()
        var previous: PdfGlyph? = null
        for (g in glyphs.sortedBy { it.x }) {
            val prev = previous
            if (g.isSpace) {
                if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
            } else {
                if (prev != null && g.x - prev.right > SPACE_GAP * g.fontSize && sb.isNotEmpty() && sb.last() != ' ') {
                    sb.append(' ')
                }
                sb.append(g.text)
            }
            previous = g
        }
        return sb.toString().trim()
    }

    /** Groups glyphs into lines: same baseline, split at gaps wider than [split] font sizes. */
    fun lines(glyphs: List<PdfGlyph>, split: Float = TEXT_SPLIT): List<TextLine> {
        val sorted = dedupe(
            glyphs.filter {
                it.fontSize > 0f && it.text.isNotEmpty()
            },
        ).sortedWith(compareBy({ it.y }, { it.x }))
        val rows = mutableListOf<MutableList<PdfGlyph>>()
        for (g in sorted) {
            val row = rows.lastOrNull()
            val anchor = row?.first()
            if (anchor != null && abs(g.y - anchor.y) <= SAME_BASELINE * min(g.fontSize, anchor.fontSize)) {
                row += g
            } else {
                rows += mutableListOf(g)
            }
        }
        return rows.flatMap { row -> splitRow(row.sortedBy { it.x }, split) }
    }

    private fun splitRow(row: List<PdfGlyph>, split: Float): List<TextLine> {
        val out = mutableListOf<TextLine>()
        var current = mutableListOf<PdfGlyph>()
        var lastInk: PdfGlyph? = null
        for (g in row) {
            val prev = lastInk
            if (!g.isSpace && prev != null && g.x - prev.right > split * max(g.fontSize, prev.fontSize)) {
                current.takeIf { list -> list.any { !it.isSpace } }?.let { out += TextLine(it.trimSpaces()) }
                current = mutableListOf()
            }
            current += g
            if (!g.isSpace) lastInk = g
        }
        current.takeIf { list -> list.any { !it.isSpace } }?.let { out += TextLine(it.trimSpaces()) }
        return out
    }

    private fun List<PdfGlyph>.trimSpaces(): List<PdfGlyph> = dropWhile { it.isSpace }.dropLastWhile { it.isSpace }

    /** Fake-bold PDFs draw each glyph twice at almost the same spot; keep one. */
    private fun dedupe(glyphs: List<PdfGlyph>): List<PdfGlyph> {
        val seen = HashSet<Triple<String, Int, Int>>()
        return glyphs.filter { g ->
            seen.add(Triple(g.text, (g.x / DEDUPE).toInt(), (g.y / DEDUPE).toInt()))
        }
    }

    private class Block(val lines: MutableList<TextLine>) {
        val top: Float get() = lines.first().top
        val left: Float get() = lines.minOf { it.x }
        val right: Float get() = lines.maxOf { it.right }
    }

    /** Paragraph blocks of one page in reading order. */
    private fun blocks(lines: List<TextLine>, pageWidth: Float): List<Block> {
        val blocks = mutableListOf<Block>()
        for (line in lines.sortedWith(compareBy({ it.baseline }, { it.x }))) {
            val target = blocks.filter { canContinue(it, line) }.maxByOrNull { overlap(it.lines.last(), line) }
            if (target != null) target.lines += line else blocks += Block(mutableListOf(line))
        }
        return readingOrder(blocks, pageWidth)
    }

    private fun canContinue(block: Block, line: TextLine): Boolean {
        val last = block.lines.last()
        val gap = line.top - last.bottom
        val sizeRatio = max(line.fontSize, last.fontSize) / min(line.fontSize, last.fontSize)
        return overlap(last, line) > 0f &&
            gap > -last.fontSize * SAME_BASELINE &&
            gap < PARAGRAPH_GAP * last.fontSize &&
            sizeRatio <= SIZE_TOLERANCE &&
            bulletOf(line.text) == null &&
            last.allBold == line.allBold
    }

    private fun overlap(a: TextLine, b: TextLine): Float = min(a.right, b.right) - max(a.x, b.x)

    /** Two-column pages read the left column, then the right, between full-width blocks. */
    private fun readingOrder(blocks: List<Block>, pageWidth: Float): List<Block> {
        val mid = pageWidth / 2
        val narrow = blocks.count { it.right - it.left < NARROW * pageWidth && (it.right <= mid || it.left >= mid) }
        if (blocks.size < 4 || narrow < TWO_COLUMN_SHARE * blocks.size) return blocks.sortedBy { it.top }
        val ordered = mutableListOf<Block>()
        val pending = mutableListOf<Block>()
        fun flush() {
            ordered += pending.filter { it.left < mid }.sortedBy { it.top }
            ordered += pending.filter { it.left >= mid }.sortedBy { it.top }
            pending.clear()
        }
        for (block in blocks.sortedBy { it.top }) {
            val spans = block.left < mid && block.right > mid
            if (spans) {
                flush()
                ordered += block
            } else {
                pending += block
            }
        }
        flush()
        return ordered
    }

    fun bulletOf(text: String): String? {
        val first = text.substringBefore(' ')
        return first.takeIf { it in BULLETS && text.length > first.length }
    }

    /** The most common glyph size, weighted by characters: the body text size. */
    fun bodySize(pages: List<PdfPageText>): Float {
        val counts = HashMap<Int, Int>()
        pages.forEach { p ->
            p.glyphs.forEach { g ->
                if (!g.isSpace) {
                    counts.merge(
                        (g.fontSize * 2).toInt(),
                        1,
                        Int::plus,
                    )
                }
            }
        }
        return counts.maxByOrNull { it.value }?.key?.div(2f) ?: RichDocument.DEFAULT_SIZE_PT
    }

    /** A Word-style document from the pages' text and pictures. */
    fun toRichDocument(pages: List<PdfPageText>): RichDocument {
        val body = bodySize(pages)
        val blocks = mutableListOf<DocBlock>()
        pages.forEachIndexed { index, page ->
            if (index > 0) blocks += DocPageBreak
            val pictures = page.pictures.sortedBy { it.y }.toMutableList()
            for (block in blocks(lines(page.glyphs), page.widthPt)) {
                while (pictures.isNotEmpty() && pictures.first().y < block.top) {
                    blocks += picture(pictures.removeAt(0), page)
                }
                blocks += paragraphs(block, body, page.widthPt)
            }
            pictures.forEach { blocks += picture(it, page) }
        }
        val first = pages.firstOrNull()
        val setup = if (first != null && first.widthPt > 0f) PageSetup(first.widthPt, first.heightPt) else PageSetup()
        return RichDocument(blocks, page = setup, baseSizePt = body)
    }

    private fun picture(p: PdfPicture, page: PdfPageText): DocImage {
        val maxWidth = PageSetup(page.widthPt, page.heightPt).contentWidthPt
        val scale = if (p.width > maxWidth) maxWidth / p.width else 1f
        return DocImage(p.bytes, p.mimeType, p.width * scale, p.height * scale)
    }

    private fun paragraphs(block: Block, body: Float, pageWidth: Float): List<DocParagraph> {
        val out = mutableListOf<DocParagraph>()
        val size = block.lines.map { it.fontSize }.sorted()[block.lines.size / 2]
        val heading = headingLevel(block, size, body)
        val align = if (isCentered(block, pageWidth)) TextAlign.CENTER else TextAlign.START
        var runs = mutableListOf<DocRun>()
        var list = ListKind.NONE
        fun flush() {
            if (runs.isNotEmpty()) {
                val style =
                    ParagraphStyle(
                        heading = if (list == ListKind.NONE) heading else 0,
                        align = align,
                        list = list,
                        spaceAfterPt = size / 2,
                    )
                out += DocParagraph(com.mrfilefixit.core.office.docx.DocxReader.mergeRuns(runs), style)
            }
            runs = mutableListOf()
        }
        block.lines.forEach { line ->
            val bullet = bulletOf(line.text)
            if (bullet != null) {
                flush()
                list = ListKind.BULLET
            }
            appendLine(runs, line, skipPrefix = bullet, size = size, heading = heading > 0)
        }
        flush()
        return out
    }

    private fun appendLine(
        runs: MutableList<DocRun>,
        line: TextLine,
        skipPrefix: String?,
        size: Float,
        heading: Boolean,
    ) {
        val last = runs.lastOrNull()
        if (last != null) {
            // Re-join words hyphenated at the end of the previous line.
            if (last.text.endsWith("-") && line.text.firstOrNull()?.isLowerCase() == true) {
                runs[runs.size - 1] = last.copy(text = last.text.dropLast(1))
            } else {
                runs[runs.size - 1] = last.copy(text = last.text + " ")
            }
        }
        var glyphs = line.glyphs
        if (skipPrefix != null) {
            var dropped = 0
            glyphs = glyphs.dropWhile { g ->
                (dropped < skipPrefix.length || g.isSpace).also {
                    if (!g.isSpace) dropped += g.text.length
                }
            }
        }
        groupRuns(glyphs).forEach { (style, text) ->
            runs += DocRun(
                text = text,
                bold = style.bold && !heading,
                italic = style.italic,
                sizePt = style.size.takeIf { abs(it - size) > 0.5f },
                colorArgb = style.color.takeIf { it != BLACK },
            )
        }
    }

    private data class RunStyle(val bold: Boolean, val italic: Boolean, val size: Float, val color: Int?)

    /** The line as runs that keep each glyph group's size, weight, slant and colour (for positioned text). */
    fun lineRuns(line: TextLine): List<DocRun> = groupRuns(line.glyphs).map { (style, text) ->
        DocRun(
            text = text,
            bold = style.bold,
            italic = style.italic,
            sizePt = style.size,
            colorArgb = style.color.takeIf { it != BLACK },
        )
    }

    private fun groupRuns(glyphs: List<PdfGlyph>): List<Pair<RunStyle, String>> {
        val out = mutableListOf<Pair<RunStyle, MutableList<PdfGlyph>>>()
        for (g in glyphs) {
            val style = RunStyle(g.bold, g.italic, (g.fontSize * 2).toInt() / 2f, g.colorArgb)
            val last = out.lastOrNull()
            if (last != null && (last.first == style || g.isSpace)) {
                last.second += g
            } else {
                out += style to mutableListOf(g)
            }
        }
        return out.map { (style, gs) -> style to joinWithGaps(gs) }.filter { it.second.isNotEmpty() }
    }

    /** Like [joinGlyphs] but keeps leading and trailing spaces, which separate runs. */
    private fun joinWithGaps(glyphs: List<PdfGlyph>): String {
        val sb = StringBuilder()
        var prev: PdfGlyph? = null
        for (g in glyphs) {
            val p = prev
            when {
                g.isSpace -> if (sb.isEmpty() || sb.last() != ' ') sb.append(' ')
                p != null && g.x - p.right > SPACE_GAP * g.fontSize && sb.isNotEmpty() && sb.last() != ' ' -> sb.append(
                    ' ',
                ).append(g.text)
                else -> sb.append(g.text)
            }
            prev = g
        }
        return sb.toString()
    }

    private fun isBoldLabel(block: Block, text: String, ratio: Float): Boolean =
        block.lines.size == 1 && text.length < SHORT_LINE && block.lines.first().allBold && ratio >= 1f &&
            bulletOf(text) == null

    private fun headingLevel(block: Block, size: Float, body: Float): Int {
        val ratio = size / body
        val text = block.lines.joinToString(" ") { it.text }
        return when {
            ratio >= H1 -> 1
            ratio >= H2 -> 2
            ratio >= H3 -> H3_LEVEL
            isBoldLabel(block, text, ratio) -> MINOR_HEADING
            else -> 0
        }
    }

    private fun isCentered(block: Block, pageWidth: Float): Boolean {
        if (pageWidth <= 0f) return false
        val center = (block.left + block.right) / 2
        val margins = min(block.left, pageWidth - block.right) / pageWidth
        return abs(center - pageWidth / 2) < CENTER_TOLERANCE * pageWidth && margins > MIN_SIDE_MARGIN
    }

    private const val BLACK = -0x1000000
}
