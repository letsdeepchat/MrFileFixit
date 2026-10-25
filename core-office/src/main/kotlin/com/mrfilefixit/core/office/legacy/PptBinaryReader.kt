package com.mrfilefixit.core.office.legacy

import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.FrameRect
import com.mrfilefixit.core.domain.document.ListKind
import com.mrfilefixit.core.domain.document.ParagraphStyle
import com.mrfilefixit.core.domain.document.Presentation
import com.mrfilefixit.core.domain.document.Slide
import com.mrfilefixit.core.domain.document.SlideShape
import com.mrfilefixit.core.domain.document.VerticalAnchor
import com.mrfilefixit.core.office.OfficeEncryptedException
import com.mrfilefixit.core.office.OfficeFormatException
import com.mrfilefixit.core.office.OfficeLimitException
import com.mrfilefixit.core.office.OfficeLimits
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * Slide text of a legacy PowerPoint 97-2003 (.ppt) file. Each slide becomes a title and a bulleted body on a
 * plain 4:3 slide: positions, pictures and formatting are not read.
 */
/** A text atom's text type (title, body, ...) and its text. */
private typealias SlideText = Pair<Int, String>

class PptBinaryReader(private val limits: OfficeLimits = OfficeLimits.DEFAULT) {

    fun read(data: ByteArray): Presentation {
        val cfb = CompoundFile(data)
        if (cfb.stream("EncryptedSummary") != null) throw OfficeEncryptedException()
        val stream = cfb.stream("PowerPoint Document") ?: throw OfficeFormatException("No PowerPoint stream")
        val records = Records(stream)
        val persist = cfb.stream("Current User")?.let { persistDirectory(records, it) }.orEmpty()
        val slides = persist[DOCUMENT_REF_KEY]?.let { slidesFromDocument(records, it, persist) }
            ?: linearSlides(records)
        if (slides.size > limits.maxSlides) throw OfficeLimitException("Too many slides")
        return Presentation(WIDTH, HEIGHT, slides.filter { it.isNotEmpty() }.map(::toSlide))
    }

    private class Records(val data: ByteArray) {
        val b: ByteBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        class Header(
            val pos: Int,
            val instance: Int,
            val container: Boolean,
            val type: Int,
            val body: Int,
            val len: Int,
        ) {
            val end: Int get() = body + len
        }

        fun header(pos: Int, limit: Int = data.size): Header? {
            if (pos < 0 || pos + HEADER > limit) return null
            val verInstance = b.getShort(pos).toUShort().toInt()
            val len = b.getInt(pos + 4)
            if (len < 0 || pos + HEADER + len > limit) return null
            return Header(
                pos = pos,
                instance = verInstance shr INSTANCE_SHIFT,
                container = verInstance and VERSION_MASK == CONTAINER,
                type = b.getShort(pos + 2).toUShort().toInt(),
                body = pos + HEADER,
                len = len,
            )
        }

        /** Direct children of the container at [parent]. */
        fun children(parent: Header): List<Header> {
            val out = mutableListOf<Header>()
            var pos = parent.body
            while (true) {
                val h = header(pos, parent.end) ?: break
                out += h
                pos = h.end
            }
            return out
        }
    }

    /**
     * Persist id → stream offset, following the edit chain from the newest UserEditAtom back to the first
     * (newer saves override older entries). [DOCUMENT_REF_KEY] holds the Document record's offset.
     */
    private fun persistDirectory(records: Records, currentUser: ByteArray): Map<Int, Int> {
        if (currentUser.size < CURRENT_USER_EDIT_OFFSET + 4) return emptyMap()
        var edit = ByteBuffer.wrap(currentUser).order(ByteOrder.LITTLE_ENDIAN).getInt(CURRENT_USER_EDIT_OFFSET)
        val chain = mutableListOf<Pair<Int, Int>>() // (persist directory offset, document persist id)
        val seen = mutableSetOf<Int>()
        while (edit > 0 && seen.add(edit) && seen.size < MAX_EDITS) {
            val h = records.header(edit)?.takeIf { it.type == USER_EDIT && it.len >= USER_EDIT_MIN } ?: break
            chain += records.b.getInt(h.body + EDIT_PERSIST_DIR) to records.b.getInt(h.body + EDIT_DOC_REF)
            edit = records.b.getInt(h.body + EDIT_LAST_EDIT)
        }
        val map = mutableMapOf<Int, Int>()
        for ((dirOffset, _) in chain.asReversed()) {
            val h = records.header(dirOffset)?.takeIf { it.type == PERSIST_DIRECTORY } ?: continue
            var pos = h.body
            while (pos + 4 <= h.end) {
                val entry = records.b.getInt(pos)
                val first = entry and PERSIST_ID_MASK
                val count = entry ushr PERSIST_COUNT_SHIFT
                pos += 4
                for (i in 0 until count) {
                    if (pos + 4 > h.end) break
                    map[first + i] = records.b.getInt(pos)
                    pos += 4
                }
            }
        }
        chain.firstOrNull()?.second?.let { docRef -> map[docRef]?.let { map[DOCUMENT_REF_KEY] = it } }
        return map
    }

