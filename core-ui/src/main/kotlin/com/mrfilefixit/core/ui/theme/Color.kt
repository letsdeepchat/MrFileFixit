package com.mrfilefixit.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette (used when dynamic color is off or unavailable, i.e. below Android 12).
private val Teal40 = Color(0xFF006A60)
private val Teal80 = Color(0xFF53DBC9)
private val Saffron40 = Color(0xFF8B5000)
private val Saffron80 = Color(0xFFFFB870)
private val Slate40 = Color(0xFF4A6360)
private val Slate80 = Color(0xFFB1CCC7)

/** Semantic colours for pass/fail that keep 4.5:1 contrast on surfaces in both themes. */
val PassLight = Color(0xFF1B6E2A)
val PassDark = Color(0xFF8CD99A)
val FailLight = Color(0xFFB3261E)
val FailDark = Color(0xFFF2B8B5)

internal val LightColors = lightColorScheme(
    primary = Teal40,
    secondary = Slate40,
    tertiary = Saffron40,
)

internal val DarkColors = darkColorScheme(
    primary = Teal80,
    secondary = Slate80,
    tertiary = Saffron80,
)
