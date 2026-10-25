package com.mrfilefixit.core.office.pptx

import com.mrfilefixit.core.office.docx.DocxReader
import com.mrfilefixit.core.office.xml.XmlReader
import kotlin.math.abs
import kotlin.math.roundToInt

/** Theme colour scheme (dk1, lt1, accent1...) used to resolve `schemeClr` references. */
internal class Theme(private val colors: Map<String, Int>) {
    fun color(name: String?): Int? = colors[ALIASES[name] ?: name]

    companion object {
        private val ALIASES = mapOf("tx1" to "dk1", "bg1" to "lt1", "tx2" to "dk2", "bg2" to "lt2")
        private const val RGB_MASK = 0xFFFFFF
        private const val OPAQUE = -0x1000000
        private const val CHANNEL = 255f
        private const val HUE_SECTORS = 6f
        private const val HUE_THIRD = 1f / 3f
        private const val HALF = 0.5f
        private const val BYTE = 0xFF
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8

        val DEFAULT = Theme(
            mapOf(
                "dk1" to OPAQUE,
                "lt1" to -0x1,
                "dk2" to (OPAQUE or 0x44546A),
                "lt2" to (OPAQUE or 0xE7E6E6),
                "accent1" to (OPAQUE or 0x4472C4),
                "accent2" to (OPAQUE or 0xED7D31),
                "accent3" to (OPAQUE or 0xA5A5A5),
                "accent4" to (OPAQUE or 0xFFC000),
                "accent5" to (OPAQUE or 0x5B9BD5),
                "accent6" to (OPAQUE or 0x70AD47),
                "hlink" to (OPAQUE or 0x0563C1),
                "folHlink" to (OPAQUE or 0x954F72),
            ),
        )

        fun read(xml: XmlReader): Theme {
            val colors = mutableMapOf<String, Int>()
            while (xml.next() != XmlReader.Event.EOF) {
                if (xml.event == XmlReader.Event.START && xml.localName == "clrScheme") {
                    xml.forEachChild { slot ->
                        val name = slot.localName
                        slot.forEachChild { c ->
                            val value = when (c.localName) {
                                "srgbClr" -> DocxReader.parseColor(c.attr("val"))
                                "sysClr" -> DocxReader.parseColor(c.attr("lastClr"))
                                else -> null
                            }
                            if (value != null) colors[name] = value
                        }
                    }
                    break
                }
            }
            return if (colors.isEmpty()) DEFAULT else Theme(DEFAULT.colors + colors)
        }

        /** DrawingML lumMod/lumOff: scale and offset the HSL lightness. */
        fun adjustLuminance(argb: Int, lumMod: Float, lumOff: Float): Int {
            if (lumMod == 1f && lumOff == 0f) return argb
            val r = ((argb shr RED_SHIFT) and BYTE) / CHANNEL
            val g = ((argb shr GREEN_SHIFT) and BYTE) / CHANNEL
            val b = (argb and BYTE) / CHANNEL
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val l = (max + min) / 2
            val d = max - min
            val s = if (d == 0f) 0f else d / (1 - abs(2 * l - 1))
            val h = when {
                d == 0f -> 0f
                max == r -> ((g - b) / d).mod(HUE_SECTORS) / HUE_SECTORS
                max == g -> ((b - r) / d + 2) / HUE_SECTORS
                else -> ((r - g) / d + 4) / HUE_SECTORS
            }
            val newL = (l * lumMod + lumOff).coerceIn(0f, 1f)
            return hsl(h, s, newL) and RGB_MASK or OPAQUE
        }

        private fun hsl(h: Float, s: Float, l: Float): Int {
            if (s == 0f) {
                val v = (l * CHANNEL).roundToInt()
                return (v shl RED_SHIFT) or (v shl GREEN_SHIFT) or v
            }
            val q = if (l < HALF) l * (1 + s) else l + s - l * s
            val p = 2 * l - q
            fun channel(t0: Float): Int {
                val t = t0.mod(1f)
                val v = when {
                    t < 1f / HUE_SECTORS -> p + (q - p) * HUE_SECTORS * t
                    t < HALF -> q
                    t < 2 * HUE_THIRD -> p + (q - p) * (2 * HUE_THIRD - t) * HUE_SECTORS
                    else -> p
                }
                return (v * CHANNEL).roundToInt().coerceIn(0, BYTE)
            }
            return (channel(h + HUE_THIRD) shl RED_SHIFT) or (channel(h) shl GREEN_SHIFT) or channel(h - HUE_THIRD)
        }
    }
}
