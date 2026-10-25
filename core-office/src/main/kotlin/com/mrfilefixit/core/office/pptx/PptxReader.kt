package com.mrfilefixit.core.office.pptx

import com.mrfilefixit.core.domain.document.DocParagraph
import com.mrfilefixit.core.domain.document.DocRun
import com.mrfilefixit.core.domain.document.FrameRect
import com.mrfilefixit.core.domain.document.ListKind
import com.mrfilefixit.core.domain.document.ParagraphStyle
import com.mrfilefixit.core.domain.document.Presentation
import com.mrfilefixit.core.domain.document.Slide
import com.mrfilefixit.core.domain.document.SlideShape
import com.mrfilefixit.core.domain.document.TextAlign
import com.mrfilefixit.core.domain.document.VerticalAnchor
import com.mrfilefixit.core.office.OfficeFormatException
import com.mrfilefixit.core.office.OfficeLimitException
import com.mrfilefixit.core.office.OfficeLimits
import com.mrfilefixit.core.office.docx.DocxReader
import com.mrfilefixit.core.office.xml.XmlReader
import com.mrfilefixit.core.office.zip.OpcPackage
import com.mrfilefixit.core.office.zip.Relationship
import java.io.File

/**
 * Reads a .pptx into a [Presentation]: text boxes and placeholders (with positions inherited from the
 * layout and master), pictures, filled shapes, tables, groups and slide backgrounds, including the
 * decorative shapes of the layout and master. Charts, SmartArt, gradients, effects and animations are
 * not carried over.
 */
class PptxReader(private val limits: OfficeLimits = OfficeLimits.DEFAULT) {

    fun read(file: File): Presentation = OpcPackage(file, limits).use { pkg ->
        val main = pkg.rootPart("/officeDocument") ?: throw OfficeFormatException("Not a presentation")
        val rels = pkg.relationships(main)
        val xml = pkg.readXml(main) ?: throw OfficeFormatException("Missing presentation part")
        var width = Presentation.WIDE_WIDTH
        var height = Presentation.WIDE_HEIGHT
        val slideParts = mutableListOf<String>()
        while (xml.next() != XmlReader.Event.EOF) {
            if (xml.event != XmlReader.Event.START) continue
            when (xml.localName) {
                "sldSz" -> {
                    width = emu(xml.attr("cx")) ?: width
                    height = emu(xml.attr("cy")) ?: height
                }
                "sldId" -> xml.attr(R_NS, "id")?.let { rels[it] }?.let { slideParts += it.target }
            }
        }
        if (slideParts.size > limits.maxSlides) throw OfficeLimitException("Too many slides")
        val cache = mutableMapOf<String, PartInfo>()
        val slides = slideParts.map { readSlide(pkg, it, cache) }.map { slide ->
            slide.copy(shapes = slide.shapes.map { shape -> stretchBackground(shape, width, height) })
        }
        Presentation(width, height, slides)
    }

    /** What a layout or master contributes: placeholder frames, decorative shapes, background, theme. */
    private class PartInfo(
        val placeholders: Map<String, Placeholder>,
        val shapes: List<SlideShape>,
        val background: SlideShape?,
        val backgroundArgb: Int?,
        val theme: Theme,
        val parent: PartInfo?,
        val titleSize: Float?,
        val bodySizes: Map<Int, Float>,
    ) {
        /** The nearest placeholder that has a frame: matched by index first, then by type, then up the chain. */
        fun placeholder(key: PlaceholderKey): Placeholder? {
            val own = listOfNotNull(key.byIdx?.let { placeholders[it] }, placeholders[key.byType])
            return own.firstOrNull { it.frame != null } ?: parent?.placeholder(key) ?: own.firstOrNull()
        }

        fun backgroundFill(): Pair<Int?, SlideShape?> =
            if (backgroundArgb != null || background != null) {
                backgroundArgb to background
            } else {
                parent?.backgroundFill() ?: (null to null)
            }

        fun allShapes(): List<SlideShape> = parent?.allShapes().orEmpty() + shapes

        val rootTitleSize: Float? get() = titleSize ?: parent?.rootTitleSize
        fun bodySize(level: Int): Float? = bodySizes[level] ?: parent?.bodySize(level)
    }

    private class Placeholder(val frame: FrameRect?, val anchor: VerticalAnchor?)

