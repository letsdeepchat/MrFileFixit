package com.mrfilefixit.app.logging

import com.mrfilefixit.app.BuildConfig
import com.mrfilefixit.core.domain.logging.AppLogger
import javax.inject.Inject
import timber.log.Timber

/** Release tree: logs nothing. There is no crash-reporting SDK, so no user data can leave via logs. */
object ReleaseTree : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean = false

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) = Unit
}

/** [AppLogger] backed by Timber. Messages are built lazily, so release builds never even format them. */
class TimberLogger @Inject constructor() : AppLogger {
    override fun debug(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Timber.tag(tag).d(message())
    }

    override fun warn(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Timber.tag(tag).w(message())
    }

    override fun error(tag: String, throwable: Throwable?, message: () -> String) {
        if (BuildConfig.DEBUG) Timber.tag(tag).e(throwable, message())
    }
}
