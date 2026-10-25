package com.mrfilefixit.core.domain.resume

import com.mrfilefixit.core.domain.document.FontFamily
import com.mrfilefixit.core.domain.document.PageSetup
import kotlinx.serialization.Serializable

/**
 * A resume draft as the user edits it. It is stored as JSON in app-private storage (see [ResumeJson]) and
 * embedded in exported DOCX files, so a draft can be re-opened from either.
 *
 * The profile photo is not part of the JSON; [hasPhoto] says whether the repository holds one for [id].
 */
@Serializable
data class Resume(
    val id: String,
    val templateId: String,
    val contact: ContactInfo = ContactInfo(),
    val sections: List<ResumeSection> = emptyList(),
    val style: ResumeStyle = ResumeStyle(),
    val hasPhoto: Boolean = false,
    val updatedAtMillis: Long = 0,
) {
    /** Name shown in the saved-resumes list and used for exported file names. */
    val displayName: String get() = contact.fullName.ifBlank { DEFAULT_NAME }

    override fun toString(): String = "Resume(template=$templateId, sections=${sections.size})"

    companion object {
        const val DEFAULT_NAME = "Resume"
    }
}

@Serializable
data class ContactInfo(
    val fullName: String = "",
    /** One line under the name, e.g. "Senior Android Engineer". */
    val headline: String = "",
    val email: String = "",
    val phone: String = "",
    val location: String = "",
    /** Portfolio, LinkedIn, GitHub or any other links, one per entry. */
    val links: List<String> = emptyList(),
)

/** The blocks a resume can contain. CUSTOM sections can be added any number of times. */
@Serializable
enum class SectionKind {
    SUMMARY,
    EXPERIENCE,
    EDUCATION,
    SKILLS,
    PROJECTS,
    CERTIFICATIONS,
    CUSTOM,
    ;

    /** Summary is free text; every other kind is a list of entries. */
    val isText: Boolean get() = this == SUMMARY
}

@Serializable
data class ResumeSection(
    val id: String,
    val kind: SectionKind,
    val title: String,
    val visible: Boolean = true,
    /** Body of a SUMMARY section, or an optional intro paragraph for the others. */
    val text: String = "",
    val entries: List<ResumeEntry> = emptyList(),
)

/**
 * One job, degree, project, certificate or skill group. Which fields matter depends on the section:
 * a skill group uses [title] as the group name and [bullets] as its skills.
 */
@Serializable
data class ResumeEntry(
    val id: String,
    /** Job title, degree, project name, certificate or skill group. */
    val title: String = "",
    /** Employer, institution, issuer or tech stack. */
    val subtitle: String = "",
    val location: String = "",
    /** Free text such as "Jun 2021 – Present", so any date style works. */
    val dates: String = "",
    val bullets: List<String> = emptyList(),
)

@Serializable
enum class ResumeFont(val family: FontFamily) {
    SANS(FontFamily.SANS),
    SERIF(FontFamily.SERIF),
    CONDENSED(FontFamily.CONDENSED),
    MONO(FontFamily.MONO),
}

@Serializable
enum class ResumePaper(val widthPt: Float, val heightPt: Float) {
    A4(PageSetup.A4_WIDTH, PageSetup.A4_HEIGHT),
    LETTER(PageSetup.LETTER_WIDTH, PageSetup.LETTER_HEIGHT),
}

@Serializable
data class ResumeStyle(
    val font: ResumeFont = ResumeFont.SANS,
    /** Body text size in points; headings scale from it. */
    val bodySizePt: Float = DEFAULT_BODY_SIZE,
    /** Packed ARGB colour of the name, section headings and rules. */
    val accentArgb: Int = DEFAULT_ACCENT,
    val paper: ResumePaper = ResumePaper.A4,
) {
    companion object {
        const val MIN_BODY_SIZE = 9f
        const val MAX_BODY_SIZE = 13f
        const val DEFAULT_BODY_SIZE = 10.5f
        const val DEFAULT_ACCENT = -0xdcaa6b // 0xFF235595
    }
}

/** A saved draft in the list, without loading its sections. */
data class ResumeSummary(
    val id: String,
    val name: String,
    val templateId: String,
    val updatedAtMillis: Long,
) {
    override fun toString(): String = "ResumeSummary(template=$templateId)"
}
