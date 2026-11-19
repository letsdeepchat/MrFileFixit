package com.mrfilefixit.feature.resume.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import com.mrfilefixit.core.domain.biodata.BiodataField
import com.mrfilefixit.core.domain.biodata.BiodataKind
import com.mrfilefixit.core.domain.biodata.BiodataLabels
import com.mrfilefixit.core.domain.biodata.BiodataSection
import com.mrfilefixit.core.domain.biodata.BiodataTemplate
import com.mrfilefixit.core.domain.biodata.BiodataTemplates
import com.mrfilefixit.feature.resume.R

/** String resources for biodata fields, sections and designs. Used on screen and in the printed biodata. */
internal object BiodataText {
    @StringRes
    fun field(field: BiodataField): Int = when (field) {
        BiodataField.DATE_OF_BIRTH -> R.string.biodata_f_date_of_birth
        BiodataField.TIME_OF_BIRTH -> R.string.biodata_f_time_of_birth
        BiodataField.PLACE_OF_BIRTH -> R.string.biodata_f_place_of_birth
        BiodataField.AGE -> R.string.biodata_f_age
        BiodataField.HEIGHT -> R.string.biodata_f_height
        BiodataField.WEIGHT -> R.string.biodata_f_weight
        BiodataField.COMPLEXION -> R.string.biodata_f_complexion
        BiodataField.BLOOD_GROUP -> R.string.biodata_f_blood_group
        BiodataField.MARITAL_STATUS -> R.string.biodata_f_marital_status
        BiodataField.RELIGION -> R.string.biodata_f_religion
        BiodataField.CASTE -> R.string.biodata_f_caste
        BiodataField.SUB_CASTE -> R.string.biodata_f_sub_caste
        BiodataField.GOTRA -> R.string.biodata_f_gotra
        BiodataField.RASHI -> R.string.biodata_f_rashi
        BiodataField.NAKSHATRA -> R.string.biodata_f_nakshatra
        BiodataField.MANGLIK -> R.string.biodata_f_manglik
        BiodataField.MOTHER_TONGUE -> R.string.biodata_f_mother_tongue
        BiodataField.DIET -> R.string.biodata_f_diet
        BiodataField.QUALIFICATION -> R.string.biodata_f_qualification
        BiodataField.COLLEGE -> R.string.biodata_f_college
        BiodataField.DESIGNATION -> R.string.biodata_f_designation
        BiodataField.COMPANY -> R.string.biodata_f_company
        BiodataField.ANNUAL_INCOME -> R.string.biodata_f_annual_income
        BiodataField.FATHER_NAME -> R.string.biodata_f_father_name
        BiodataField.FATHER_OCCUPATION -> R.string.biodata_f_father_occupation
        BiodataField.MOTHER_NAME -> R.string.biodata_f_mother_name
        BiodataField.MOTHER_OCCUPATION -> R.string.biodata_f_mother_occupation
        BiodataField.BROTHERS -> R.string.biodata_f_brothers
        BiodataField.SISTERS -> R.string.biodata_f_sisters
        BiodataField.FAMILY_TYPE -> R.string.biodata_f_family_type
        BiodataField.FAMILY_VALUES -> R.string.biodata_f_family_values
        BiodataField.NATIVE_PLACE -> R.string.biodata_f_native_place
        BiodataField.BIO -> R.string.biodata_f_bio
        BiodataField.SKILLS -> R.string.biodata_f_skills
        BiodataField.EQUIPMENT -> R.string.biodata_f_equipment
        BiodataField.SERVICES -> R.string.biodata_f_services
        BiodataField.YEARS_EXPERIENCE -> R.string.biodata_f_years_experience
        BiodataField.CLIENTS -> R.string.biodata_f_clients
        BiodataField.AWARDS -> R.string.biodata_f_awards
        BiodataField.PARTNER_EXPECTATIONS -> R.string.biodata_f_partner_expectations
        BiodataField.ADDRESS -> R.string.biodata_f_address
        BiodataField.LOCATION -> R.string.biodata_f_location
        BiodataField.PHONE -> R.string.biodata_f_phone
        BiodataField.EMAIL -> R.string.biodata_f_email
        BiodataField.WEBSITE -> R.string.biodata_f_website
        BiodataField.SOCIAL -> R.string.biodata_f_social
    }

    @StringRes
    fun section(section: BiodataSection): Int = when (section) {
        BiodataSection.PERSONAL -> R.string.biodata_section_personal
        BiodataSection.CULTURAL -> R.string.biodata_section_cultural
        BiodataSection.EDUCATION_CAREER -> R.string.biodata_section_education_career
        BiodataSection.FAMILY -> R.string.biodata_section_family
        BiodataSection.ABOUT -> R.string.biodata_section_about
        BiodataSection.SKILLS -> R.string.biodata_section_skills
        BiodataSection.SERVICES -> R.string.biodata_section_services
        BiodataSection.EXPERIENCE -> R.string.biodata_section_experience
        BiodataSection.EXPECTATIONS -> R.string.biodata_section_expectations
        BiodataSection.CONTACT -> R.string.biodata_section_contact
    }

    @StringRes
    fun kind(kind: BiodataKind): Int = when (kind) {
        BiodataKind.MARRIAGE -> R.string.biodata_kind_marriage
        BiodataKind.PORTFOLIO -> R.string.biodata_kind_portfolio
    }

    @StringRes
    fun templateName(template: BiodataTemplate): Int = when (template.id) {
        BiodataTemplates.CLASSIC.id -> R.string.biodata_template_classic
        BiodataTemplates.MODERN.id -> R.string.biodata_template_modern
        BiodataTemplates.PHOTOGRAPHER.id -> R.string.biodata_template_photographer
        BiodataTemplates.CREATIVE.id -> R.string.biodata_template_creative
        else -> R.string.biodata_template_traditional
    }

    @StringRes
    fun templateDescription(template: BiodataTemplate): Int = when (template.id) {
        BiodataTemplates.CLASSIC.id -> R.string.biodata_template_classic_desc
        BiodataTemplates.MODERN.id -> R.string.biodata_template_modern_desc
        BiodataTemplates.PHOTOGRAPHER.id -> R.string.biodata_template_photographer_desc
        BiodataTemplates.CREATIVE.id -> R.string.biodata_template_creative_desc
        else -> R.string.biodata_template_traditional_desc
    }
}

/** The printed words in the app's language (English or Hindi), from the same strings the editor shows. */
internal class ResourceBiodataLabels(private val resources: Resources) : BiodataLabels {
    override val title: String = resources.getString(R.string.biodata_print_title)
    override val photos: String = resources.getString(R.string.biodata_print_photos)
    override val workSamples: String = resources.getString(R.string.biodata_print_work)

    override fun field(field: BiodataField): String = resources.getString(BiodataText.field(field))

    override fun section(section: BiodataSection): String = resources.getString(BiodataText.section(section))

    override fun age(years: Int): String = resources.getQuantityString(R.plurals.biodata_print_age, years, years)
}
