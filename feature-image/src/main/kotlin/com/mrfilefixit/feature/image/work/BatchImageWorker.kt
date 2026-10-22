package com.mrfilefixit.feature.image.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result as WorkResult
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mrfilefixit.core.domain.image.BatchImageUseCase
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.feature.image.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * Runs large image batches so they survive the app going to the background. The worker is a
 * foreground service only while it runs (mediaProcessing on Android 15+, dataSync before).
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
        setForeground(getForegroundInfo())
        val (sources, spec) = BatchSpecSerialization.decode(File(jobDir, SPEC_FILE).readText())
        // One progress event per image; each is published without blocking the batch.
        val reporter = ProgressReporter { p -> launch { setProgress(workDataOf(KEY_PROGRESS to p)) } }
        when (val outcome = batch(sources, spec, jobDir.absolutePath, reporter)) {
            is Result.Success -> {
                File(jobDir, RESULT_FILE).writeText(BatchSpecSerialization.encodeResult(outcome.value))
                WorkResult.success()
            }
            is Result.Failure -> WorkResult.failure(workDataOf(KEY_ERROR to outcome.error.javaClass.simpleName))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.notification_channel_jobs),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        // No file names in the notification: it can show on the lock screen.
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(applicationContext.getString(R.string.notification_job_running))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(0, 0, true)
            .build()
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
                ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else -> ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_JOB_DIR = "jobDir"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val SPEC_FILE = "batch.json"
        const val RESULT_FILE = "result.json"
        private const val CHANNEL_ID = "jobs"
        private const val NOTIFICATION_ID = 4201
    }
}