    private data class PlaceholderKey(val type: String, val idx: String?) {
        val byIdx: String? get() = idx?.let { "idx:$it" }
        val byType: String get() = "type:$type"
        val isTitle: Boolean get() = type == "title" || type == "ctrTitle"
        val hasBullets: Boolean get() = type == "body" || type == "obj"
    }

    private class Ctx(
        val pkg: OpcPackage,
        val rels: Map<String, Relationship>,
        val theme: Theme,
        val inherited: PartInfo?,
    )

    private fun readSlide(pkg: OpcPackage, part: String, cache: MutableMap<String, PartInfo>): Slide {
        val rels = pkg.relationships(part)
        val layout = rels.values.firstOrNull {
            it.type.endsWith(
                "/slideLayout",
            )
        }?.let { readLayoutChain(pkg, it.target, cache) }
        val theme = layout?.theme ?: Theme.DEFAULT
        val ctx = Ctx(pkg, rels, theme, layout)
        val parsed = readShapeTree(pkg.readXml(part) ?: throw OfficeFormatException("Missing slide"), ctx)
        val (inheritedArgb, inheritedPicture) = layout?.backgroundFill() ?: (null to null)
        val backgroundArgb = parsed.backgroundArgb ?: inheritedArgb.takeIf { parsed.background == null }
        val background = parsed.background ?: inheritedPicture.takeIf { parsed.backgroundArgb == null }
        val inheritedShapes = if (parsed.showMasterShapes) layout?.allShapes().orEmpty() else emptyList()
        return Slide(backgroundArgb ?: WHITE, listOfNotNull(background) + inheritedShapes + parsed.shapes)
    }

    private fun readLayoutChain(pkg: OpcPackage, part: String, cache: MutableMap<String, PartInfo>): PartInfo =
        cache.getOrPut(part) {
            val rels = pkg.relationships(part)
            val master = rels.values.firstOrNull { it.type.endsWith("/slideMaster") }
                ?.let { readLayoutChain(pkg, it.target, cache) }
            val theme = rels.values.firstOrNull {
                it.type.endsWith("/theme")
            }?.let { rel -> pkg.readXml(rel.target)?.let(Theme::read) }
                ?: master?.theme ?: Theme.DEFAULT
            val ctx = Ctx(pkg, rels, theme, master)
            val parsed = readShapeTree(
                pkg.readXml(part) ?: throw OfficeFormatException("Missing layout"),
                ctx,
                template = true,
            )
            PartInfo(
                placeholders = parsed.placeholders,
                shapes = parsed.shapes,
                background = parsed.background,
                backgroundArgb = parsed.backgroundArgb,
                theme = theme,
                parent = master,
                titleSize = parsed.titleSize,
                bodySizes = parsed.bodySizes,
            )
        }

    private class ParsedTree {
        val shapes = mutableListOf<SlideShape>()
        val placeholders = mutableMapOf<String, Placeholder>()
        var background: SlideShape? = null
        var backgroundArgb: Int? = null
        var showMasterShapes = true
        var titleSize: Float? = null
        val bodySizes = mutableMapOf<Int, Float>()
    }

    /** Reads a slide, layout or master part. On templates, placeholders are recorded rather than drawn. */
    private fun readShapeTree(xml: XmlReader, ctx: Ctx, template: Boolean = false): ParsedTree {
        val tree = ParsedTree()
        while (xml.next() != XmlReader.Event.EOF) {
            if (xml.event != XmlReader.Event.START) continue
            when {
                xml.depth == 1 -> tree.showMasterShapes = xml.attr("showMasterSp") != "0"
                xml.localName == "bg" -> readBackground(xml, ctx, tree)
                xml.localName == "spTree" -> xml.forEachChild { readShape(it, ctx, tree, template, Transform.IDENTITY) }
                xml.localName == "titleStyle" -> tree.titleSize = firstLevelSize(xml)[1]
                xml.localName == "bodyStyle" -> tree.bodySizes += firstLevelSize(xml)
            }
        }
        return tree
    }

    private fun firstLevelSize(xml: XmlReader): Map<Int, Float> {
        val sizes = mutableMapOf<Int, Float>()
        xml.forEachChild { lvl ->
            val level = lvl.localName.removePrefix("lvl").removeSuffix("pPr").toIntOrNull()
            lvl.forEachChild {
                if (it.localName == "defRPr" && level != null) {
                    it.attr(
                        "sz",
                    )?.toFloatOrNull()?.let { sz -> sizes[level] = sz / HUNDREDTHS }
                }
            }
        }
        return sizes
    }

