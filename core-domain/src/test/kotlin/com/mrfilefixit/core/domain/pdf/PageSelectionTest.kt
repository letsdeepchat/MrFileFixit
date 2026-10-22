package com.mrfilefixit.core.domain.pdf

import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.success
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PageSelectionTest {
    @Test
    fun `parses ranges, singles, open ends and reverse ranges`() {
        assertEquals(success(listOf(1, 2, 3, 5, 8, 9, 10)), PageSelection.parse("1-3, 5, 8-", 10))
        assertEquals(success(listOf(3, 2, 1)), PageSelection.parse("3-1", 10))
        assertEquals(success(listOf(1, 2)), PageSelection.parse("-2", 10))
        assertEquals(success((1..4).toList()), PageSelection.parse("all", 4))
        assertEquals(success((1..4).toList()), PageSelection.parse("  ", 4))
    }

    @Test
    fun `accepts Devanagari digits`() {
        assertEquals(success(listOf(1, 2, 3)), PageSelection.parse("१-३", 5))
    }

    @Test
    fun `rejects out of range and garbage`() {
        assertEquals(failure(AppError.InvalidPageSelection(5)), PageSelection.parse("6", 5))
        assertEquals(failure(AppError.InvalidPageSelection(5)), PageSelection.parse("0", 5))
        assertEquals(failure(AppError.InvalidPageSelection(5)), PageSelection.parse("1-2-3", 5))
        assertEquals(failure(AppError.InvalidPageSelection(5)), PageSelection.parse("abc", 5))
    }

    @Test
    fun `split ranges must ascend`() {
        assertEquals(success(listOf(1..2, 3..5)), PageSelection.parseRanges("1-2, 3-5", 5))
        assertEquals(failure(AppError.InvalidPageSelection(5)), PageSelection.parseRanges("3-1", 5))
    }

    @Test
    fun `every n pages covers the document`() {
        assertEquals(listOf(1..3, 4..6, 7..7), PageSelection.everyNPages(3, 7))
    }
}
