package com.mrfilefixit.core.domain.dispatchers

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Injected instead of hardcoding [Dispatchers], so tests can use a test dispatcher. */
interface DispatcherProvider {
    /** CPU-bound work: decoding, encoding, rendering. */
    val default: CoroutineDispatcher

    /** Blocking I/O: reading and writing files. */
    val io: CoroutineDispatcher

    val main: CoroutineDispatcher
}

class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val main: CoroutineDispatcher = Dispatchers.Main
}
