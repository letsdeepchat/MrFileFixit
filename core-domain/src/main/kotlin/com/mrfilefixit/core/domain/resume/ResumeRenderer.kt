package com.mrfilefixit.core.domain.resume

import com.mrfilefixit.core.domain.document.DocBlock
import com.mrfilefixit.core.domain.document.DocImage
import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.DocRun
import com.mrfilefixit.core.domain.document.DocTable
import com.mrfilefixit.core.domain.document.DocTableCell
import com.mrfilefixit.core.domain.document.DocTableRow
import com.mrfilefixit.core.domain.document.ListKind
import com.mrfilefixit.core.domain.document.PageSetup
import com.mrfilefixit.core.domain.document.ParagraphStyle
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.domain.document.TextAlign

/**
 * Lays a [Resume] out as a [RichDocument], which the PDF and DOCX writers both take.
 *
 * The output follows common ATS rules: one column of selectable text in reading order, real headings,
 * real bullet lists, contact details as text (not in a header or picture), and standard section names
 * chosen by the user. The optional photo sits in a borderless two-cell row beside the name, which parsers
 * read as the name block followed by an image.
 */
object ResumeRenderer {

    fun render(resume: Resume, template: ResumeTemplate, photoJpeg: ByteArray? = null): RichDocument {
        val style = resume.style
        val body = style.bodySizePt.coerceIn(ResumeStyle.MIN_BODY_SIZE, ResumeStyle.MAX_BODY_SIZE)
        val ctx = Context(template, style.accentArgb, body)
        val blocks = mutableListOf<DocBlock>()
        blocks += header(resume.contact, ctx, photoJpeg)
        resume.sections.filter { it.visible && it.hasContent() }.forEach { blocks += section(it, ctx) }
        return RichDocument(
            blocks = blocks,
            page = PageSetup(
                widthPt = style.paper.widthPt,
                heightPt = style.paper.heightPt,
                marginTopPt = MARGIN,
                marginBottomPt = MARGIN,
                marginStartPt = MARGIN,
                marginEndPt = MARGIN,
            ),
            defaultFont = style.font.family,
            baseSizePt = body,
            textColor = TEXT,
            title = resume.displayName,
            author = resume.contact.fullName,
            appData = ResumeJson.encode(resume),
        )
    }

    private class Context(val template: ResumeTemplate, val accent: Int, val body: Float)

    // region header

    private fun header(contact: ContactInfo, ctx: Context, photo: ByteArray?): List<DocBlock> {
        val centered = ctx.template.header == HeaderLayout.CENTERED && photo == null
        val align = if (centered) TextAlign.CENTER else TextAlign.START
        val lines = headerLines(contact, ctx, align)
        if (photo == null) return lines
        val image = DocImage(photo, "image/jpeg", PHOTO_WIDTH, PHOTO_WIDTH * PHOTO_ASPECT, TextAlign.END)
        val row = DocTableRow(
            listOf(
                DocTableCell(lines, paddingPt = 0f),
                DocTableCell(listOf(image), paddingPt = 0f),
            ),
        )
        return listOf(DocTable(listOf(row), columnWeights = listOf(TEXT_WEIGHT, PHOTO_WEIGHT), borderArgb = null))
    }

    private fun headerLines(contact: ContactInfo, ctx: Context, align: TextAlign): List<DocBlock> {
        val band = ctx.template.header == HeaderLayout.BAND
        val blocks = mutableListOf<DocBlock>()
        val name = contact.fullName.ifBlank { Resume.DEFAULT_NAME }
        blocks += DocParagraph(
            listOf(
                DocRun(
                    name,
                    bold = true,
                    sizePt = (ctx.body * NAME_SCALE).coerceAtMost(NAME_MAX),
                    colorArgb = if (band) WHITE else ctx.accent,
                ),
            ),
            ParagraphStyle(
                heading = 1,
                align = align,
                spaceAfterPt = if (band) BAND_GAP else 2f,
                shadingArgb = if (band) ctx.accent else null,
                lineSpacing = 1f,
            ),
        )
        if (contact.headline.isNotBlank()) {
            blocks += DocParagraph(
                listOf(DocRun(contact.headline.trim(), sizePt = ctx.body * HEADLINE_SCALE, colorArgb = MUTED)),
                ParagraphStyle(align = align, spaceAfterPt = 2f),
            )
        }
        val runs = contactRuns(contact)
        if (runs.isNotEmpty()) {
            blocks += DocParagraph(runs, ParagraphStyle(align = align, spaceAfterPt = 4f))
        }
        return blocks
    }

