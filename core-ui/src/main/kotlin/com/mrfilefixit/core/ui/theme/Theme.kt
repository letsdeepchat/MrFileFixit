package com.mrfilefixit.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.mrfilefixit.core.domain.prefs.ThemeMode

@Immutable
data class StatusColors(val pass: Color, val fail: Color)

val LocalStatusColors = staticCompositionLocalOf { StatusColors(PassLight, FailLight) }

/**
 * App theme: Material 3, dynamic color on Android 12+, dark mode. Typography uses the default
 * scale in `sp`, so it follows the user's font size (tested up to 200%).
 */
@Composable
fun MffTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val status = if (dark) StatusColors(PassDark, FailDark) else StatusColors(PassLight, FailLight)
    CompositionLocalProvider(LocalStatusColors provides status) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography(), content = content)
    }
}
