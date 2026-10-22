package com.mrfilefixit.feature.image.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.image.BatchOutcome
import com.mrfilefixit.core.domain.image.ImageBatchSpec
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.domain.result.AppError
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Hands big batches to WorkManager and maps WorkInfo back to the app's [JobState]. */
class ImageBatchScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    private val workManager get() = WorkManager.getInstance(context)

    suspend fun enqueue(jobDir: String, sources: List<FileRef>, spec: ImageBatchSpec): UUID {
        withContext(dispatchers.io) {
            File(jobDir, BatchImageWorker.SPEC_FILE).writeText(BatchSpecSerialization.encode(sources, spec))
        }
        val request = OneTimeWorkRequestBuilder<BatchImageWorker>()
            .setInputData(workDataOf(BatchImageWorker.KEY_JOB_DIR to jobDir))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        return request.id
    }

    fun observe(id: UUID, jobDir: String): Flow<JobState<BatchOutcome>> = workManager.getWorkInfoByIdFlow(
        id,
    ).map { info ->
        when (info?.state) {
            null, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> JobState.Running(null)
            WorkInfo.State.RUNNING -> JobState.Running(info.progress.getFloat(BatchImageWorker.KEY_PROGRESS, 0f))
            WorkInfo.State.SUCCEEDED -> {
                val text = withContext(dispatchers.io) { File(jobDir, BatchImageWorker.RESULT_FILE).readText() }
                JobState.Success(BatchSpecSerialization.decodeResult(text))
            }
            WorkInfo.State.FAILED -> JobState.Failed(
                AppError.Unexpected(info.outputData.getString(BatchImageWorker.KEY_ERROR) ?: "Batch"),
            )
            WorkInfo.State.CANCELLED -> JobState.Cancelled
        }
    }

    fun cancel(id: UUID) {
        workManager.cancelWorkById(id)
    }

    private companion object {
        const val UNIQUE_NAME = "image-batch"
    }
}