    /** Email, phone, location and links on one line, separated by " | ", with real links for email and URLs. */
    private fun contactRuns(contact: ContactInfo): List<DocRun> {
        val parts = buildList {
            contact.email.trim().takeIf { it.isNotEmpty() }?.let { add(DocRun(it, link = "mailto:$it")) }
            contact.phone.trim().takeIf { it.isNotEmpty() }?.let { add(DocRun(it)) }
            contact.location.trim().takeIf { it.isNotEmpty() }?.let { add(DocRun(it)) }
            contact.links.map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(DocRun(it, link = webLink(it))) }
        }
        return parts.flatMapIndexed { index, run -> if (index == 0) listOf(run) else listOf(DocRun(SEPARATOR), run) }
    }

    private fun webLink(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.startsWith("https://") -> text
            lower.startsWith("http://") -> "https://" + text.substring("http://".length)
            ' ' !in text && '.' in text -> "https://$text"
            else -> null
        }
    }

    // endregion

    // region sections

    private fun ResumeSection.hasContent(): Boolean =
        text.isNotBlank() || entries.any { it.hasContent() }

    private fun ResumeEntry.hasContent(): Boolean =
        title.isNotBlank() || subtitle.isNotBlank() || dates.isNotBlank() || bullets.any { it.isNotBlank() }

    private fun section(section: ResumeSection, ctx: Context): List<DocBlock> {
        val blocks = mutableListOf<DocBlock>(heading(section.title, ctx))
        paragraphs(section.text).forEach { blocks += DocParagraph.of(it, ParagraphStyle(spaceAfterPt = 4f)) }
        val entries = section.entries.filter { it.hasContent() }
        when (section.kind) {
            SectionKind.SKILLS -> entries.forEach { blocks += skillLine(it) }
            else -> entries.forEach { blocks += entry(it) }
        }
        return blocks
    }

    private fun heading(title: String, ctx: Context): DocParagraph {
        val text = title.trim().ifEmpty { " " }
        val style = ctx.template.heading
        val run = DocRun(
            if (style == HeadingStyle.CAPS) text.uppercase() else text,
            bold = true,
            sizePt = ctx.body * if (style == HeadingStyle.CAPS) CAPS_SCALE else HEADING_SCALE,
            colorArgb = ctx.accent,
        )
        return DocParagraph(
            listOf(run),
            ParagraphStyle(
                heading = 2,
                spaceBeforePt = HEADING_BEFORE,
                spaceAfterPt = HEADING_AFTER,
                lineSpacing = 1f,
                bottomBorderArgb = if (style == HeadingStyle.RULE) ctx.accent else null,
                shadingArgb = if (style == HeadingStyle.BAND) tint(ctx.accent, BAND_TINT) else null,
                keepWithNext = true,
            ),
        )
    }

    /** Title and organisation in bold on the first line, place and dates below, then the bullets. */
    private fun entry(entry: ResumeEntry): List<DocBlock> {
        val blocks = mutableListOf<DocBlock>()
        val bullets = ResumeEdits.bulletsFromText(entry.bullets.joinToString("\n"))
        val meta = listOf(entry.location, entry.dates).map { it.trim() }.filter { it.isNotEmpty() }
        val firstRuns = buildList {
            if (entry.title.isNotBlank()) add(DocRun(entry.title.trim(), bold = true))
            if (entry.subtitle.isNotBlank()) {
                if (isNotEmpty()) add(DocRun(SEPARATOR))
                add(DocRun(entry.subtitle.trim(), bold = entry.title.isBlank()))
            }
        }
        val keep = meta.isNotEmpty() || bullets.isNotEmpty()
        if (firstRuns.isNotEmpty()) {
            blocks += DocParagraph(
                firstRuns,
                ParagraphStyle(
                    spaceBeforePt = ENTRY_BEFORE,
                    spaceAfterPt = if (keep) 1f else ENTRY_AFTER,
                    keepWithNext = keep,
                ),
            )
        }
        if (meta.isNotEmpty()) {
            blocks += DocParagraph(
                listOf(DocRun(meta.joinToString(SEPARATOR), italic = true, colorArgb = MUTED)),
                ParagraphStyle(
                    spaceAfterPt = if (bullets.isEmpty()) ENTRY_AFTER else 2f,
                    keepWithNext = bullets.isNotEmpty(),
                ),
            )
        }
        bullets.forEachIndexed { index, text ->
            blocks += DocParagraph(
                listOf(DocRun(text)),
                ParagraphStyle(
                    list = ListKind.BULLET,
                    spaceAfterPt = if (index == bullets.lastIndex) ENTRY_AFTER else 1f,
                ),
            )
        }
        return blocks
    }

    /** "Languages: Kotlin, Java, SQL" with the group name in bold. */
    private fun skillLine(entry: ResumeEntry): DocParagraph {
        val skills = ResumeEdits.skillsFromText(entry.bullets.joinToString("\n")).joinToString(", ")
        val runs = buildList {
            if (entry.title.isNotBlank()) {
                add(DocRun(entry.title.trim() + if (skills.isEmpty()) "" else ": ", bold = true))
            }
            if (skills.isNotEmpty()) add(DocRun(skills))
        }
        return DocParagraph(runs, ParagraphStyle(spaceAfterPt = 3f))
    }

    private fun paragraphs(text: String): List<String> =
        text.split(Regex("\\n\\s*\\n")).map { it.trim().replace(Regex("\\s*\\n\\s*"), " ") }.filter { it.isNotEmpty() }

    /** [color] mixed with white; [amount] 0 is white, 1 is the colour itself. */
    fun tint(color: Int, amount: Float): Int {
        fun mix(shift: Int): Int {
            val c = (color shr shift) and BYTE
            return (BYTE - (BYTE - c) * amount).toInt().coerceIn(0, BYTE) shl shift
        }
        return OPAQUE or mix(RED_SHIFT) or mix(GREEN_SHIFT) or mix(0)
    }

    // endregion

    private const val SEPARATOR = " | "
    private const val MARGIN = 42f
    private const val NAME_SCALE = 2.2f
    private const val NAME_MAX = 28f
    private const val HEADLINE_SCALE = 1.15f
    private const val HEADING_SCALE = 1.25f
    private const val CAPS_SCALE = 1.1f
    private const val HEADING_BEFORE = 10f
    private const val HEADING_AFTER = 4f
    private const val ENTRY_BEFORE = 4f
    private const val ENTRY_AFTER = 5f
    private const val BAND_GAP = 6f
    private const val BAND_TINT = 0.14f

    /** 413 × 531 px photo frame, 85 pt wide (about 3 cm). */
    private const val PHOTO_WIDTH = 85f
    private const val PHOTO_ASPECT = 531f / 413f
    private const val TEXT_WEIGHT = 0.8f
    private const val PHOTO_WEIGHT = 0.2f

    private const val TEXT = -0xdedede // 0xFF212122
    private const val MUTED = -0xaaaaab // 0xFF555555
    private const val WHITE = -0x1
    private const val OPAQUE = -0x1000000
    private const val BYTE = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
}
