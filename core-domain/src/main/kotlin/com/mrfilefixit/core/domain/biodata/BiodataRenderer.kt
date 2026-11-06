package com.mrfilefixit.core.domain.biodata

import com.mrfilefixit.core.domain.document.DocBlock
import com.mrfilefixit.core.domain.document.DocImage
import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.DocRun
import com.mrfilefixit.core.domain.document.DocTable
import com.mrfilefixit.core.domain.document.DocTableCell
import com.mrfilefixit.core.domain.document.DocTableRow
import com.mrfilefixit.core.domain.document.ListKind
import com.mrfilefixit.core.domain.document.PageDecoration
import com.mrfilefixit.core.domain.document.PageSetup
import com.mrfilefixit.core.domain.document.ParagraphStyle
import com.mrfilefixit.core.domain.document.RichDocument
import com.mrfilefixit.core.domain.document.TextAlign
import java.time.LocalDate

/**
 * Lays a [Biodata] out as a [RichDocument] (the same model the PDF and Word writers take). Only fields with
 * text that the user has not switched off are printed; empty sections disappear entirely.
 *
 * [photos] holds the JPEG bytes of [Biodata.photos] in the same order (null when a file went missing).
 */
object BiodataRenderer {

    fun render(
        biodata: Biodata,
        template: BiodataTemplate,
        photos: List<ByteArray?>,
        labels: BiodataLabels,
        today: LocalDate,
    ): RichDocument {
        val ctx = Context(template, labels)
        val pictures = biodata.photos.zip(photos).mapNotNull { (photo, bytes) -> bytes?.let { Picture(photo, it) } }
        val main = pictures.firstOrNull()
        val gallery = pictures.drop(1)
        val blocks = when (template.kind) {
            BiodataKind.MARRIAGE -> marriage(biodata, ctx, main, gallery, today)
            BiodataKind.PORTFOLIO -> portfolio(biodata, ctx, main, gallery)
        }
        val margin = if (template.border != null) FRAMED_MARGIN else MARGIN
        return RichDocument(
            blocks = blocks,
            page = PageSetup(
                marginTopPt = margin,
                marginBottomPt = margin,
                marginStartPt = margin,
                marginEndPt = margin,
            ),
            defaultFont = template.font,
            baseSizePt = BODY,
            textColor = TEXT,
            title = "${biodata.displayName} - ${labels.title}",
            author = biodata.name,
            decoration = template.border?.let { PageDecoration(it, template.accentArgb, template.secondaryArgb) },
        )
    }

    private class Context(val template: BiodataTemplate, val labels: BiodataLabels) {
        val accent: Int get() = template.accentArgb
        val secondary: Int get() = template.secondaryArgb
    }

    private class Picture(val photo: BiodataPhoto, val bytes: ByteArray) {
        val aspect: Float get() = photo.heightPx.toFloat() / photo.widthPx

        fun image(widthPt: Float, align: TextAlign = TextAlign.CENTER) =
            DocImage(bytes, JPEG, widthPt, widthPt * aspect, align)
    }

    // region marriage

    private fun marriage(
        biodata: Biodata,
        ctx: Context,
        main: Picture?,
        gallery: List<Picture>,
        today: LocalDate,
    ): List<DocBlock> {
        val blocks = mutableListOf<DocBlock>()
        if (biodata.invocation.isNotBlank()) {
            blocks += centered(biodata.invocation, ctx.secondary, BODY * INVOCATION_SCALE, italic = true)
        }
        blocks += centered(ctx.labels.title.uppercase(), ctx.secondary, BODY * TITLE_SCALE, bold = true)
        blocks += centered(biodata.displayName, ctx.accent, BODY * NAME_SCALE, bold = true, spaceAfter = NAME_GAP)

        val personal = personalRows(biodata, ctx, today)
        if (main != null) {
            val details = mutableListOf<DocBlock>()
            if (personal.isNotEmpty()) {
                details += heading(ctx.labels.section(BiodataSection.PERSONAL), ctx, center = false)
                personal.forEach { (label, value) -> details += labelled(label, value) }
            }
            val row = DocTableRow(
                listOf(
                    DocTableCell(details.ifEmpty { listOf(DocParagraph.of("")) }, paddingPt = 0f),
                    DocTableCell(listOf(main.image(MAIN_PHOTO_WIDTH, TextAlign.END)), paddingPt = 0f),
                ),
            )
            blocks += DocTable(listOf(row), listOf(DETAILS_WEIGHT, PHOTO_WEIGHT), borderArgb = null)
        } else if (personal.isNotEmpty()) {
            blocks += heading(ctx.labels.section(BiodataSection.PERSONAL), ctx)
            blocks += keyValueTable(personal, ctx)
        }

        listOf(
            BiodataSection.CULTURAL,
            BiodataSection.EDUCATION_CAREER,
            BiodataSection.FAMILY,
            BiodataSection.EXPECTATIONS,
            BiodataSection.CONTACT,
        ).forEach { section -> blocks += marriageSection(section, biodata, ctx) }

        if (gallery.isNotEmpty()) {
            blocks += heading(ctx.labels.photos, ctx)
            blocks += grid(gallery)
        }
        return blocks
    }

