package com.mrfilefixit.core.domain.job

/** Receives progress in 0..1. Implementations must be cheap; engines call it once per page or image. */
fun interface ProgressReporter {
    fun report(progress: Float)

    companion object {
        val NONE = ProgressReporter { }
    }
}

/**
 * Maps this reporter's 0..1 onto [start]..[end] of the parent, so a multi-step job can give each step
 * a slice of the bar.
 */
fun ProgressReporter.slice(start: Float, end: Float): ProgressReporter {
    require(start in 0f..1f && end in start..1f) { "Invalid slice $start..$end" }
    return ProgressReporter { p -> report(start + (end - start) * p.coerceIn(0f, 1f)) }
}

/** Progress for step [index] (0-based) of [count] equal steps, with [inner] progress inside the step. */
fun ProgressReporter.step(index: Int, count: Int, inner: Float = 1f) {
    if (count <= 0) return
    report(((index + inner.coerceIn(0f, 1f)) / count).coerceIn(0f, 1f))
}
