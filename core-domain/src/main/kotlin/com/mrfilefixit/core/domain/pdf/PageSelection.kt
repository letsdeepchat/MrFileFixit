package com.mrfilefixit.core.domain.pdf

import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.success

/**
 * Parses page selections typed by users: "1-3, 5, 8-" (open end = last page), "all".
 * Both ASCII and Devanagari digits are accepted. Order and duplicates are preserved for reordering.
 */
object PageSelection {
    private const val MAX_TOKENS = 1000

    fun parse(input: String, pageCount: Int): AppResult<List<Int>> {
        val normalized = input.map { it.devanagariToAscii() }.joinToString("").trim()
        if (pageCount <= 0) return failure(AppError.InvalidPageSelection(pageCount))
        if (normalized.isEmpty() || normalized.equals("all", ignoreCase = true)) return success((1..pageCount).toList())
        val tokens = normalized.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty() || tokens.size > MAX_TOKENS) return failure(AppError.InvalidPageSelection(pageCount))
        val pages = mutableListOf<Int>()
        for (token in tokens) {
            val range = parseToken(token, pageCount) ?: return failure(AppError.InvalidPageSelection(pageCount))
            pages.addAll(range)
        }
        return success(pages)
    }

    /** Parses "1-3, 4-6" into ranges for Split. Ranges must not be descending. */
    fun parseRanges(input: String, pageCount: Int): AppResult<List<IntRange>> {
        val tokens = input.map { it.devanagariToAscii() }.joinToString("")
            .split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return failure(AppError.InvalidPageSelection(pageCount))
        val ranges = tokens.map { token ->
            parseToken(token, pageCount)?.takeIf { it.first <= it.last }?.let { it.first..it.last }
                ?: return failure(AppError.InvalidPageSelection(pageCount))
        }
        return success(ranges)
    }

    /** Splits every [n] pages: 1-n, n+1-2n, ... */
    fun everyNPages(n: Int, pageCount: Int): List<IntRange> =
        (1..pageCount step n.coerceAtLeast(1)).map { start -> start..minOf(start + n - 1, pageCount) }

    /**
     * The files a split will produce, one range per file. [ranges] is read for [SplitMode.CUSTOM] and
     * [everyN] for [SplitMode.EVERY_N]. The UI shows this as a live preview before the job runs.
     */
    fun splitPlan(mode: SplitMode, ranges: String, everyN: Int?, pageCount: Int): AppResult<List<IntRange>> =
        when {
            pageCount <= 0 -> failure(AppError.InvalidPageSelection(pageCount))
            mode == SplitMode.CUSTOM -> parseRanges(ranges, pageCount)
            mode == SplitMode.SINGLE_PAGES -> success(everyNPages(1, pageCount))
            everyN == null || everyN !in 1..pageCount -> failure(AppError.InvalidPageSelection(pageCount))
            else -> success(everyNPages(everyN, pageCount))
        }

    private fun parseToken(token: String, pageCount: Int): IntProgression? {
        val parts = token.split('-').map { it.trim() }
        return when (parts.size) {
            1 -> parts[0].toIntOrNull()?.takeIf { it in 1..pageCount }?.let { it..it }
            2 -> {
                val start = if (parts[0].isEmpty()) 1 else parts[0].toIntOrNull()
                val end = if (parts[1].isEmpty()) pageCount else parts[1].toIntOrNull()
                if (start == null || end == null || start !in 1..pageCount || end !in 1..pageCount) {
                    null
                } else if (start <= end) {
                    start..end
                } else {
                    start downTo end
                }
            }
            else -> null
        }
    }

    private fun Char.devanagariToAscii(): Char =
        if (this in '०'..'९') '0' + (this - '०') else this
}