    private fun personalRows(biodata: Biodata, ctx: Context, today: LocalDate): List<Pair<String, String>> =
        BiodataSection.PERSONAL.fields(BiodataKind.MARRIAGE).mapNotNull { field ->
            val value = when {
                field == BiodataField.AGE && biodata.value(field).isBlank() && field !in biodata.hidden ->
                    BiodataAge.years(biodata.value(BiodataField.DATE_OF_BIRTH), today)?.let(ctx.labels::age)
                biodata.shows(field) -> biodata.value(field)
                else -> null
            }
            value?.let { ctx.labels.field(field) to it }
        }

    private fun marriageSection(section: BiodataSection, biodata: Biodata, ctx: Context): List<DocBlock> {
        val fields = section.fields(BiodataKind.MARRIAGE).filter(biodata::shows)
        if (fields.isEmpty()) return emptyList()
        val blocks = mutableListOf<DocBlock>(heading(ctx.labels.section(section), ctx))
        // A section that is a single paragraph (partner expectations) needs no label column.
        val single = fields.singleOrNull()
        if (single != null && single.style == FieldStyle.PARAGRAPH && section == BiodataSection.EXPECTATIONS) {
            blocks += paragraphs(biodata.value(single))
        } else {
            blocks += keyValueTable(fields.map { ctx.labels.field(it) to biodata.value(it) }, ctx)
        }
        return blocks
    }

    // endregion

    // region portfolio

    private fun portfolio(biodata: Biodata, ctx: Context, main: Picture?, gallery: List<Picture>): List<DocBlock> {
        val blocks = mutableListOf<DocBlock>()
        val identity = portfolioIdentity(biodata, ctx)
        if (main != null) {
            val row = DocTableRow(
                listOf(
                    DocTableCell(listOf(main.image(PROFILE_PHOTO_WIDTH, TextAlign.START)), paddingPt = 0f),
                    DocTableCell(identity, paddingPt = IDENTITY_PADDING),
                ),
            )
            blocks += DocTable(listOf(row), listOf(PROFILE_WEIGHT, IDENTITY_WEIGHT), borderArgb = null)
        } else {
            blocks += identity
        }

        if (biodata.shows(BiodataField.BIO)) {
            blocks += heading(ctx.labels.section(BiodataSection.ABOUT), ctx)
            blocks += paragraphs(biodata.value(BiodataField.BIO))
        }
        blocks += listSection(BiodataSection.SKILLS, listOf(BiodataField.SKILLS, BiodataField.EQUIPMENT), biodata, ctx)
        blocks += listSection(BiodataSection.SERVICES, listOf(BiodataField.SERVICES), biodata, ctx)
        blocks += experience(biodata, ctx)

        if (gallery.isNotEmpty()) {
            blocks += heading(ctx.labels.workSamples, ctx)
            if (ctx.template.photoLayout == PhotoLayout.SHOWCASE) {
                blocks += gallery.first().image(SHOWCASE_WIDTH)
                if (gallery.size > 1) blocks += grid(gallery.drop(1))
            } else {
                blocks += grid(gallery)
            }
        }
        return blocks
    }