    private fun slidesFromDocument(records: Records, docOffset: Int, persist: Map<Int, Int>): List<List<SlideText>>? {
        val doc = records.header(docOffset)?.takeIf { it.type == DOCUMENT && it.container } ?: return null
        val list = records.children(doc).firstOrNull {
            it.type == SLIDE_LIST_WITH_TEXT && it.instance == 0
        } ?: return emptyList()
        val slides = mutableListOf<Pair<Int?, MutableList<SlideText>>>()
        var textType = BODY
        for (h in records.children(list)) {
            when (h.type) {
                SLIDE_PERSIST -> slides += (if (h.len >= 4) records.b.getInt(h.body) else null) to mutableListOf()
                TEXT_HEADER -> if (h.len >= 4) textType = records.b.getInt(h.body)
                TEXT_CHARS, TEXT_BYTES -> slides.lastOrNull()?.second?.add(textType to text(records, h))
            }
        }
        return slides.map { (ref, listTexts) ->
            val slideTexts = ref?.let { persist[it] }?.let { records.header(it) }?.takeIf { it.container }
                ?.let { slide -> mutableListOf<SlideText>().also { collectTexts(records, slide, it, 0) } }
            slideTexts?.takeIf { it.isNotEmpty() } ?: listTexts
        }
    }

    /** Text atoms anywhere inside [container] (slide shapes keep theirs in OfficeArt client text boxes). */
    private fun collectTexts(
        records: Records,
        container: Records.Header,
        out: MutableList<SlideText>,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH) throw OfficeFormatException("Records nested too deeply")
        var textType = BODY
        for (h in records.children(container)) {
            when {
                h.container -> collectTexts(records, h, out, depth + 1)
                h.type == TEXT_HEADER && h.len >= 4 -> textType = records.b.getInt(h.body)
                h.type == TEXT_CHARS || h.type == TEXT_BYTES -> out += textType to text(records, h)
            }
        }
    }

    private fun text(records: Records, h: Records.Header): String = if (h.type == TEXT_CHARS) {
        String(records.data, h.body, h.len - h.len % 2, Charsets.UTF_16LE)
    } else {
        String(records.data, h.body, h.len, CP1252)
    }

    /** Fallback without a usable persist directory: every SlideListWithText in stream order. */
    private fun linearSlides(records: Records): List<List<SlideText>> {
        val slides = mutableListOf<MutableList<SlideText>>()
        fun walk(parent: Records.Header, depth: Int) {
            if (depth > MAX_DEPTH) return
            var textType = BODY
            for (h in records.children(parent)) {
                when {
                    h.type == SLIDE_LIST_WITH_TEXT && h.instance != 0 -> Unit
                    h.type == SLIDE_PERSIST -> slides += mutableListOf<SlideText>()
                    h.container -> walk(h, depth + 1)
                    h.type == TEXT_HEADER && h.len >= 4 -> textType = records.b.getInt(h.body)
                    h.type == TEXT_CHARS || h.type == TEXT_BYTES -> slides.lastOrNull()?.add(
                        textType to text(records, h),
                    )
                }
            }
        }
        walk(Records.Header(0, 0, true, 0, 0, records.data.size), 0)
        return slides.distinct()
    }

    private fun toSlide(texts: List<SlideText>): Slide {
        val titleIndex = texts.indexOfFirst { it.first == TITLE || it.first == CENTER_TITLE }.coerceAtLeast(0)
        val title = texts[titleIndex].second
        val body = texts.filterIndexed { i, _ -> i != titleIndex }.flatMap { (_, text) -> text.split('\r') }
            .map { it.replace('\u000B', '\n').trim() }.filter { it.isNotEmpty() }
        val shapes = mutableListOf<SlideShape>(
            SlideShape.TextBox(
                FrameRect(MARGIN, MARGIN, WIDTH - 2 * MARGIN, TITLE_HEIGHT),
                title.split('\r').map { DocParagraph.of(it.replace('\u000B', '\n').trim()) },
                anchor = VerticalAnchor.MIDDLE,
                defaultSizePt = TITLE_SIZE,
            ),
        )
        if (body.isNotEmpty()) {
            shapes += SlideShape.TextBox(
                FrameRect(MARGIN, BODY_TOP, WIDTH - 2 * MARGIN, HEIGHT - BODY_TOP - MARGIN),
                body.map { DocParagraph.of(it, ParagraphStyle(list = ListKind.BULLET, spaceAfterPt = GAP / 2)) },
                defaultSizePt = BODY_SIZE,
            )
        }
        return Slide(WHITE, shapes)
    }

    companion object {
        private val CP1252: Charset = Charset.forName("windows-1252")
        private const val HEADER = 8
        private const val VERSION_MASK = 0x000F
        private const val CONTAINER = 0x000F
        private const val INSTANCE_SHIFT = 4
        private const val MAX_DEPTH = 32
        private const val DOCUMENT = 0x03E8
        private const val SLIDE_LIST_WITH_TEXT = 0x0FF0
        private const val SLIDE_PERSIST = 0x03F3
        private const val TEXT_HEADER = 0x0F9F
        private const val TEXT_CHARS = 0x0FA0
        private const val TEXT_BYTES = 0x0FA8
        private const val USER_EDIT = 0x0FF5
        private const val PERSIST_DIRECTORY = 0x1772
        private const val CURRENT_USER_EDIT_OFFSET = 16
        private const val USER_EDIT_MIN = 20
        private const val EDIT_LAST_EDIT = 8
        private const val EDIT_PERSIST_DIR = 12
        private const val EDIT_DOC_REF = 16
        private const val PERSIST_ID_MASK = 0xFFFFF
        private const val PERSIST_COUNT_SHIFT = 20
        private const val MAX_EDITS = 10_000
        private const val DOCUMENT_REF_KEY = -1
        private const val TITLE = 0
        private const val BODY = 1
        private const val CENTER_TITLE = 6
        private const val WIDTH = 720f
        private const val HEIGHT = 540f
        private const val MARGIN = 36f
        private const val TITLE_HEIGHT = 90f
        private const val GAP = 18f
        private const val BODY_TOP = MARGIN + TITLE_HEIGHT + GAP
        private const val TITLE_SIZE = 36f
        private const val BODY_SIZE = 22f
        private const val WHITE = -0x1
    }
}
