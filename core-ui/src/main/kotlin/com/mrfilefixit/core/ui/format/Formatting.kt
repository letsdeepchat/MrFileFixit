package com.mrfilefixit.core.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

private const val KIB = 1024.0
private const val MIB = KIB * KIB

/** Locale-aware number formatting (Devanagari digits stay Latin, as Indian forms expect). */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale =
    ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()

@Composable
@ReadOnlyComposable
fun formatNumber(value: Number): String = NumberFormat.getInstance(currentLocale()).format(value)

/** KB with one decimal under 10 KB, whole numbers above; MB above 1 MB. 1 KB = 1024 bytes, as exam portals use. */
@Composable
@ReadOnlyComposable
fun formatBytes(bytes: Long): String {
    val format = NumberFormat.getInstance(currentLocale())
    return when {
        bytes >= MIB -> {
            format.maximumFractionDigits = 1
            "${format.format(bytes / MIB)} MB"
        }
        else -> {
            format.maximumFractionDigits = if (bytes < ONE_DECIMAL_BELOW) 1 else 0
            "${format.format(bytes / KIB)} KB"
        }
    }
}

/** Below 10 KiB show one decimal ("9.8 KB"); above, whole numbers are precise enough. */
private const val ONE_DECIMAL_BELOW = 10 * KIB

/** An ISO date (yyyy-MM-dd) in the user's locale, e.g. "24 Oct 2026". Falls back to the raw text. */
@Composable
@ReadOnlyComposable
fun formatIsoDate(iso: String): String = formatIsoDate(iso, currentLocale())

internal fun formatIsoDate(iso: String, locale: Locale): String = try {
    LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
} catch (_: DateTimeParseException) {
    iso
}
