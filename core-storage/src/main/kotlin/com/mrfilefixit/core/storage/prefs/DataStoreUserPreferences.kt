package com.mrfilefixit.core.storage.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mrfilefixit.core.domain.prefs.ThemeMode
import com.mrfilefixit.core.domain.prefs.UserPreferences
import com.mrfilefixit.core.domain.prefs.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
