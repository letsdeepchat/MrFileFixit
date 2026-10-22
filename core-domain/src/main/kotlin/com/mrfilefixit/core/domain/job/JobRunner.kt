package com.mrfilefixit.core.domain.job

import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs one job at a time in [scope] (normally `viewModelScope`) and exposes its [state].
 *
 * Contract:
 * - [start] is ignored while a job is running and returns false.
 * - The block returns an [AppResult]; it never needs to catch exceptions other than to map them.
 * - [cancel] moves the state to [JobState.Cancelled] immediately, and the coroutine is cancelled cooperatively.
 * - [cleanup] always runs (also on cancel and failure) under [NonCancellable], so temp files are deleted.
 */
class JobRunner<T>(private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow<JobState<T>>(JobState.Idle)
    val state: StateFlow<JobState<T>> = mutableState.asStateFlow()

    private var job: Job? = null

    fun start(
        cleanup: suspend () -> Unit = {},
        block: suspend (ProgressReporter) -> AppResult<T>,
    ): Boolean {
        if (job?.isActive == true) return false
        mutableState.value = JobState.Running(progress = null)
        val reporter = ProgressReporter { progress ->
            mutableState.update { current ->
                if (current is JobState.Running) JobState.Running(progress.coerceIn(0f, 1f)) else current
            }
        }
        job = scope.launch {
            try {
                val terminal = when (val result = block(reporter)) {
                    is Result.Success -> JobState.Success(result.value)
                    is Result.Failure -> JobState.Failed(result.error)
                }
                // A cancel() that raced with completion wins: the user already saw "Cancelled".
                mutableState.update { current -> if (current is JobState.Running) terminal else current }
            } catch (e: CancellationException) {
                mutableState.value = JobState.Cancelled
                throw e
            } finally {
                withContext(NonCancellable) { cleanup() }
            }
        }
        return true
    }

    fun cancel() {
        val running = job
        if (running?.isActive == true) {
            mutableState.value = JobState.Cancelled
            running.cancel()
        }
    }

    /** Back to [JobState.Idle] after the UI has consumed a terminal state. */
    fun reset() {
        if (job?.isActive != true) mutableState.value = JobState.Idle
    }
}
