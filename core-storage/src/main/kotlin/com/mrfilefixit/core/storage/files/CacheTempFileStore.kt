package com.mrfilefixit.core.storage.files

import android.content.Context
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.file.TempFileStore
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.runCatchingAppFlat
import com.mrfilefixit.core.domain.result.success
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/** Job scratch space under app-private `cache/jobs/<jobId>/`. Never on shared storage. */
@Singleton
internal class CacheTempFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : TempFileStore {
    private val root: File get() = File(context.cacheDir, JOBS_DIR)

    override suspend fun createJobDir(jobId: String): AppResult<String> = withContext(dispatchers.io) {
        runCatchingAppFlat {
            if (!JOB_ID.matches(jobId)) return@runCatchingAppFlat failure(AppError.Unexpected("InvalidJobId"))
            val dir = File(root, jobId)
            if (dir.isDirectory || dir.mkdirs()) success(dir.absolutePath) else failure(AppError.StorageFull)
        }
    }

    override suspend fun deleteJobDir(jobId: String) {
        if (!JOB_ID.matches(jobId)) return
        withContext(dispatchers.io) { File(root, jobId).deleteRecursively() }
    }

    override suspend fun deleteStale(maxAgeMillis: Long) {
        withContext(dispatchers.io) {
            val cutoff = System.currentTimeMillis() - maxAgeMillis
            root.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
        }
    }

    companion object {
        const val JOBS_DIR = "jobs"
        private val JOB_ID = Regex("^[A-Za-z0-9-]{1,64}$")
    }
}
