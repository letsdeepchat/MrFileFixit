package com.mrfilefixit.core.domain.batch

/** Batches are free and unlimited; only where they run depends on their size. */
object BatchPolicy {
    /** Up to this many files run in the ViewModel; more go to WorkManager so they survive process death. */
    const val INLINE_LIMIT = 10
}
