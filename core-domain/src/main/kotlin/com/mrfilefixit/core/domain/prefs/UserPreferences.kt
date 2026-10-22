package com.mrfilefixit.core.domain.prefs

import kotlinx.coroutines.flow.Flow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Non-sensitive app settings. Passwords and file data are never stored. */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** EXIF is stripped on export unless the user turns this on (location is always removed). */
    val keepMetadata: Boolean = false,
    /** The exam disclaimer was acknowledged at least once. It is still shown on every exam screen. */
    val examDisclaimerAcknowledged: Boolean = false,
)

interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setKeepMetadata(enabled: Boolean)

    suspend fun acknowledgeExamDisclaimer()
}
