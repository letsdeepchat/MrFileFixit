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

    @Test
    fun `split plan covers custom ranges, fixed sets and single pages`() {
        val custom = PageSelection.splitPlan(SplitMode.CUSTOM, "1-3, 5, 8-", null, 10)
        assertEquals(success(listOf(1..3, 5..5, 8..10)), custom)
        assertEquals(success(listOf(1..2, 3..4, 5..5)), PageSelection.splitPlan(SplitMode.EVERY_N, "", 2, 5))
        val singles = PageSelection.splitPlan(SplitMode.SINGLE_PAGES, "junk", null, 3)
        assertEquals(success(listOf(1..1, 2..2, 3..3)), singles)
    }

    @Test
    fun `split plan rejects a missing or oversized set size`() {
        assertEquals(failure(AppError.InvalidPageSelection(5)), PageSelection.splitPlan(SplitMode.EVERY_N, "", null, 5))
        assertEquals(failure(AppError.InvalidPageSelection(5)), PageSelection.splitPlan(SplitMode.EVERY_N, "", 6, 5))
        val empty = PageSelection.splitPlan(SplitMode.SINGLE_PAGES, "", 1, 0)
        assertEquals(failure(AppError.InvalidPageSelection(0)), empty)
    }

    @Test
    fun `format keeps the order and writes ascending runs as ranges`() {
        assertEquals("3, 1-4, 9", PageSelection.format(listOf(3, 1, 2, 3, 4, 9)))
        assertEquals("", PageSelection.format(emptyList()))
        assertEquals("5", PageSelection.format(listOf(5)))
        assertEquals("2, 1", PageSelection.format(listOf(2, 1)))
        val pages = listOf(7, 8, 9, 1, 4, 5, 2)
        assertEquals(success(pages), PageSelection.parse(PageSelection.format(pages), 9))
    }

    @Test
    fun `tapping a page adds it at the end or takes it out`() {
        assertEquals("4", PageSelection.toggle("", 4, 10))
        assertEquals("4, 2", PageSelection.toggle("4", 2, 10))
        assertEquals("1, 3-4", PageSelection.toggle("1-4", 2, 10))
        assertEquals("7", PageSelection.toggle("not pages", 7, 10))
    }

    @Test
    fun `moving a page swaps it with its neighbour and ignores moves past the ends`() {
        assertEquals("2, 1, 3", PageSelection.move("1-3", 1, -1, 3))
        assertEquals("1-3", PageSelection.move("1-3", 0, -1, 3))
        assertEquals("1, 3, 2", PageSelection.move("1-3", 1, 1, 3))
    }
}
