package com.mrfilefixit.core.domain.job

import com.mrfilefixit.core.domain.result.AppError

/** State of one tool run. Exposed by [JobRunner] as a StateFlow. */
sealed interface JobState<out T> {
    data object Idle : JobState<Nothing>

    /** [progress] is in 0..1, or null while the total amount of work is unknown. */
    data class Running(val progress: Float?) : JobState<Nothing>

    data class Success<out T>(val value: T) : JobState<T>

    data class Failed(val error: AppError) : JobState<Nothing>

    data object Cancelled : JobState<Nothing>
}

val JobState<*>.isRunning: Boolean get() = this is JobState.Running

val JobState<*>.isTerminal: Boolean
    get() = this is JobState.Success || this is JobState.Failed || this is JobState.Cancelled
