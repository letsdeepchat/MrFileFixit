package com.mrfilefixit.core.domain.biodata

import com.mrfilefixit.core.domain.document.BorderStyle
import com.mrfilefixit.core.domain.document.FontFamily

/** How the main photo and the gallery are placed. */
enum class PhotoLayout {
    /** Main photo beside the personal details; other photos in a two-column grid at the end. */
    BESIDE_DETAILS,

    /** Profile photo beside the name; work samples large, the first across the full width. */
    SHOWCASE,

    /** Profile photo beside the name; work samples in an even two-column grid. */
    GRID,
}

/** How section headings look. */
enum class BiodataHeading {
    /** Centred, between thin rules, in the accent colour. */
    ORNAMENTAL,

    /** On a tinted band. */
    BAND,

    /** Accent text with a rule underneath. */
    RULE,
}

/** One of the bundled biodata designs. */
data class BiodataTemplate(
    val id: String,
    val kind: BiodataKind,
    val accentArgb: Int,
    val secondaryArgb: Int,
    val font: FontFamily,
    val border: BorderStyle?,
    val heading: BiodataHeading,
    val photoLayout: PhotoLayout,
)

/**
 * The five biodata templates: three for marriage (traditional, classic, modern) and two portfolios
 * (photographer, creative). Colours are muted so they print well in colour and in black and white.
 */
object BiodataTemplates {
    val TRADITIONAL = BiodataTemplate(
        id = "marriage-traditional",
        kind = BiodataKind.MARRIAGE,
        accentArgb = 0xFF7A1E1E.toInt(),
        secondaryArgb = 0xFFB8892B.toInt(),
        font = FontFamily.SERIF,
        border = BorderStyle.ORNATE,
        heading = BiodataHeading.ORNAMENTAL,
        photoLayout = PhotoLayout.BESIDE_DETAILS,
    )
    val CLASSIC = BiodataTemplate(
        id = "marriage-classic",
        kind = BiodataKind.MARRIAGE,
        accentArgb = 0xFF1F3A5F.toInt(),
        secondaryArgb = 0xFFB8892B.toInt(),
        font = FontFamily.SERIF,
        border = BorderStyle.DOUBLE,
        heading = BiodataHeading.BAND,
        photoLayout = PhotoLayout.BESIDE_DETAILS,
    )
    val MODERN = BiodataTemplate(
        id = "marriage-modern",
        kind = BiodataKind.MARRIAGE,
        accentArgb = 0xFF0F766E.toInt(),
        secondaryArgb = 0xFF64748B.toInt(),
        font = FontFamily.SANS,
        border = BorderStyle.SINGLE,
        heading = BiodataHeading.RULE,
        photoLayout = PhotoLayout.BESIDE_DETAILS,
    )
    val PHOTOGRAPHER = BiodataTemplate(
        id = "portfolio-photographer",
        kind = BiodataKind.PORTFOLIO,
        accentArgb = 0xFF1F2937.toInt(),
        secondaryArgb = 0xFFD97706.toInt(),
        font = FontFamily.SANS,
        border = null,
        heading = BiodataHeading.RULE,
        photoLayout = PhotoLayout.SHOWCASE,
    )
    val CREATIVE = BiodataTemplate(
        id = "portfolio-creative",
        kind = BiodataKind.PORTFOLIO,
        accentArgb = 0xFF9F3A22.toInt(),
        secondaryArgb = 0xFF3730A3.toInt(),
        font = FontFamily.SANS,
        border = BorderStyle.CORNERS,
        heading = BiodataHeading.BAND,
        photoLayout = PhotoLayout.GRID,
    )

    val all: List<BiodataTemplate> = listOf(TRADITIONAL, CLASSIC, MODERN, PHOTOGRAPHER, CREATIVE)

    fun byId(id: String): BiodataTemplate = all.firstOrNull { it.id == id } ?: all.first()
}