    private fun readBackground(xml: XmlReader, ctx: Ctx, tree: ParsedTree) {
        xml.forEachChild { c ->
            when (c.localName) {
                "bgPr" -> c.forEachChild { fill ->
                    when (fill.localName) {
                        "solidFill" -> tree.backgroundArgb = readColor(fill, ctx.theme)
                        "blipFill" -> tree.background = readBlip(fill, ctx)?.let { (bytes, mime) ->
                            SlideShape.Picture(FrameRect(0f, 0f, FULL, FULL), bytes, mime)
                        }
                    }
                }
                "bgRef" -> tree.backgroundArgb = readColor(c, ctx.theme)
            }
        }
    }

    private fun readShape(xml: XmlReader, ctx: Ctx, tree: ParsedTree, template: Boolean, transform: Transform) {
        when (xml.localName) {
            "sp", "cxnSp" -> readSp(xml, ctx, tree, template, transform)
            "pic" -> readPicture(xml, ctx, transform)?.let { tree.shapes += it }
            "grpSp" -> readGroup(xml, ctx, tree, template, transform)
            "graphicFrame" -> tree.shapes += readGraphicFrame(xml, ctx, transform)
            "AlternateContent" -> {
                var done = false
                xml.forEachChild { c ->
                    if (!done && c.localName == "Choice") {
                        done = true
                        c.forEachChild { readShape(it, ctx, tree, template, transform) }
                    }
                }
            }
            else -> xml.skipElement()
        }
    }

    private class ShapeProps {
        var frame: FrameRect? = null
        var fill: Int? = null
        var line: Int? = null
        var placeholder: PlaceholderKey? = null
        var anchor: VerticalAnchor? = null
        var paragraphs: List<DocParagraph> = emptyList()
        var levels: List<Int> = emptyList()
    }

    private fun readSp(xml: XmlReader, ctx: Ctx, tree: ParsedTree, template: Boolean, transform: Transform) {
        val props = ShapeProps()
        xml.forEachChild { c ->
            when (c.localName) {
                "nvSpPr", "nvCxnSpPr" -> props.placeholder = readPlaceholder(c)
                "spPr" -> readShapeProperties(c, ctx.theme, props, transform)
                "txBody" -> readTextBody(c, ctx.theme, props)
            }
        }
        val key = props.placeholder
        if (template && key != null) {
            val placeholder = Placeholder(props.frame, props.anchor)
            key.byIdx?.let { tree.placeholders[it] = placeholder }
            tree.placeholders.putIfAbsent(key.byType, placeholder)
            return
        }
        val inherited = key?.let { ctx.inherited?.placeholder(it) }
        val frame = props.frame ?: inherited?.frame ?: return
        val hasText = props.paragraphs.any { it.plainText.isNotBlank() }
        if (!hasText) {
            if (props.fill != null || props.line != null) tree.shapes += SlideShape.Box(frame, props.fill, props.line)
            return
        }
        val defaultSize = when {
            key?.isTitle == true -> ctx.inherited?.rootTitleSize ?: TITLE_SIZE
            key != null -> ctx.inherited?.bodySize(1) ?: BODY_SIZE
            else -> SlideShape.DEFAULT_TEXT_SIZE
        }
        val defaultAnchor = if (key?.isTitle == true) VerticalAnchor.MIDDLE else VerticalAnchor.TOP
        tree.shapes += SlideShape.TextBox(
            frame = frame,
            paragraphs = inheritParagraphStyles(props, key, ctx),
            fillArgb = props.fill,
            lineArgb = props.line,
            anchor = props.anchor ?: inherited?.anchor ?: defaultAnchor,
            defaultSizePt = defaultSize,
        )
    }

    /** Applies the placeholder's inherited bullets and per-level sizes to paragraphs that do not set their own. */
    private fun inheritParagraphStyles(props: ShapeProps, key: PlaceholderKey?, ctx: Ctx): List<DocParagraph> =
        props.paragraphs.mapIndexed { i, p ->
            val level = props.levels.getOrElse(i) { 0 }
            val explicitNone = p.style.keepWithNext
            val bullets = key?.hasBullets == true && p.style.list == ListKind.NONE && !explicitNone
            val levelSize = if (key != null && !key.isTitle) ctx.inherited?.bodySize(level + 1) else null
            val sized = p.runs.map { run ->
                if (run.sizePt == null && levelSize != null) {
                    run.copy(
                        sizePt = levelSize,
                    )
                } else {
                    run
                }
            }
            val style = p.style.copy(keepWithNext = false)
            p.copy(runs = sized, style = if (bullets) style.copy(list = ListKind.BULLET, listLevel = level) else style)
        }

