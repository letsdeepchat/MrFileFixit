package com.mrfilefixit.app.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
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
import com.mrfilefixit.core.ui.components.ToolScaffold
import com.mrfilefixit.core.ui.theme.Dimens

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSupport: (() -> Unit)?,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
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

            if (onOpenSupport != null) {
                SectionTitle(stringResource(R.string.settings_support_title))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.MinTouchTarget)
                        .clickable(role = Role.Button, onClick = onOpenSupport)
                        .padding(vertical = Dimens.SpaceS)
                        .testTag("settings_support"),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                        Text(stringResource(R.string.support_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.settings_support_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }

            SectionTitle(stringResource(R.string.settings_privacy_title))
            Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodyMedium)
            privacyPoints.forEach { PrivacyPoint(it) }
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Dimens.SpaceL),
            )
        }
    }
}

private class PrivacyPointSpec(val icon: ImageVector, @StringRes val title: Int, @StringRes val body: Int)

private val privacyPoints = listOf(
    PrivacyPointSpec(Icons.Filled.PhoneAndroid, R.string.privacy_offline_title, R.string.privacy_offline_body),
    PrivacyPointSpec(Icons.Filled.CloudOff, R.string.privacy_network_title, R.string.privacy_network_body),
    PrivacyPointSpec(Icons.Filled.AutoDelete, R.string.privacy_cache_title, R.string.privacy_cache_body),
    PrivacyPointSpec(Icons.Filled.PersonOff, R.string.privacy_metadata_title, R.string.privacy_metadata_body),
)

/** One privacy promise: icon, short title and the detail. Read by TalkBack as a single item. */
@Composable
private fun PrivacyPoint(point: PrivacyPointSpec) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpaceXs)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        Icon(point.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            Text(stringResource(point.title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(point.body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
