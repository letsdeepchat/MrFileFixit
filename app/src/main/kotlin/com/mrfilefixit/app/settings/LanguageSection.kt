package com.mrfilefixit.app.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
            val label = languageLabel(language)
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

@Composable
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
    AppLanguage.ENGLISH -> stringResource(R.string.settings_language_english)
    AppLanguage.HINDI -> stringResource(R.string.settings_language_hindi)
}

/**
 * The language switch in the Home top bar: a translate icon that opens a menu of the same choices as Settings →
 * Language. Phones before Android 13 have no per-app language, so the menu says the app follows the phone.
 */
@Composable
internal fun LanguageMenuButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("home_language")) {
            Icon(Icons.Filled.Translate, contentDescription = stringResource(R.string.settings_language))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val current = currentLanguage(context)
                AppLanguage.entries.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(languageLabel(language)) },
                        leadingIcon = { RadioButton(selected = current == language, onClick = null) },
                        onClick = {
                            open = false
                            if (current != language) applyLanguage(context, language)
                        },
                        modifier = Modifier.testTag("home_language_${language.name.lowercase()}"),
                    )
                }
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_language_follows_phone)) },
                    onClick = { open = false },
                )
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