    private fun readPlaceholder(xml: XmlReader): PlaceholderKey? {
        var key: PlaceholderKey? = null
        xml.forEachChild { c ->
            if (c.localName == "nvPr") {
                c.forEachChild { ph ->
                    if (ph.localName == "ph") key = PlaceholderKey(ph.attr("type") ?: "body", ph.attr("idx"))
                }
            }
        }
        return key
    }

    private fun readShapeProperties(xml: XmlReader, theme: Theme, props: ShapeProps, transform: Transform) {
        xml.forEachChild { c ->
            when (c.localName) {
                "xfrm" -> props.frame = readXfrm(c)?.let(transform::apply)
                "solidFill" -> props.fill = readColor(c, theme)
                "ln" -> c.forEachChild { if (it.localName == "solidFill") props.line = readColor(it, theme) }
            }
        }
    }

    private fun readXfrm(xml: XmlReader): FrameRect? {
        var x: Float? = null
        var y: Float? = null
        var w: Float? = null
        var h: Float? = null
        xml.forEachChild { c ->
            when (c.localName) {
                "off" -> {
                    x = emu(c.attr("x"))
                    y = emu(c.attr("y"))
                }
                "ext" -> {
                    w = emu(c.attr("cx"))
                    h = emu(c.attr("cy"))
                }
            }
        }
        val fx = x ?: return null
        val fy = y ?: return null
        return FrameRect(fx, fy, w ?: 0f, h ?: 0f)
    }

    private fun readTextBody(xml: XmlReader, theme: Theme, props: ShapeProps) {
        val paragraphs = mutableListOf<DocParagraph>()
        val levels = mutableListOf<Int>()
        xml.forEachChild { c ->
            when (c.localName) {
                "bodyPr" -> props.anchor = when (c.attr("anchor")) {
                    "ctr" -> VerticalAnchor.MIDDLE
                    "b" -> VerticalAnchor.BOTTOM
                    "t" -> VerticalAnchor.TOP
                    else -> null
                }
                "p" -> {
                    val (paragraph, level) = readParagraph(c, theme)
                    paragraphs += paragraph
                    levels += level
                }
            }
        }
        props.paragraphs = paragraphs
        props.levels = levels
    }

    /** A paragraph and its outline level. `keepWithNext` marks an explicit "no bullet" (a:buNone). */
    private fun readParagraph(xml: XmlReader, theme: Theme): Pair<DocParagraph, Int> {
        val runs = mutableListOf<DocRun>()
        var style = ParagraphStyle(spaceAfterPt = PARAGRAPH_GAP)
        var level = 0
        xml.forEachChild { c ->
            when (c.localName) {
                "pPr" -> {
                    level = c.attr("lvl")?.toIntOrNull()?.coerceIn(0, MAX_LEVEL) ?: 0
                    style = style.copy(align = alignment(c.attr("algn")))
                    c.forEachChild { b ->
                        style = when (b.localName) {
                            "buNone" -> style.copy(keepWithNext = true)
                            "buChar" -> style.copy(list = ListKind.BULLET, listLevel = level)
                            "buAutoNum" -> style.copy(list = ListKind.NUMBERED, listLevel = level)
                            else -> style
                        }
                    }
                }
                "r", "fld" -> readRun(c, theme)?.let { runs += it }
                "br" -> runs += DocRun("\n")
            }
        }
        return DocParagraph(DocxReader.mergeRuns(runs), style) to level
    }

    private fun readRun(xml: XmlReader, theme: Theme): DocRun? {
        var run = DocRun("")
        val text = StringBuilder()
        xml.forEachChild { c ->
            when (c.localName) {
                "rPr" -> {
                    run = run.copy(
                        bold = c.attr("b") == "1",
                        italic = c.attr("i") == "1",
                        underline = c.attr("u").let { it != null && it != "none" },
                        sizePt = c.attr("sz")?.toFloatOrNull()?.div(HUNDREDTHS),
                    )
                    c.forEachChild { p ->
                        when (p.localName) {
                            "solidFill" -> run = run.copy(colorArgb = readColor(p, theme))
                            "latin" -> run = run.copy(font = DocxReader.fontFamily(p.attr("typeface")))
                        }
                    }
                }
                "t" -> text.append(c.readElementText())
            }
        }
        return if (text.isEmpty()) null else run.copy(text = text.toString())
    }

