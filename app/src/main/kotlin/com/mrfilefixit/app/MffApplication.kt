package com.mrfilefixit.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mrfilefixit.app.logging.ReleaseTree
import com.mrfilefixit.core.domain.dispatchers.ApplicationScope
import com.mrfilefixit.core.domain.file.TempFileStore
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class MffApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var tempFiles: TempFileStore

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Debug: logcat. Release: a tree that drops everything, so file names, paths or content can never be logged.
        Timber.plant(if (BuildConfig.DEBUG) Timber.DebugTree() else ReleaseTree)
        // Leftovers from a killed process. Off the main thread so cold start is not delayed.
        appScope.launch { tempFiles.deleteStale(TimeUnit.HOURS.toMillis(STALE_HOURS)) }
    }

    private companion object {
        const val STALE_HOURS = 6L
    }
}
