package com.mrfilefixit.core.domain.resume

/**
 * Pure edits on a [Resume], so the editor's view model stays thin and the rules are unit-tested.
 * Ids that are not found leave the resume unchanged.
 */
object ResumeEdits {

    fun moveSection(resume: Resume, sectionId: String, delta: Int): Resume =
        resume.copy(sections = resume.sections.moved({ it.id == sectionId }, delta))

    fun updateSection(resume: Resume, sectionId: String, transform: (ResumeSection) -> ResumeSection): Resume =
        resume.copy(sections = resume.sections.map { if (it.id == sectionId) transform(it) else it })

    fun removeSection(resume: Resume, sectionId: String): Resume =
        resume.copy(sections = resume.sections.filterNot { it.id == sectionId })

    /** Adds a section at the end, with one empty entry for list sections so the user can start typing. */
    fun addSection(resume: Resume, kind: SectionKind, title: String, newId: () -> String): Resume {
        val entries = if (kind.isText) emptyList() else listOf(ResumeEntry(id = newId()))
        return resume.copy(sections = resume.sections + ResumeSection(newId(), kind, title, entries = entries))
    }

    fun addEntry(resume: Resume, sectionId: String, newId: String): Resume =
        updateSection(resume, sectionId) { it.copy(entries = it.entries + ResumeEntry(id = newId)) }

    fun updateEntry(
        resume: Resume,
        sectionId: String,
        entryId: String,
        transform: (ResumeEntry) -> ResumeEntry,
    ): Resume = updateSection(resume, sectionId) { section ->
        section.copy(entries = section.entries.map { if (it.id == entryId) transform(it) else it })
    }

    fun removeEntry(resume: Resume, sectionId: String, entryId: String): Resume =
        updateSection(
            resume,
            sectionId,
        ) { section -> section.copy(entries = section.entries.filterNot { it.id == entryId }) }

    fun moveEntry(resume: Resume, sectionId: String, entryId: String, delta: Int): Resume =
        updateSection(resume, sectionId) { section ->
            section.copy(entries = section.entries.moved({ it.id == entryId }, delta))
        }

    /**
     * Switches to another template's look (font and accent colour) and keeps all content, the user's
     * section order, text size and paper.
     */
    fun applyTemplate(resume: Resume, template: ResumeTemplate): Resume = resume.copy(
        templateId = template.id,
        style = resume.style.copy(font = template.font, accentArgb = template.accentArgb),
    )

    /**
     * One bullet per non-blank line, without typed bullet marks. The editor stores what the user typed
     * (one list item per line, blank lines included) so the text field never rewrites itself while typing;
     * the cleaning happens here, when the resume is rendered.
     */
    fun bulletsFromText(text: String): List<String> =
        text.lines().map { it.trim().trimStart('•', '-', '*', '–').trim() }.filter { it.isNotEmpty() }

    /** Skills typed as "Kotlin, Java; SQL" or one per line (stored as typed, like bullets). */
    fun skillsFromText(text: String): List<String> =
        text.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    private fun <T> List<T>.moved(match: (T) -> Boolean, delta: Int): List<T> {
        val from = indexOfFirst(match)
        if (from < 0) return this
        val to = (from + delta).coerceIn(0, lastIndex)
        if (to == from) return this
        return toMutableList().apply { add(to, removeAt(from)) }
    }
}

/** Creates new drafts from templates. */
object ResumeFactory {
    /** A draft with the template's sample content, so the user edits rather than starting from a blank page. */
    fun fromTemplate(template: ResumeTemplate, id: String, nowMillis: Long, newId: () -> String): Resume = Resume(
        id = id,
        templateId = template.id,
        contact = template.sampleContact,
        sections = template.sections.map { sample ->
            ResumeSection(
                id = newId(),
                kind = sample.kind,
                title = sample.title,
                text = sample.text,
                entries = sample.entries.map { it.copy(id = newId()) },
            )
        },
        style = ResumeStyle(font = template.font, accentArgb = template.accentArgb),
        updatedAtMillis = nowMillis,
    )
}