    private fun readPicture(xml: XmlReader, ctx: Ctx, transform: Transform): SlideShape? {
        var frame: FrameRect? = null
        var image: Pair<ByteArray, String>? = null
        xml.forEachChild { c ->
            when (c.localName) {
                "blipFill" -> image = readBlip(c, ctx)
                "spPr" -> c.forEachChild { if (it.localName == "xfrm") frame = readXfrm(it)?.let(transform::apply) }
            }
        }
        val (bytes, mime) = image ?: return null
        return frame?.let { SlideShape.Picture(it, bytes, mime) }
    }

    private fun readBlip(xml: XmlReader, ctx: Ctx): Pair<ByteArray, String>? {
        var result: Pair<ByteArray, String>? = null
        xml.forEachChild { c ->
            if (c.localName == "blip") {
                val rel = c.attr(R_NS, "embed")?.let { ctx.rels[it] }?.takeIf { !it.external }
                val mime = rel?.let { DocxReader.imageMime(it.target) }
                val bytes = rel?.let { ctx.pkg.read(it.target) }
                if (mime != null && bytes != null) result = bytes to mime
            }
        }
        return result
    }

    private fun readGroup(xml: XmlReader, ctx: Ctx, tree: ParsedTree, template: Boolean, transform: Transform) {
        var inner = transform
        xml.forEachChild { c ->
            when (c.localName) {
                "grpSpPr" -> c.forEachChild {
                    if (it.localName == "xfrm") inner = transform.then(readGroupTransform(it))
                }
                "nvGrpSpPr" -> Unit
                else -> readShape(c, ctx, tree, template, inner)
            }
        }
    }

    private fun readGroupTransform(xml: XmlReader): Transform {
        val values = mutableMapOf<String, Float>()
        xml.forEachChild { c ->
            when (c.localName) {
                "off" -> {
                    values["x"] = emu(c.attr("x")) ?: 0f
                    values["y"] = emu(c.attr("y")) ?: 0f
                }
                "ext" -> {
                    values["cx"] = emu(c.attr("cx")) ?: 0f
                    values["cy"] = emu(c.attr("cy")) ?: 0f
                }
                "chOff" -> {
                    values["chx"] = emu(c.attr("x")) ?: 0f
                    values["chy"] = emu(c.attr("y")) ?: 0f
                }
                "chExt" -> {
                    values["chcx"] = emu(c.attr("cx")) ?: 0f
                    values["chcy"] = emu(c.attr("cy")) ?: 0f
                }
            }
        }
        val sx = ratio(values["cx"], values["chcx"])
        val sy = ratio(values["cy"], values["chcy"])
        return Transform(
            scaleX = sx,
            scaleY = sy,
            dx = (values["x"] ?: 0f) - (values["chx"] ?: 0f) * sx,
            dy = (values["y"] ?: 0f) - (values["chy"] ?: 0f) * sy,
        )
    }

    /** Tables become one text box per cell, laid out on the table grid. */
    private fun readGraphicFrame(xml: XmlReader, ctx: Ctx, transform: Transform): List<SlideShape> {
        var frame: FrameRect? = null
        val shapes = mutableListOf<SlideShape>()
        xml.forEachChild { c ->
            when (c.localName) {
                "xfrm" -> frame = readXfrm(c)?.let(transform::apply)
                "graphic" -> c.forEachChild { data ->
                    data.forEachChild { tbl ->
                        val origin = frame
                        if (tbl.localName == "tbl" && origin != null) {
                            shapes += readTable(tbl, ctx.theme, origin, transform)
                        }
                    }
                }
            }
        }
        return shapes
    }

