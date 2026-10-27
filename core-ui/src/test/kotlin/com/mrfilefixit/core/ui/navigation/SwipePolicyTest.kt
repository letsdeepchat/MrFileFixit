package com.mrfilefixit.core.ui.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SwipePolicyTest {
    @Test
    fun `drags starting in either system edge are left to the back gesture`() {
        assertTrue(SwipePolicy.isSystemEdge(x = 10f, width = 1080f, edge = 66f))
        assertTrue(SwipePolicy.isSystemEdge(x = 1050f, width = 1080f, edge = 66f))
        assertFalse(SwipePolicy.isSystemEdge(x = 540f, width = 1080f, edge = 66f))
        assertFalse(SwipePolicy.isSystemEdge(x = 66f, width = 1080f, edge = 66f))
    }

    @Test
    fun `back on another root tab goes home`() {
        assertEquals(0, SwipePolicy.backTarget(currentPage = 3, currentIsInner = false))
        assertEquals(0, SwipePolicy.backTarget(currentPage = 5, currentIsInner = false))
    }

    @Test
    fun `back on home or an inner screen is not taken by the tabs`() {
        assertNull(SwipePolicy.backTarget(currentPage = 0, currentIsInner = false))
        assertNull(SwipePolicy.backTarget(currentPage = 2, currentIsInner = true))
    }

    @Test
    fun `an inner screen reported by any owner makes the tab inner until it leaves`() {
        val tracker = InnerScreenTracker()
        val list = Any()
        val detail = Any()
        assertFalse(tracker.isInner)
        tracker.set(list, false)
        tracker.set(detail, true)
        assertTrue(tracker.isInner)
        tracker.remove(detail)
        assertFalse(tracker.isInner)
    }
}
