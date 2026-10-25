package com.mrfilefixit.core.domain.resume

/** The fields a template is written for. Each has exactly one template, with the same name as its id. */
enum class ResumeField {
    SOFTWARE,
    CORE_ENGINEERING,
    ARTS,
    MARKETING,
    FINANCE,
    DATA_SCIENCE,
    HEALTHCARE,
    EDUCATION,
    GRAPHIC_DESIGN,
    FRESH_GRADUATE,
    BUSINESS_ADMIN,
    LEGAL,
    HUMAN_RESOURCES,
    OPERATIONS,
    ACADEMIC_CV,
}

/** How the name and contact lines are laid out. All three are plain text, so ATS parsers read them. */
enum class HeaderLayout {
    /** Name and contact centred. */
    CENTERED,

    /** Name and contact left-aligned. */
    LEFT,

    /** Name on a tinted band across the page. */
    BAND,
}

/** How section headings look. */
enum class HeadingStyle {
    /** Accent-coloured heading with a thin rule under it. */
    RULE,

    /** Small capitals in the accent colour, no rule. */
    CAPS,

    /** Heading on a light tint of the accent colour. */
    BAND,
}

/** A section as a template proposes it: its kind, default title, and sample content the user overwrites. */
data class SampleSection(
    val kind: SectionKind,
    val title: String,
    val text: String = "",
    val entries: List<ResumeEntry> = emptyList(),
)

/**
 * One ATS-friendly template: a single column of real text (no text in pictures, no multi-column layout),
 * standard section names, and a field-specific section order with sample content to edit.
 */
data class ResumeTemplate(
    val field: ResumeField,
    val font: ResumeFont,
    val accentArgb: Int,
    val header: HeaderLayout,
    val heading: HeadingStyle,
    /** Whether the template shows the photo frame by default (common in arts, design, healthcare, academia). */
    val photo: Boolean,
    val sampleContact: ContactInfo,
    val sections: List<SampleSection>,
) {
    val id: String get() = this.field.name
}

/** The 15 bundled templates. Text is English sample content; every word is editable. */
object ResumeTemplates {

    val all: List<ResumeTemplate> by lazy { ResumeField.entries.map(::buildTemplate) }

    fun byId(id: String): ResumeTemplate = all.firstOrNull { it.id == id } ?: all.first()
}
