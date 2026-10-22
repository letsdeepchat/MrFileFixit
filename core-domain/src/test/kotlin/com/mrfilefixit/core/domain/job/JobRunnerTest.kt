package com.mrfilefixit.core.domain.job

import app.cash.turbine.test
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.success
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JobRunnerTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    @Test
    fun `emits running with progress then success`() = scope.runTest {
        val runner = JobRunner<String>(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        runner.state.test {
            assertEquals(JobState.Idle, awaitItem())
            runner.start { progress ->
                progress.report(0.5f)
                gate.await()
                success("done")
            }
            assertEquals(JobState.Running(null), awaitItem())
            assertEquals(JobState.Running(0.5f), awaitItem())
            gate.complete(Unit)
            assertEquals(JobState.Success("done"), awaitItem())
        }
    }

    @Test
    fun `failure result becomes Failed state`() = scope.runTest {
        val runner = JobRunner<String>(backgroundScope)
        runner.start { failure(AppError.CorruptFile) }
        runCurrent()
        assertEquals(JobState.Failed(AppError.CorruptFile), runner.state.value)
    }

    @Test
    fun `cancel moves to Cancelled and runs cleanup`() = scope.runTest {
        val runner = JobRunner<String>(backgroundScope)
        var cleaned = false
        runner.start(cleanup = { cleaned = true }) { awaitCancellation() }
        runCurrent()
        assertTrue(runner.state.value.isRunning)
        runner.cancel()
        runCurrent()
        assertEquals(JobState.Cancelled, runner.state.value)
        assertTrue(cleaned)
    }

    @Test
    fun `second start while running is ignored`() = scope.runTest {
        val runner = JobRunner<String>(backgroundScope)
        assertTrue(runner.start { awaitCancellation() })
        runCurrent()
        assertFalse(runner.start { success("second") })
        runner.cancel()
    }

    @Test
    fun `progress is clamped and reset returns to idle`() = scope.runTest {
        val runner = JobRunner<Int>(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        runner.start { p ->
            p.report(7f)
            gate.await()
            success(1)
        }
        runCurrent()
        assertEquals(JobState.Running(1f), runner.state.value)
        gate.complete(Unit)
        runCurrent()
        runner.reset()
        assertEquals(JobState.Idle, runner.state.value)
    }

    @Test
    fun `slice maps child progress into parent range`() {
        val seen = mutableListOf<Float>()
        val parent = ProgressReporter { seen += it }
        val child = parent.slice(0.5f, 1f)
        child.report(0f)
        child.report(0.5f)
        child.report(1f)
        assertEquals(listOf(0.5f, 0.75f, 1f), seen)
    }
}
