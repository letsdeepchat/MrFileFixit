package com.mrfilefixit.feature.exam.data

import android.content.Context
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.exam.ExamProfile
import com.mrfilefixit.core.domain.exam.ExamRulesRepository
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.flatMap
import com.mrfilefixit.core.domain.result.runCatchingAppFlat
import com.mrfilefixit.core.domain.result.success
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads `assets/exam_rules.json` once per process and keeps the parsed profiles in memory. */
@Singleton
internal class AssetExamRulesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : ExamRulesRepository {
    private val mutex = Mutex()
    private var cache: List<ExamProfile>? = null

    override suspend fun profiles(): AppResult<List<ExamProfile>> = mutex.withLock {
        cache?.let { return@withLock success(it) }
        val loaded = withContext(dispatchers.io) {
            runCatchingAppFlat {
                val text = context.assets.open(RULES_ASSET).bufferedReader().use { it.readText() }
                ExamRulesParser.parse(text)
            }
        }
        if (loaded is Result.Success) cache = loaded.value
        loaded
    }

    override suspend fun profile(id: String): AppResult<ExamProfile> = profiles().flatMap { list ->
        list.firstOrNull { it.id == id }?.let(::success) ?: failure(AppError.Unexpected("UnknownExamProfile"))
    }

    private companion object {
        const val RULES_ASSET = "exam_rules.json"
    }
}
