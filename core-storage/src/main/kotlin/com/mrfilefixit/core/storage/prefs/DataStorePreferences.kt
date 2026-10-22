package com.mrfilefixit.core.storage.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mrfilefixit.core.domain.batch.BatchPolicy
import com.mrfilefixit.core.domain.batch.BatchQuotaRepository
import com.mrfilefixit.core.domain.prefs.ThemeMode
import com.mrfilefixit.core.domain.prefs.UserPreferences
import com.mrfilefixit.core.domain.prefs.UserPreferencesRepository
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.success
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Single DataStore file in app-private storage; excluded from backup by data_extraction_rules. */
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private object Keys {
    val THEME = stringPreferencesKey("theme_mode")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val KEEP_METADATA = booleanPreferencesKey("keep_metadata")
    val DISCLAIMER = booleanPreferencesKey("exam_disclaimer_ack")
    val BATCH_DAY = longPreferencesKey("batch_day")
    val BATCH_USED = intPreferencesKey("batch_used")
    val BATCH_BONUS = intPreferencesKey("batch_bonus")
}

@Singleton
internal class DataStoreUserPreferences @Inject constructor(
    @ApplicationContext context: Context,
) : UserPreferencesRepository {
    private val store = context.settingsStore

    override val preferences: Flow<UserPreferences> = store.data.map { p ->
        UserPreferences(
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
            keepMetadata = p[Keys.KEEP_METADATA] ?: false,
            examDisclaimerAcknowledged = p[Keys.DISCLAIMER] ?: false,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.THEME] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setKeepMetadata(enabled: Boolean) {
        store.edit { it[Keys.KEEP_METADATA] = enabled }
    }

    override suspend fun acknowledgeExamDisclaimer() {
        store.edit { it[Keys.DISCLAIMER] = true }
    }
}

/** Daily batch allowance. Resets at local midnight; rewarded-ad bonuses expire with the day. */
@Singleton
internal class DataStoreBatchQuota @Inject constructor(
    @ApplicationContext context: Context,
    private val clock: Clock,
) : BatchQuotaRepository {
    private val store = context.settingsStore

    private fun today(): Long = LocalDate.now(clock).toEpochDay()

    override val remaining: Flow<Int> = store.data.map { p -> remainingIn(p) }

    private fun remainingIn(p: Preferences): Int {
        if (p[Keys.BATCH_DAY] != today()) return BatchPolicy.FREE_BATCHES_PER_DAY
        val used = p[Keys.BATCH_USED] ?: 0
        val bonus = p[Keys.BATCH_BONUS] ?: 0
        return (BatchPolicy.FREE_BATCHES_PER_DAY + bonus - used).coerceAtLeast(0)
    }

    override suspend fun consume(): AppResult<Unit> {
        var allowed = false
        store.edit { p ->
            rollOver(p)
            if (remainingIn(p) > 0) {
                p[Keys.BATCH_USED] = (p[Keys.BATCH_USED] ?: 0) + 1
                allowed = true
            }
        }
        return if (allowed) success(Unit) else failure(AppError.QuotaExceeded(remaining = 0))
    }

    override suspend fun grant(count: Int) {
        require(count in 1..BatchPolicy.REWARD_BATCHES) { "Unexpected reward size" }
        store.edit { p ->
            rollOver(p)
            p[Keys.BATCH_BONUS] = (p[Keys.BATCH_BONUS] ?: 0) + count
        }
    }

    private fun rollOver(p: androidx.datastore.preferences.core.MutablePreferences) {
        if (p[Keys.BATCH_DAY] != today()) {
            p[Keys.BATCH_DAY] = today()
            p[Keys.BATCH_USED] = 0
            p[Keys.BATCH_BONUS] = 0
        }
    }
}
