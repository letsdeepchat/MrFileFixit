package com.mrfilefixit.core.domain.batch

import com.mrfilefixit.core.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

object BatchPolicy {
    /** Up to this many files run in the ViewModel; more go to WorkManager so they survive process death. */
    const val INLINE_LIMIT = 10

    /** Free batch jobs per day. Watching a rewarded ad (opt-in) adds [REWARD_BATCHES]. */
    const val FREE_BATCHES_PER_DAY = 3
    const val REWARD_BATCHES = 2
}

/** Daily batch allowance, persisted in DataStore. */
interface BatchQuotaRepository {
    /** Remaining batch jobs today. */
    val remaining: Flow<Int>

    /** Uses one batch job; fails with QuotaExceeded when none is left. */
    suspend fun consume(): AppResult<Unit>

    /** Adds [count] jobs after the user earned a rewarded-ad reward (never on click). */
    suspend fun grant(count: Int)
}
