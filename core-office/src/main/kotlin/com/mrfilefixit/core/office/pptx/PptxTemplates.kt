package com.mrfilefixit.core.office.pptx

import com.mrfilefixit.core.office.xml.XmlWriter

/** The fixed parts of a minimal valid presentation: one master, one blank layout and the Office theme. */
internal object PptxTemplates {
    private const val LAYOUT_ID = 2_147_483_649L
    private val ACCENTS = listOf("4472C4", "ED7D31", "A5A5A5", "FFC000", "5B9BD5", "70AD47")
    private val LINE_WIDTHS = listOf(6_350, 12_700, 19_050)
    private const val STYLE_COUNT = 3
    private const val FIRST_BACKGROUND_STYLE = 1001

    fun groupHeader(xml: XmlWriter) {
        xml.within("p:nvGrpSpPr") {
            empty("p:cNvPr", "id" to 1, "name" to "")
            empty("p:cNvGrpSpPr")
            empty("p:nvPr")
        }
        xml.within("p:grpSpPr") {
            within("a:xfrm") {
                empty("a:off", "x" to 0, "y" to 0)
                empty("a:ext", "cx" to 0, "cy" to 0)
                empty("a:chOff", "x" to 0, "y" to 0)
                empty("a:chExt", "cx" to 0, "cy" to 0)
            }
        }
    }

    fun master(): XmlWriter = XmlWriter().within(
        "p:sldMaster",
        "xmlns:a" to PptxWriter.A_NS,
        "xmlns:r" to PptxWriter.R_NS,
        "xmlns:p" to PptxWriter.P_NS,
    ) {
        within("p:cSld") {
            within(
                "p:bg",
            ) { within("p:bgRef", "idx" to FIRST_BACKGROUND_STYLE) { empty("a:schemeClr", "val" to "bg1") } }
            within("p:spTree") { groupHeader(this) }
        }
        empty(
            "p:clrMap",
            "bg1" to "lt1",
            "tx1" to "dk1",
            "bg2" to "lt2",
            "tx2" to "dk2",
            "accent1" to "accent1",
            "accent2" to "accent2",
            "accent3" to "accent3",
            "accent4" to "accent4",
            "accent5" to "accent5",
            "accent6" to "accent6",
            "hlink" to "hlink",
            "folHlink" to "folHlink",
        )
        within("p:sldLayoutIdLst") { empty("p:sldLayoutId", "id" to LAYOUT_ID, "r:id" to "rId1") }
    }

    fun layout(): XmlWriter = XmlWriter().within(
        "p:sldLayout",
        "xmlns:a" to PptxWriter.A_NS,
        "xmlns:r" to PptxWriter.R_NS,
        "xmlns:p" to PptxWriter.P_NS,
        "type" to "blank",
        "preserve" to 1,
    ) {
        within("p:cSld", "name" to "Blank") { within("p:spTree") { groupHeader(this) } }
        within("p:clrMapOvr") { empty("a:masterClrMapping") }
    }

    fun presProps(): XmlWriter = XmlWriter().within(
        "p:presentationPr",
        "xmlns:a" to PptxWriter.A_NS,
        "xmlns:r" to PptxWriter.R_NS,
        "xmlns:p" to PptxWriter.P_NS,
    ) {}

    fun theme(): XmlWriter = XmlWriter().within("a:theme", "xmlns:a" to PptxWriter.A_NS, "name" to "Office Theme") {
        within("a:themeElements") {
            within("a:clrScheme", "name" to "Office") {
                within("a:dk1") { empty("a:sysClr", "val" to "windowText", "lastClr" to "000000") }
                within("a:lt1") { empty("a:sysClr", "val" to "window", "lastClr" to "FFFFFF") }
                within("a:dk2") { empty("a:srgbClr", "val" to "44546A") }
                within("a:lt2") { empty("a:srgbClr", "val" to "E7E6E6") }
                ACCENTS.forEachIndexed { i, hex -> within("a:accent${i + 1}") { empty("a:srgbClr", "val" to hex) } }
                within("a:hlink") { empty("a:srgbClr", "val" to "0563C1") }
                within("a:folHlink") { empty("a:srgbClr", "val" to "954F72") }
            }
            within("a:fontScheme", "name" to "Office") {
                listOf("a:majorFont" to "Calibri Light", "a:minorFont" to "Calibri").forEach { (slot, face) ->
                    within(slot) {
                        empty("a:latin", "typeface" to face)
                        empty("a:ea", "typeface" to "")
                        empty("a:cs", "typeface" to "")
                    }
                }
            }
            within("a:fmtScheme", "name" to "Office") {
                within("a:fillStyleLst") { repeat(STYLE_COUNT) { phFill() } }
                within("a:lnStyleLst") {
                    LINE_WIDTHS.forEach { w -> within("a:ln", "w" to w) { phFill() } }
                }
                within("a:effectStyleLst") { repeat(STYLE_COUNT) { within("a:effectStyle") { empty("a:effectLst") } } }
                within("a:bgFillStyleLst") { repeat(STYLE_COUNT) { phFill() } }
            }
        }
    }

    private fun XmlWriter.phFill() {
        within("a:solidFill") { empty("a:schemeClr", "val" to "phClr") }
    }
}
