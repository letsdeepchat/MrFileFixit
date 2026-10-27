package com.mrfilefixit.core.domain.biodata

/** Pure edits of a [Biodata] draft. Text is capped and control characters are dropped. */
object BiodataEdits {
    fun setValue(biodata: Biodata, field: BiodataField, text: String): Biodata {
        val multiline = field.style != FieldStyle.LINE
        val clean = clean(text, multiline, if (multiline) Biodata.MAX_PARAGRAPH else Biodata.MAX_LINE)
        val values = if (clean.isBlank()) biodata.values - field else biodata.values + (field to clean)
        return biodata.copy(values = values)
    }

    fun setName(biodata: Biodata, text: String) = biodata.copy(name = clean(text, false, Biodata.MAX_LINE))

    fun setTagline(biodata: Biodata, text: String) = biodata.copy(tagline = clean(text, false, Biodata.MAX_LINE))

    fun setInvocation(biodata: Biodata, text: String) =
        biodata.copy(invocation = clean(text, false, Biodata.MAX_LINE))

    /** Shows or hides one field on the printout; its text is kept either way. */
    fun setShown(biodata: Biodata, field: BiodataField, shown: Boolean): Biodata =
        biodata.copy(hidden = if (shown) biodata.hidden - field else biodata.hidden + field)

    /** Shows or hides the whole cultural and astrological section. */
    fun setCulturalShown(biodata: Biodata, shown: Boolean): Biodata {
        val cultural = BiodataSection.CULTURAL.fields(BiodataKind.MARRIAGE).toSet()
        return biodata.copy(hidden = if (shown) biodata.hidden - cultural else biodata.hidden + cultural)
    }

    fun culturalShown(biodata: Biodata): Boolean =
        BiodataSection.CULTURAL.fields(BiodataKind.MARRIAGE).any { it !in biodata.hidden }

    /** Adds [photo] at the end, unless the draft already has [Biodata.MAX_PHOTOS]. */
    fun addPhoto(biodata: Biodata, photo: BiodataPhoto): Biodata =
        if (biodata.canAddPhoto && biodata.photos.none { it.key == photo.key }) {
            biodata.copy(photos = biodata.photos + photo)
        } else {
            biodata
        }

    /** Puts [photo] in place of the photo with the same key (after re-cropping). */
    fun replacePhoto(biodata: Biodata, photo: BiodataPhoto): Biodata =
        biodata.copy(photos = biodata.photos.map { if (it.key == photo.key) photo else it })

    fun removePhoto(biodata: Biodata, key: String): Biodata =
        biodata.copy(photos = biodata.photos.filterNot { it.key == key })

    /** Makes the photo with [key] the main portrait (first). */
    fun makePrimary(biodata: Biodata, key: String): Biodata {
        val photo = biodata.photos.firstOrNull { it.key == key } ?: return biodata
        return biodata.copy(photos = listOf(photo) + biodata.photos.filterNot { it.key == key })
    }

    fun movePhoto(biodata: Biodata, from: Int, to: Int): Biodata {
        if (from !in biodata.photos.indices || to !in biodata.photos.indices || from == to) return biodata
        val list = biodata.photos.toMutableList()
        list.add(to, list.removeAt(from))
        return biodata.copy(photos = list)
    }

    /** Switches the design. Only templates of the same kind are offered, so no field is lost. */
    fun applyTemplate(biodata: Biodata, template: BiodataTemplate): Biodata {
        val current = BiodataTemplates.byId(biodata.templateId)
        return if (current.kind == template.kind) biodata.copy(templateId = template.id) else biodata
    }

    private fun clean(text: String, multiline: Boolean, max: Int): String = text
        .filter { c -> !c.isISOControl() || (multiline && c == '\n') }
        .take(max)
}