    private fun portfolioIdentity(biodata: Biodata, ctx: Context): List<DocBlock> {
        val blocks = mutableListOf<DocBlock>()
        blocks += DocParagraph(
            listOf(DocRun(biodata.displayName, bold = true, sizePt = BODY * NAME_SCALE, colorArgb = ctx.accent)),
            ParagraphStyle(spaceAfterPt = SMALL_GAP),
        )
        if (biodata.tagline.isNotBlank()) {
            blocks += DocParagraph(
                listOf(DocRun(biodata.tagline, sizePt = BODY * TAGLINE_SCALE, colorArgb = ctx.secondary)),
                ParagraphStyle(spaceAfterPt = NAME_GAP),
            )
        }
        BiodataSection.CONTACT.fields(BiodataKind.PORTFOLIO).filter(biodata::shows).forEach { field ->
            blocks += labelled(ctx.labels.field(field), biodata.value(field), spaceAfter = TIGHT_GAP)
        }
        return blocks
    }

    private fun listSection(
        section: BiodataSection,
        fields: List<BiodataField>,
        biodata: Biodata,
        ctx: Context,
    ): List<DocBlock> {
        val shown = fields.filter(biodata::shows)
        if (shown.isEmpty()) return emptyList()
        val blocks = mutableListOf<DocBlock>(heading(ctx.labels.section(section), ctx))
        shown.forEach { field ->
            // With more than one list in a section, each gets its own small label.
            if (shown.size > 1) blocks += subLabel(ctx.labels.field(field), ctx)
            blocks += bullets(biodata.value(field))
        }
        return blocks
    }

    private fun experience(biodata: Biodata, ctx: Context): List<DocBlock> {
        val years = biodata.shows(BiodataField.YEARS_EXPERIENCE)
        val lists = listOf(BiodataField.CLIENTS, BiodataField.AWARDS).filter(biodata::shows)
        if (!years && lists.isEmpty()) return emptyList()
        val blocks = mutableListOf<DocBlock>(heading(ctx.labels.section(BiodataSection.EXPERIENCE), ctx))
        if (years) {
            blocks += labelled(
                ctx.labels.field(BiodataField.YEARS_EXPERIENCE),
                biodata.value(BiodataField.YEARS_EXPERIENCE),
            )
        }
        lists.forEach { field ->
            blocks += subLabel(ctx.labels.field(field), ctx)
            blocks += bullets(biodata.value(field))
        }
        return blocks
    }

    // endregion

    // region building blocks

    private fun heading(text: String, ctx: Context, center: Boolean? = null): DocParagraph {
        val style = ctx.template.heading
        val align = when {
            center != null -> if (center) TextAlign.CENTER else TextAlign.START
            style == BiodataHeading.ORNAMENTAL -> TextAlign.CENTER
            else -> TextAlign.START
        }
        return DocParagraph(
            listOf(DocRun(text, bold = true, sizePt = BODY * HEADING_SCALE, colorArgb = ctx.accent)),
            ParagraphStyle(
                heading = 2,
                align = align,
                spaceBeforePt = HEADING_BEFORE,
                spaceAfterPt = HEADING_AFTER,
                shadingArgb = if (style == BiodataHeading.BAND) tint(ctx.accent, BAND_TINT) else null,
                bottomBorderArgb = when (style) {
                    BiodataHeading.ORNAMENTAL -> ctx.secondary
                    BiodataHeading.RULE -> ctx.accent
                    BiodataHeading.BAND -> null
                },
                keepWithNext = true,
            ),
        )
    }

    private fun keyValueTable(rows: List<Pair<String, String>>, ctx: Context): DocTable = DocTable(
        rows = rows.mapIndexed { index, (label, value) ->
            val shade = if (index % 2 == 1) tint(ctx.accent, ROW_TINT) else null
            DocTableRow(
                listOf(
                    DocTableCell(
                        listOf(DocParagraph(listOf(DocRun(label, bold = true, colorArgb = LABEL)), TIGHT)),
                        shadingArgb = shade,
                        paddingPt = CELL_PADDING,
                    ),
                    DocTableCell(paragraphs(value, TIGHT), shadingArgb = shade, paddingPt = CELL_PADDING),
                ),
            )
        },
        columnWeights = listOf(LABEL_WEIGHT, VALUE_WEIGHT),
        borderArgb = null,
    )

    /** "Label: value" on one line, for the details beside a photo. */
    private fun labelled(label: String, value: String, spaceAfter: Float = SMALL_GAP) = DocParagraph(
        listOf(
            DocRun("$label: ", bold = true, colorArgb = LABEL),
            DocRun(value.lines().joinToString(", ") { it.trim() }.trim(',', ' ')),
        ),
        ParagraphStyle(spaceAfterPt = spaceAfter, lineSpacing = LINE_SPACING),
    )

