package com.mrfilefixit.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.ui.R
import com.mrfilefixit.core.ui.error.appErrorMessage
import com.mrfilefixit.core.ui.preview.FontScalePreviews
import com.mrfilefixit.core.ui.preview.ThemePreviews
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.theme.MffTheme

/** Shown on every exam screen. Not dismissible: the rules are samples, not official. */
@Composable
fun ExamDisclaimerCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(modifier = Modifier.padding(Dimens.SpaceM), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.cd_info_icon))
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                Text(stringResource(R.string.disclaimer_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.disclaimer_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Progress with a cancel button. [progress] null means indeterminate. Announced politely by TalkBack. */
@Composable
fun JobProgressCard(progress: Float?, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(Dimens.SpaceM)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            val label = if (progress == null) {
                stringResource(R.string.job_working)
            } else {
                stringResource(R.string.job_progress_percent, (progress * PERCENT).toInt())
            }
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (progress == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .align(Alignment.End)
                    .heightIn(min = Dimens.MinTouchTarget),
            ) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

private const val PERCENT = 100

@Composable
fun ErrorCard(error: AppError, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(Dimens.SpaceM)
                .semantics { liveRegion = LiveRegionMode.Assertive },
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null)
            Text(appErrorMessage(error), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (onRetry != null) {
                Button(onClick = onRetry, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
    }
}

@ThemePreviews
@FontScalePreviews
@Composable
private fun CardsPreview() {
    MffTheme(dynamicColor = false) {
        Column(Modifier.padding(Dimens.SpaceM), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            ExamDisclaimerCard()
            JobProgressCard(progress = 0.42f, onCancel = {})
            ErrorCard(AppError.TargetSizeUnreachable(9_000, 10_240, 20_480), onRetry = {})
        }
    }
}
