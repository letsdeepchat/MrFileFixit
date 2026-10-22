package com.mrfilefixit.core.domain.result

import kotlin.coroutines.cancellation.CancellationException

/**
 * Typed result returned by every layer. Exceptions never cross a layer boundary:
 * the data layer converts them with [runCatchingApp].
 */
sealed interface Result<out T, out E> {
    data class Success<out T>(val value: T) : Result<T, Nothing>

    data class Failure<out E>(val error: E) : Result<Nothing, E>
}

typealias AppResult<T> = Result<T, AppError>

fun <T> success(value: T): AppResult<T> = Result.Success(value)

fun failure(error: AppError): AppResult<Nothing> = Result.Failure(error)

val AppResult<*>.isSuccess: Boolean get() = this is Result.Success

fun <T, E> Result<T, E>.getOrNull(): T? = (this as? Result.Success)?.value

fun <T, E> Result<T, E>.errorOrNull(): E? = (this as? Result.Failure)?.error

inline fun <T, E, R> Result<T, E>.map(transform: (T) -> R): Result<R, E> = when (this) {
    is Result.Success -> Result.Success(transform(value))
    is Result.Failure -> this
}

inline fun <T, E, R> Result<T, E>.flatMap(transform: (T) -> Result<R, E>): Result<R, E> = when (this) {
    is Result.Success -> transform(value)
    is Result.Failure -> this
}

inline fun <T, E, F> Result<T, E>.mapError(transform: (E) -> F): Result<T, F> = when (this) {
    is Result.Success -> this
    is Result.Failure -> Result.Failure(transform(error))
}

inline fun <T, E, R> Result<T, E>.fold(onSuccess: (T) -> R, onFailure: (E) -> R): R = when (this) {
    is Result.Success -> onSuccess(value)
    is Result.Failure -> onFailure(error)
}

inline fun <T, E> Result<T, E>.onSuccess(action: (T) -> Unit): Result<T, E> {
    if (this is Result.Success) action(value)
    return this
}

inline fun <T, E> Result<T, E>.onFailure(action: (E) -> Unit): Result<T, E> {
    if (this is Result.Failure) action(error)
    return this
}

/** Collects a list of results into one: the first failure wins. */
fun <T, E> List<Result<T, E>>.collectAll(): Result<List<T>, E> {
    val values = ArrayList<T>(size)
    for (result in this) {
        when (result) {
            is Result.Success -> values += result.value
            is Result.Failure -> return result
        }
    }
    return Result.Success(values)
}

/**
 * Runs [block] and converts any thrown exception into an [AppError].
 * [CancellationException] is always rethrown so structured concurrency keeps working.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> runCatchingApp(
    mapError: (Throwable) -> AppError = AppError::fromThrowable,
    block: () -> T,
): AppResult<T> = try {
    Result.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.Failure(mapError(e))
}

/** Like [runCatchingApp] but for blocks that already return an [AppResult]. */
@Suppress("TooGenericExceptionCaught")
inline fun <T> runCatchingAppFlat(
    mapError: (Throwable) -> AppError = AppError::fromThrowable,
    block: () -> AppResult<T>,
): AppResult<T> = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.Failure(mapError(e))
}