    private fun subLabel(text: String, ctx: Context) = DocParagraph(
        listOf(DocRun(text, bold = true, colorArgb = ctx.secondary)),
        ParagraphStyle(spaceBeforePt = SMALL_GAP, spaceAfterPt = TIGHT_GAP, keepWithNext = true),
    )

    private fun paragraphs(text: String, style: ParagraphStyle = BODY_STYLE): List<DocParagraph> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { DocParagraph.of(it, style) }

    private fun bullets(text: String): List<DocParagraph> = listItems(text).map {
        DocParagraph.of(it, ParagraphStyle(list = ListKind.BULLET, spaceAfterPt = TIGHT_GAP))
    }

    /** One item per line; a single line with commas is split at the commas. */
    fun listItems(text: String): List<String> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val items = if (lines.size == 1 && lines.single().contains(',')) lines.single().split(',') else lines
        return items.map { it.trim().trimStart('-', '*', '•').trim() }.filter { it.isNotEmpty() }
    }

    /** Pictures two to a row; an odd last picture sits alone on the left. */
    private fun grid(pictures: List<Picture>): DocTable = DocTable(
        rows = pictures.chunked(2).map { pair ->
            DocTableRow(
                pair.map { DocTableCell(listOf(it.image(GRID_PHOTO_WIDTH)), paddingPt = GRID_PADDING) } +
                    List(2 - pair.size) { DocTableCell(emptyList(), paddingPt = GRID_PADDING) },
            )
        },
        columnWeights = listOf(1f, 1f),
        borderArgb = null,
    )

    private fun centered(
        text: String,
        color: Int,
        size: Float,
        bold: Boolean = false,
        italic: Boolean = false,
        spaceAfter: Float = SMALL_GAP,
    ) = DocParagraph(
        listOf(DocRun(text, bold = bold, italic = italic, sizePt = size, colorArgb = color)),
        ParagraphStyle(align = TextAlign.CENTER, spaceAfterPt = spaceAfter),
    )

    /** [color] mixed with white; [amount] 0 is white, 1 is the colour. */
    fun tint(color: Int, amount: Float): Int {
        fun mix(shift: Int): Int {
            val c = (color shr shift) and CHANNEL
            return (CHANNEL - (CHANNEL - c) * amount).toInt().coerceIn(0, CHANNEL) shl shift
        }
        return OPAQUE or mix(RED_SHIFT) or mix(GREEN_SHIFT) or mix(0)
    }

    // endregion

    private const val JPEG = "image/jpeg"
    private const val BODY = 10.5f
    private const val MARGIN = 40f
    private const val FRAMED_MARGIN = 48f
    private const val NAME_SCALE = 2f
    private const val TITLE_SCALE = 0.95f
    private const val INVOCATION_SCALE = 1.05f
    private const val TAGLINE_SCALE = 1.15f
    private const val HEADING_SCALE = 1.15f
    private const val HEADING_BEFORE = 10f
    private const val HEADING_AFTER = 5f
    private const val NAME_GAP = 10f
    private const val SMALL_GAP = 4f
    private const val TIGHT_GAP = 2f
    private const val LINE_SPACING = 1.2f
    private const val CELL_PADDING = 3f
    private const val GRID_PADDING = 4f
    private const val IDENTITY_PADDING = 6f
    private const val MAIN_PHOTO_WIDTH = 150f
    private const val PROFILE_PHOTO_WIDTH = 150f
    private const val GRID_PHOTO_WIDTH = 240f
    private const val SHOWCASE_WIDTH = 480f
    private const val DETAILS_WEIGHT = 0.66f
    private const val PHOTO_WEIGHT = 0.34f
    private const val PROFILE_WEIGHT = 0.32f
    private const val IDENTITY_WEIGHT = 0.68f
    private const val LABEL_WEIGHT = 0.38f
    private const val VALUE_WEIGHT = 0.62f
    private const val BAND_TINT = 0.14f
    private const val ROW_TINT = 0.05f
    private const val TEXT = -0xe5e3dd // 0xFF1A1C23
    private const val LABEL = -0xc8c4b8 // 0xFF373B48
    private const val CHANNEL = 255
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val OPAQUE = -0x1000000

    private val TIGHT = ParagraphStyle(spaceAfterPt = TIGHT_GAP, lineSpacing = LINE_SPACING)
    private val BODY_STYLE = ParagraphStyle(spaceAfterPt = SMALL_GAP, lineSpacing = LINE_SPACING)
}
