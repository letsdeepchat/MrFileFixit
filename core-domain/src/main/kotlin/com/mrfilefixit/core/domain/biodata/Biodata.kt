package com.mrfilefixit.core.domain.biodata

import kotlinx.serialization.Serializable

/** Marriage biodata (Indian matrimonial format) or a creative / photographer portfolio. */
@Serializable
enum class BiodataKind { MARRIAGE, PORTFOLIO }

/** Blocks of a biodata, in print order. [kinds] says which biodata types use the section. */
@Serializable
enum class BiodataSection(val kinds: Set<BiodataKind>) {
    PERSONAL(setOf(BiodataKind.MARRIAGE)),

    /** Religion, caste, gotra, rashi and the like. Every field here can be left off the printed biodata. */
    CULTURAL(setOf(BiodataKind.MARRIAGE)),
    EDUCATION_CAREER(setOf(BiodataKind.MARRIAGE)),
    FAMILY(setOf(BiodataKind.MARRIAGE)),
    ABOUT(setOf(BiodataKind.PORTFOLIO)),
    SKILLS(setOf(BiodataKind.PORTFOLIO)),
    SERVICES(setOf(BiodataKind.PORTFOLIO)),
    EXPERIENCE(setOf(BiodataKind.PORTFOLIO)),
    EXPECTATIONS(setOf(BiodataKind.MARRIAGE)),
    CONTACT(setOf(BiodataKind.MARRIAGE, BiodataKind.PORTFOLIO)),
    ;

    fun fields(kind: BiodataKind): List<BiodataField> =
        BiodataField.entries.filter { it.section == this && kind in it.kinds }

    companion object {
        fun forKind(kind: BiodataKind): List<BiodataSection> = entries.filter { kind in it.kinds }
    }
}

/** How a field is entered and printed. */
enum class FieldStyle {
    /** One line of text. */
    LINE,

    /** A paragraph. */
    PARAGRAPH,

    /** One item per line (or comma separated), printed as a bullet list. */
    LIST,
}

/** What keyboard a field wants. */
enum class FieldInput { TEXT, DATE, TIME, NUMBER, PHONE, EMAIL, URL }

/** Every field a biodata can hold. The name (and a portfolio's tagline) live on [Biodata] itself. */
@Serializable
enum class BiodataField(
    val section: BiodataSection,
    val style: FieldStyle = FieldStyle.LINE,
    val input: FieldInput = FieldInput.TEXT,
    val kinds: Set<BiodataKind> = section.kinds,
) {
    DATE_OF_BIRTH(BiodataSection.PERSONAL, input = FieldInput.DATE),
    TIME_OF_BIRTH(BiodataSection.PERSONAL, input = FieldInput.TIME),
    PLACE_OF_BIRTH(BiodataSection.PERSONAL),

    /** Left blank, it is worked out from the date of birth when the biodata is made. */
    AGE(BiodataSection.PERSONAL, input = FieldInput.NUMBER),
    HEIGHT(BiodataSection.PERSONAL),
    WEIGHT(BiodataSection.PERSONAL),
    COMPLEXION(BiodataSection.PERSONAL),
    BLOOD_GROUP(BiodataSection.PERSONAL),
    MARITAL_STATUS(BiodataSection.PERSONAL),

    RELIGION(BiodataSection.CULTURAL),
    CASTE(BiodataSection.CULTURAL),
    SUB_CASTE(BiodataSection.CULTURAL),
    GOTRA(BiodataSection.CULTURAL),
    RASHI(BiodataSection.CULTURAL),
    NAKSHATRA(BiodataSection.CULTURAL),
    MANGLIK(BiodataSection.CULTURAL),
    MOTHER_TONGUE(BiodataSection.CULTURAL),
    DIET(BiodataSection.CULTURAL),

    QUALIFICATION(BiodataSection.EDUCATION_CAREER),
    COLLEGE(BiodataSection.EDUCATION_CAREER),
    DESIGNATION(BiodataSection.EDUCATION_CAREER),
    COMPANY(BiodataSection.EDUCATION_CAREER),
    ANNUAL_INCOME(BiodataSection.EDUCATION_CAREER),

    FATHER_NAME(BiodataSection.FAMILY),
    FATHER_OCCUPATION(BiodataSection.FAMILY),
    MOTHER_NAME(BiodataSection.FAMILY),
    MOTHER_OCCUPATION(BiodataSection.FAMILY),
    BROTHERS(BiodataSection.FAMILY),
    SISTERS(BiodataSection.FAMILY),
    FAMILY_TYPE(BiodataSection.FAMILY),
    FAMILY_VALUES(BiodataSection.FAMILY),
    NATIVE_PLACE(BiodataSection.FAMILY),

    BIO(BiodataSection.ABOUT, FieldStyle.PARAGRAPH),
    SKILLS(BiodataSection.SKILLS, FieldStyle.LIST),
    EQUIPMENT(BiodataSection.SKILLS, FieldStyle.LIST),
    SERVICES(BiodataSection.SERVICES, FieldStyle.LIST),
    YEARS_EXPERIENCE(BiodataSection.EXPERIENCE, input = FieldInput.NUMBER),
    CLIENTS(BiodataSection.EXPERIENCE, FieldStyle.LIST),
    AWARDS(BiodataSection.EXPERIENCE, FieldStyle.LIST),

    PARTNER_EXPECTATIONS(BiodataSection.EXPECTATIONS, FieldStyle.PARAGRAPH),

    ADDRESS(BiodataSection.CONTACT, FieldStyle.PARAGRAPH, kinds = setOf(BiodataKind.MARRIAGE)),
    LOCATION(BiodataSection.CONTACT, kinds = setOf(BiodataKind.PORTFOLIO)),
    PHONE(BiodataSection.CONTACT, input = FieldInput.PHONE),
    EMAIL(BiodataSection.CONTACT, input = FieldInput.EMAIL),
    WEBSITE(BiodataSection.CONTACT, input = FieldInput.URL, kinds = setOf(BiodataKind.PORTFOLIO)),
    SOCIAL(BiodataSection.CONTACT, kinds = setOf(BiodataKind.PORTFOLIO)),
    ;

    /** Cultural and astrological details are personal choices: each can be switched off the printout. */
    val toggleable: Boolean get() = section == BiodataSection.CULTURAL
}

