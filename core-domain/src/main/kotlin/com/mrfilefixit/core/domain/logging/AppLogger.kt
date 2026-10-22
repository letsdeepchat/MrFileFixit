package com.mrfilefixit.core.domain.logging

/**
 * Logging facade. Messages must never contain file names, paths, URIs or file content.
 * Log sizes, counts, durations and [com.mrfilefixit.core.domain.result.AppError] values instead.
 *
 * Release builds bind a no-op implementation.
 */
interface AppLogger {
    fun debug(tag: String, message: () -> String)

    fun warn(tag: String, message: () -> String)

    fun error(tag: String, throwable: Throwable? = null, message: () -> String)
}

object NoOpLogger : AppLogger {
    override fun debug(tag: String, message: () -> String) = Unit

    override fun warn(tag: String, message: () -> String) = Unit

    override fun error(tag: String, throwable: Throwable?, message: () -> String) = Unit
}
