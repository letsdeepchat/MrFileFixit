package com.mrfilefixit.core.domain.exam

/**
 * Search and filter for the exam picker. Pure, so the picker's ViewModel only holds the query and the
 * selected category and everything else is tested on the JVM.
 */
object ExamCatalog {
    /** Words people type for a category that do not appear in exam names, e.g. "bank" for SBI PO. */
    private val categoryAliases = mapOf(
        ExamCategory.SSC to "ssc staff selection",
        ExamCategory.UPSC to "upsc union public service",
        ExamCategory.BANKING to "bank banking ibps sbi insurance",
        ExamCategory.RAILWAYS to "railway railways rrb",
        ExamCategory.DEFENCE to "defence defense army navy air force",
        ExamCategory.ENTRANCE to "nta entrance engineering medical",
        ExamCategory.TEACHING to "teacher teaching tet",
        ExamCategory.STATE_PSC to "state psc",
        ExamCategory.OTHER to "",
    )

    /** Categories that have at least one profile, in pill order. */
    fun categories(profiles: List<ExamProfile>): List<ExamCategory> =
        profiles.map { it.category }.distinct().sorted()

    /**
     * Profiles in [category] (all when null) whose text contains every word of [query], ignoring case
     * and punctuation. The bundled order is kept.
     */
    fun filter(profiles: List<ExamProfile>, query: String, category: ExamCategory?): List<ExamProfile> {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        return profiles.filter { profile ->
            (category == null || profile.category == category) &&
                words.all { word -> word in haystack(profile) }
        }
    }

    private fun haystack(profile: ExamProfile): String = normalize(
        listOf(
            profile.shortName,
            profile.name,
            profile.conductingBody,
            profile.id.replace('-', ' '),
            categoryAliases[profile.category].orEmpty(),
        ).joinToString(" "),
    )

    private fun normalize(text: String): String =
        text.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
}
