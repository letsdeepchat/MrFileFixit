package com.mrfilefixit.feature.image.ui

import com.mrfilefixit.core.domain.image.BatchOutcome
import com.mrfilefixit.core.domain.job.JobState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Switching tabs shows that tool's own options: a finished run stays with the tool that ran it. */
class ImageToolUiStateTest {
    private val done = JobState.Success(BatchOutcome(emptyList(), emptyList()))

    @Test
    fun `results show only on the tab of the tool that made them`() {
        val enhanced = ImageToolUiState(tool = ImageTool.ENHANCE, job = done, ranTool = ImageTool.ENHANCE)
        assertTrue(enhanced.isDone)
        ImageTool.entries.filter { it != ImageTool.ENHANCE }.forEach { other ->
            val switched = enhanced.copy(tool = other)
            assertFalse(switched.isDone, "$other still shows Enhance results")
            assertEquals(JobState.Idle, switched.shownJob)
        }
        // Coming back shows them again; they were never thrown away.
        assertTrue(enhanced.copy(tool = ImageTool.COMPRESS).copy(tool = ImageTool.ENHANCE).isDone)
    }

    @Test
    fun `an error or cancel also stays on its own tab`() {
        val cancelled = ImageToolUiState(tool = ImageTool.CROP, job = JobState.Cancelled, ranTool = ImageTool.CROP)
        assertEquals(JobState.Cancelled, cancelled.shownJob)
        assertEquals(JobState.Idle, cancelled.copy(tool = ImageTool.EDIT).shownJob)
    }

    @Test
    fun `a run in progress shows on every tab`() {
        val running = ImageToolUiState(tool = ImageTool.EDIT, job = JobState.Running(0.5f), ranTool = ImageTool.EDIT)
        assertTrue(running.copy(tool = ImageTool.RESIZE).shownJob is JobState.Running)
    }

    @Test
    fun `a batch picked up again with no known tool shows everywhere`() {
        assertTrue(ImageToolUiState(tool = ImageTool.ROTATE, job = done, ranTool = null).isDone)
    }
}