    private fun readTable(xml: XmlReader, theme: Theme, origin: FrameRect, transform: Transform): List<SlideShape> {
        val columns = mutableListOf<Float>()
        val shapes = mutableListOf<SlideShape>()
        var y = origin.y
        xml.forEachChild { c ->
            when (c.localName) {
                "tblGrid" -> c.forEachChild { g -> emu(g.attr("w"))?.let { columns += it * transform.scaleX } }
                "tr" -> {
                    val rowHeight = (emu(c.attr("h")) ?: MIN_ROW) * transform.scaleY
                    var x = origin.x
                    var col = 0
                    c.forEachChild { tc ->
                        if (tc.localName != "tc") return@forEachChild
                        val width = columns.getOrElse(col) { 0f }
                        val props = ShapeProps()
                        tc.forEachChild { part ->
                            when (part.localName) {
                                "txBody" -> readTextBody(part, theme, props)
                                "tcPr" -> part.forEachChild {
                                    if (it.localName == "solidFill") props.fill = readColor(it, theme)
                                }
                            }
                        }
                        shapes += SlideShape.TextBox(
                            FrameRect(x, y, width, rowHeight),
                            props.paragraphs,
                            fillArgb = props.fill,
                            lineArgb = TABLE_LINE,
                            defaultSizePt = TABLE_TEXT,
                        )
                        x += width
                        col++
                    }
                    y += rowHeight
                }
            }
        }
        return shapes
    }

    private fun readColor(xml: XmlReader, theme: Theme): Int? {
        var color: Int? = null
        xml.forEachChild { c ->
            val base = when (c.localName) {
                "srgbClr" -> DocxReader.parseColor(c.attr("val"))
                "sysClr" -> DocxReader.parseColor(c.attr("lastClr"))
                "schemeClr" -> theme.color(c.attr("val"))
                "prstClr" -> PRESET_COLORS[c.attr("val")]
                else -> null
            }
            if (base != null) {
                var lumMod = 1f
                var lumOff = 0f
                c.forEachChild { m ->
                    val v = m.attr("val")?.toFloatOrNull()?.div(PERCENT_UNITS) ?: return@forEachChild
                    when (m.localName) {
                        "lumMod" -> lumMod = v
                        "lumOff" -> lumOff = v
                        "shade" -> lumMod *= v
                        "tint" -> lumOff += (1f - v) * (1f - lumMod)
                    }
                }
                color = Theme.adjustLuminance(base, lumMod, lumOff)
            }
        }
        return color
    }

    /** Child-to-parent mapping of group shapes. */
    private data class Transform(val scaleX: Float, val scaleY: Float, val dx: Float, val dy: Float) {
        fun apply(r: FrameRect) = FrameRect(r.x * scaleX + dx, r.y * scaleY + dy, r.width * scaleX, r.height * scaleY)

        fun then(inner: Transform) = Transform(
            scaleX = scaleX * inner.scaleX,
            scaleY = scaleY * inner.scaleY,
            dx = inner.dx * scaleX + dx,
            dy = inner.dy * scaleY + dy,
        )

        companion object {
            val IDENTITY = Transform(1f, 1f, 0f, 0f)
        }
    }

    companion object {
        private const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val EMU_PER_PT = 12_700f
        private const val HUNDREDTHS = 100f
        private const val PERCENT_UNITS = 100_000f
        private const val TITLE_SIZE = 40f
        private const val BODY_SIZE = 24f
        private const val TABLE_TEXT = 14f
        private const val PARAGRAPH_GAP = 4f
        private const val MIN_ROW = 20f
        private const val MAX_LEVEL = 8
        private const val WHITE = -0x1
        private const val TABLE_LINE = -0x404041

        /** Marker size for a full-slide background picture until the slide size is known. */
        private const val FULL = -1f

        private val PRESET_COLORS = mapOf(
            "black" to -0x1000000,
            "white" to -0x1,
            "red" to -0x10000,
            "blue" to -0xffff01,
        )

        private fun stretchBackground(shape: SlideShape, width: Float, height: Float): SlideShape =
            if (shape is SlideShape.Picture && shape.frame.width == FULL) {
                SlideShape.Picture(FrameRect(0f, 0f, width, height), shape.bytes, shape.mimeType)
            } else {
                shape
            }

        private fun emu(value: String?): Float? = value?.toFloatOrNull()?.div(EMU_PER_PT)

        private fun ratio(a: Float?, b: Float?): Float = if (a == null || b == null || b == 0f) 1f else a / b

        private fun alignment(value: String?): TextAlign = when (value) {
            "ctr" -> TextAlign.CENTER
            "r" -> TextAlign.END
            "just", "dist" -> TextAlign.JUSTIFY
            else -> TextAlign.START
        }
    }
}
