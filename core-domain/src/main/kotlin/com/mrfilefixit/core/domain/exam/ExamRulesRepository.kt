package com.mrfilefixit.core.domain.exam

import com.mrfilefixit.core.domain.result.AppResult

/** Source of exam profiles. The default implementation reads a JSON file bundled in the APK. */
interface ExamRulesRepository {
    suspend fun profiles(): AppResult<List<ExamProfile>>

    suspend fun profile(id: String): AppResult<ExamProfile>
}
