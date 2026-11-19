package com.mrfilefixit.core.domain.biodata

import java.time.DateTimeException
import java.time.LocalDate
import java.time.Period

/** Age in whole years from a typed date of birth, so the user does not have to keep it up to date by hand. */
object BiodataAge {
    private val dayFirst = Regex("""^\s*(\d{1,2})\s*[-/.]\s*(\d{1,2})\s*[-/.]\s*(\d{4})\s*$""")
    private val yearFirst = Regex("""^\s*(\d{4})\s*[-/.]\s*(\d{1,2})\s*[-/.]\s*(\d{1,2})\s*$""")

    /** Parses dd-mm-yyyy (also with / or .) and yyyy-mm-dd. Null when the text is not a real date. */
    fun parse(text: String): LocalDate? {
        val (day, month, year) = dayFirst.matchEntire(text)?.destructured?.let { (d, m, y) -> Triple(d, m, y) }
            ?: yearFirst.matchEntire(text)?.destructured?.let { (y, m, d) -> Triple(d, m, y) }
            ?: return null
        return try {
            LocalDate.of(year.toInt(), month.toInt(), day.toInt())
        } catch (_: DateTimeException) {
            null
        }
    }

    /** Whole years between [dateOfBirth] and [today]; null for unparseable or impossible dates. */
    fun years(dateOfBirth: String, today: LocalDate): Int? {
        val birth = parse(dateOfBirth) ?: return null
        if (birth.isAfter(today)) return null
        return Period.between(birth, today).years.takeIf { it <= MAX_AGE }
    }

    /** dd-MM-yyyy, the format the date picker writes. */
    fun format(date: LocalDate): String = "%02d-%02d-%04d".format(date.dayOfMonth, date.monthValue, date.year)

    private const val MAX_AGE = 120
}