/** A photo stored with a draft. The first photo is the main portrait (or profile photo). */
@Serializable
data class BiodataPhoto(val key: String, val widthPx: Int, val heightPx: Int) {
    init {
        require(widthPx > 0 && heightPx > 0) { "Photo size must be positive" }
    }
}

/**
 * A biodata draft. Stored as JSON in app-private storage; the photos are files next to it, named by
 * [BiodataPhoto.key]. Values are kept as typed; [hidden] fields are kept but left off the printout.
 */
@Serializable
data class Biodata(
    val id: String,
    val templateId: String,
    val name: String = "",
    /** Portfolio: the profession line under the name, e.g. "Wedding and portrait photographer". */
    val tagline: String = "",
    /** Marriage: an optional first line such as a family deity's name or a blessing. */
    val invocation: String = "",
    val values: Map<BiodataField, String> = emptyMap(),
    val hidden: Set<BiodataField> = emptySet(),
    val photos: List<BiodataPhoto> = emptyList(),
    val updatedAtMillis: Long = 0,
) {
    val displayName: String get() = name.ifBlank { DEFAULT_NAME }

    fun value(field: BiodataField): String = values[field].orEmpty()

    /** Whether the field is printed: it has text and the user has not switched it off. */
    fun shows(field: BiodataField): Boolean = field !in hidden && value(field).isNotBlank()

    val canAddPhoto: Boolean get() = photos.size < MAX_PHOTOS

    /** A biodata is made with 1 to [MAX_PHOTOS] photos. */
    val hasValidPhotos: Boolean get() = photos.size in MIN_PHOTOS..MAX_PHOTOS

    override fun toString(): String = "Biodata(template=$templateId, fields=${values.size}, photos=${photos.size})"

    companion object {
        const val DEFAULT_NAME = "Biodata"
        const val MIN_PHOTOS = 1
        const val MAX_PHOTOS = 5
        const val MAX_LINE = 120
        const val MAX_PARAGRAPH = 1500
    }
}

/** A saved draft in the list. */
data class BiodataSummary(
    val id: String,
    val displayName: String,
    val templateId: String,
    val photoCount: Int,
    val updatedAtMillis: Long,
)
