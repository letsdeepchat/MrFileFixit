package com.mrfilefixit.core.domain.biodata

/**
 * Printed words of a biodata. The app passes its translated strings (English or Hindi), so the PDF is in the
 * user's language; [English] is the fallback and what tests use.
 */
interface BiodataLabels {
    val title: String
    val photos: String
    val workSamples: String

    fun field(field: BiodataField): String

    fun section(section: BiodataSection): String

    /** "27 years". */
    fun age(years: Int): String

    object English : BiodataLabels {
        override val title = "Biodata"
        override val photos = "Photos"
        override val workSamples = "Work samples"

        override fun field(field: BiodataField): String = when (field) {
            BiodataField.DATE_OF_BIRTH -> "Date of birth"
            BiodataField.TIME_OF_BIRTH -> "Time of birth"
            BiodataField.PLACE_OF_BIRTH -> "Place of birth"
            BiodataField.AGE -> "Age"
            BiodataField.HEIGHT -> "Height"
            BiodataField.WEIGHT -> "Weight"
            BiodataField.COMPLEXION -> "Complexion"
            BiodataField.BLOOD_GROUP -> "Blood group"
            BiodataField.MARITAL_STATUS -> "Marital status"
            BiodataField.RELIGION -> "Religion"
            BiodataField.CASTE -> "Caste"
            BiodataField.SUB_CASTE -> "Sub-caste"
            BiodataField.GOTRA -> "Gotra"
            BiodataField.RASHI -> "Rashi (moon sign)"
            BiodataField.NAKSHATRA -> "Nakshatra"
            BiodataField.MANGLIK -> "Manglik"
            BiodataField.MOTHER_TONGUE -> "Mother tongue"
            BiodataField.DIET -> "Diet"
            BiodataField.QUALIFICATION -> "Highest qualification"
            BiodataField.COLLEGE -> "College / university"
            BiodataField.DESIGNATION -> "Designation"
            BiodataField.COMPANY -> "Company"
            BiodataField.ANNUAL_INCOME -> "Annual income"
            BiodataField.FATHER_NAME -> "Father's name"
            BiodataField.FATHER_OCCUPATION -> "Father's occupation"
            BiodataField.MOTHER_NAME -> "Mother's name"
            BiodataField.MOTHER_OCCUPATION -> "Mother's occupation"
            BiodataField.BROTHERS -> "Brothers"
            BiodataField.SISTERS -> "Sisters"
            BiodataField.FAMILY_TYPE -> "Family type"
            BiodataField.FAMILY_VALUES -> "Family values"
            BiodataField.NATIVE_PLACE -> "Native place"
            BiodataField.BIO -> "About me"
            BiodataField.SKILLS -> "Skills"
            BiodataField.EQUIPMENT -> "Equipment"
            BiodataField.SERVICES -> "Services"
            BiodataField.YEARS_EXPERIENCE -> "Years of experience"
            BiodataField.CLIENTS -> "Clients and projects"
            BiodataField.AWARDS -> "Awards and features"
            BiodataField.PARTNER_EXPECTATIONS -> "Partner expectations"
            BiodataField.ADDRESS -> "Address"
            BiodataField.LOCATION -> "Location"
            BiodataField.PHONE -> "Phone"
            BiodataField.EMAIL -> "Email"
            BiodataField.WEBSITE -> "Website"
            BiodataField.SOCIAL -> "Instagram / social"
        }

        override fun section(section: BiodataSection): String = when (section) {
            BiodataSection.PERSONAL -> "Personal details"
            BiodataSection.CULTURAL -> "Cultural and astrological details"
            BiodataSection.EDUCATION_CAREER -> "Education and career"
            BiodataSection.FAMILY -> "Family background"
            BiodataSection.ABOUT -> "About"
            BiodataSection.SKILLS -> "Skills and equipment"
            BiodataSection.SERVICES -> "Services"
            BiodataSection.EXPERIENCE -> "Experience"
            BiodataSection.EXPECTATIONS -> "Partner expectations"
            BiodataSection.CONTACT -> "Contact details"
        }

        override fun age(years: Int): String = "$years years"
    }
}
