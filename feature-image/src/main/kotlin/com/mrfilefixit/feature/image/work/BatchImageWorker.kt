package com.mrfilefixit.feature.image.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result as WorkResult
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mrfilefixit.core.domain.image.BatchImageUseCase
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.Result as DomainResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Runs large image batches off the screen's lifecycle. It is plain background work, never a foreground service: the
 * app declares no foreground service permissions, so Play needs no foreground service declaration.
 */
@HiltWorker
class BatchImageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val batch: BatchImageUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): WorkResult = coroutineScope {
        val jobDir = inputData.getString(KEY_JOB_DIR)?.let(::File)?.takeIf { it.isDirectory }
            ?: return@coroutineScope WorkResult.failure()
        val (sources, spec) = BatchSpecSerialization.decode(File(jobDir, SPEC_FILE).readText())
        // ListenableWorker.Result shadows the domain Result inside a worker, hence the import alias.
        // One progress event per image; each is published without blocking the batch.
        val reporter = ProgressReporter { p -> launch { setProgress(workDataOf(KEY_PROGRESS to p)) } }
        when (val outcome = batch(sources, spec, jobDir.absolutePath, reporter)) {
            is DomainResult.Success -> {
                File(jobDir, RESULT_FILE).writeText(BatchSpecSerialization.encodeResult(outcome.value))
                WorkResult.success()
            }
            is DomainResult.Failure -> WorkResult.failure(workDataOf(KEY_ERROR to outcome.error.javaClass.simpleName))
        }
    }

    companion object {
        const val KEY_JOB_DIR = "jobDir"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val SPEC_FILE = "batch.json"
        const val RESULT_FILE = "result.json"
    }
}
