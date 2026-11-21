package com.mrfilefixit.app.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.mrfilefixit.app.R
import com.mrfilefixit.core.ui.theme.Dimens

/** The app languages we ship translations for; [tag] null follows the phone. Matches res/xml/locales_config. */
private enum class AppLanguage(val tag: String?) { SYSTEM(null), ENGLISH("en"), HINDI("hi") }

/**
 * In-app language choice. Android 13+ stores a per-app language itself (the same setting as the system's
 * "App languages" screen), so nothing is persisted here and the activity is recreated with the new strings.
 * Older phones have no per-app language: the app follows the phone's language.
 */
@Composable
internal fun LanguageSection() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        LanguageChoices()
    } else {
        Text(
            stringResource(R.string.settings_language_follows_phone),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LanguageChoices() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(currentLanguage(context)) }
    Column(Modifier.selectableGroup()) {
        AppLanguage.entries.forEach { language ->
            val label = when (language) {
                AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
                AppLanguage.ENGLISH -> stringResource(R.string.settings_language_english)
                AppLanguage.HINDI -> stringResource(R.string.settings_language_hindi)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.MinTouchTarget)
                    .selectable(current == language, role = Role.RadioButton) {
                        if (current != language) {
                            current = language
                            applyLanguage(context, language)
                        }
                    }
                    .testTag("settings_language_${language.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = current == language, onClick = null)
                Text(label, Modifier.padding(start = Dimens.SpaceM))
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun currentLanguage(context: Context): AppLanguage {
    val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
    val tag = if (locales == null || locales.isEmpty) null else locales[0].language
    return AppLanguage.entries.firstOrNull { it.tag != null && it.tag == tag } ?: AppLanguage.SYSTEM
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun applyLanguage(context: Context, language: AppLanguage) {
    val tag = language.tag
    context.getSystemService(LocaleManager::class.java)?.applicationLocales =
        if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
}
