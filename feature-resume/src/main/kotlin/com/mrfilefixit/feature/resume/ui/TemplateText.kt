package com.mrfilefixit.feature.resume.ui

import androidx.annotation.StringRes
import com.mrfilefixit.core.domain.resume.ResumeField
import com.mrfilefixit.core.domain.resume.ResumeFont
import com.mrfilefixit.core.domain.resume.ResumePaper
import com.mrfilefixit.core.domain.resume.SectionKind
import com.mrfilefixit.feature.resume.R

/** String resources for domain enums, as exhaustive `when`s so a new value cannot ship without text. */
internal object TemplateText {
    @StringRes
    fun name(field: ResumeField): Int = when (field) {
        ResumeField.SOFTWARE -> R.string.resume_template_SOFTWARE
        ResumeField.CORE_ENGINEERING -> R.string.resume_template_CORE_ENGINEERING
        ResumeField.ARTS -> R.string.resume_template_ARTS
        ResumeField.MARKETING -> R.string.resume_template_MARKETING
        ResumeField.FINANCE -> R.string.resume_template_FINANCE
        ResumeField.DATA_SCIENCE -> R.string.resume_template_DATA_SCIENCE
        ResumeField.HEALTHCARE -> R.string.resume_template_HEALTHCARE
        ResumeField.EDUCATION -> R.string.resume_template_EDUCATION
        ResumeField.GRAPHIC_DESIGN -> R.string.resume_template_GRAPHIC_DESIGN
        ResumeField.FRESH_GRADUATE -> R.string.resume_template_FRESH_GRADUATE
        ResumeField.BUSINESS_ADMIN -> R.string.resume_template_BUSINESS_ADMIN
        ResumeField.LEGAL -> R.string.resume_template_LEGAL
        ResumeField.HUMAN_RESOURCES -> R.string.resume_template_HUMAN_RESOURCES
        ResumeField.OPERATIONS -> R.string.resume_template_OPERATIONS
        ResumeField.ACADEMIC_CV -> R.string.resume_template_ACADEMIC_CV
    }

    @StringRes
    fun description(field: ResumeField): Int = when (field) {
        ResumeField.SOFTWARE -> R.string.resume_template_desc_SOFTWARE
        ResumeField.CORE_ENGINEERING -> R.string.resume_template_desc_CORE_ENGINEERING
        ResumeField.ARTS -> R.string.resume_template_desc_ARTS
        ResumeField.MARKETING -> R.string.resume_template_desc_MARKETING
        ResumeField.FINANCE -> R.string.resume_template_desc_FINANCE
        ResumeField.DATA_SCIENCE -> R.string.resume_template_desc_DATA_SCIENCE
        ResumeField.HEALTHCARE -> R.string.resume_template_desc_HEALTHCARE
        ResumeField.EDUCATION -> R.string.resume_template_desc_EDUCATION
        ResumeField.GRAPHIC_DESIGN -> R.string.resume_template_desc_GRAPHIC_DESIGN
        ResumeField.FRESH_GRADUATE -> R.string.resume_template_desc_FRESH_GRADUATE
        ResumeField.BUSINESS_ADMIN -> R.string.resume_template_desc_BUSINESS_ADMIN
        ResumeField.LEGAL -> R.string.resume_template_desc_LEGAL
        ResumeField.HUMAN_RESOURCES -> R.string.resume_template_desc_HUMAN_RESOURCES
        ResumeField.OPERATIONS -> R.string.resume_template_desc_OPERATIONS
        ResumeField.ACADEMIC_CV -> R.string.resume_template_desc_ACADEMIC_CV
    }

    @StringRes
    fun kind(kind: SectionKind): Int = when (kind) {
        SectionKind.SUMMARY -> R.string.resume_kind_SUMMARY
        SectionKind.EXPERIENCE -> R.string.resume_kind_EXPERIENCE
        SectionKind.EDUCATION -> R.string.resume_kind_EDUCATION
        SectionKind.SKILLS -> R.string.resume_kind_SKILLS
        SectionKind.PROJECTS -> R.string.resume_kind_PROJECTS
        SectionKind.CERTIFICATIONS -> R.string.resume_kind_CERTIFICATIONS
        SectionKind.CUSTOM -> R.string.resume_kind_CUSTOM
    }

    /** Title a new section of [kind] starts with. */
    @StringRes
    fun defaultTitle(kind: SectionKind): Int = when (kind) {
        SectionKind.SUMMARY -> R.string.resume_default_SUMMARY
        SectionKind.EXPERIENCE -> R.string.resume_default_EXPERIENCE
        SectionKind.EDUCATION -> R.string.resume_default_EDUCATION
        SectionKind.SKILLS -> R.string.resume_default_SKILLS
        SectionKind.PROJECTS -> R.string.resume_default_PROJECTS
        SectionKind.CERTIFICATIONS -> R.string.resume_default_CERTIFICATIONS
        SectionKind.CUSTOM -> R.string.resume_default_CUSTOM
    }

    /** Label of an entry's first field; null for kinds without entries. */
    @StringRes
    fun entryTitle(kind: SectionKind): Int? = when (kind) {
        SectionKind.SUMMARY, SectionKind.SKILLS -> null
        SectionKind.EXPERIENCE -> R.string.resume_title_EXPERIENCE
        SectionKind.EDUCATION -> R.string.resume_title_EDUCATION
        SectionKind.PROJECTS -> R.string.resume_title_PROJECTS
        SectionKind.CERTIFICATIONS -> R.string.resume_title_CERTIFICATIONS
        SectionKind.CUSTOM -> R.string.resume_title_CUSTOM
    }

    @StringRes
    fun entrySubtitle(kind: SectionKind): Int? = when (kind) {
        SectionKind.SUMMARY, SectionKind.SKILLS -> null
        SectionKind.EXPERIENCE -> R.string.resume_subtitle_EXPERIENCE
        SectionKind.EDUCATION -> R.string.resume_subtitle_EDUCATION
        SectionKind.PROJECTS -> R.string.resume_subtitle_PROJECTS
        SectionKind.CERTIFICATIONS -> R.string.resume_subtitle_CERTIFICATIONS
        SectionKind.CUSTOM -> R.string.resume_subtitle_CUSTOM
    }

    @StringRes
    fun font(font: ResumeFont): Int = when (font) {
        ResumeFont.SANS -> R.string.resume_font_SANS
        ResumeFont.SERIF -> R.string.resume_font_SERIF
        ResumeFont.CONDENSED -> R.string.resume_font_CONDENSED
        ResumeFont.MONO -> R.string.resume_font_MONO
    }

    @StringRes
    fun paper(paper: ResumePaper): Int = when (paper) {
        ResumePaper.A4 -> R.string.resume_paper_A4
        ResumePaper.LETTER -> R.string.resume_paper_LETTER
    }
}
