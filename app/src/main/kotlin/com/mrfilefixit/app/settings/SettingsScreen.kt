package com.mrfilefixit.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrfilefixit.app.BuildConfig
import com.mrfilefixit.app.MainViewModel
import com.mrfilefixit.app.R
import com.mrfilefixit.core.domain.prefs.ThemeMode
import com.mrfilefixit.core.ui.ads.LocalAdSlots
import com.mrfilefixit.core.ui.components.ToolScaffold
import com.mrfilefixit.core.ui.share.findActivity
import com.mrfilefixit.core.ui.theme.Dimens

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: MainViewModel = hiltViewModel()) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val adSlots = LocalAdSlots.current
    val context = LocalContext.current
    ToolScaffold(title = stringResource(R.string.nav_settings), onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.SpaceM)
                .widthIn(max = Dimens.CardMaxWidth),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            SectionTitle(stringResource(R.string.settings_theme))
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    val label = stringResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> R.string.settings_theme_system
                            ThemeMode.LIGHT -> R.string.settings_theme_light
                            ThemeMode.DARK -> R.string.settings_theme_dark
                        },
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.MinTouchTarget)
                            .selectable(prefs.themeMode == mode, role = Role.RadioButton) {
                                viewModel.setThemeMode(mode)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = prefs.themeMode == mode, onClick = null)
                        Text(label, Modifier.padding(start = Dimens.SpaceM))
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.MinTouchTarget)
                    .toggleable(prefs.dynamicColor, role = Role.Switch) { viewModel.setDynamicColor(it) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_dynamic_color), Modifier.weight(1f))
                Switch(checked = prefs.dynamicColor, onCheckedChange = null)
            }

            SectionTitle(stringResource(R.string.settings_privacy_title))
            Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodyMedium)
            if (adSlots.privacyOptionsRequired) {
                OutlinedButton(
                    onClick = { context.findActivity()?.let(adSlots::showPrivacyOptions) },
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                ) { Text(stringResource(R.string.settings_ads_privacy)) }
            }
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Dimens.SpaceL),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = Dimens.SpaceM)
            .semantics { heading() },
    )
}
