package com.mrfilefixit.feature.image.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mrfilefixit.core.domain.image.MetadataGroup
import com.mrfilefixit.core.domain.image.MetadataSummary
import com.mrfilefixit.core.ui.components.SectionCard
import com.mrfilefixit.core.ui.components.SectionHeader
import com.mrfilefixit.core.ui.components.SwitchRow
import com.mrfilefixit.feature.image.R

/**
 * "Remove metadata": what the first photo carries, and one switch per kind of detail so the user picks what
 * goes. Everything is switched on to start with; thumbnails and vendor data always go.
 */
@Composable
internal fun MetadataOptionsCard(state: ImageToolUiState, actions: ImageToolActions, modifier: Modifier = Modifier) {
    val summary = state.firstInfo?.metadata ?: MetadataSummary.NONE
    val remove = state.options.removeMetadata
    SectionCard(modifier) {
        SectionHeader(stringResource(R.string.image_strip_choose))
        MetadataGroup.entries.forEach { group ->
            SwitchRow(
                label = stringResource(groupLabel(group)),
                supporting = foundText(group, summary),
                checked = group in remove,
                onChecked = { on ->
                    actions.onOptions { o ->
                        o.copy(removeMetadata = if (on) o.removeMetadata + group else o.removeMetadata - group)
                    }
                },
                modifier = Modifier.testTag("strip_${group.name.lowercase()}"),
            )
        }
        val colors = MaterialTheme.colorScheme
        Text(
            stringResource(if (remove.isEmpty()) R.string.image_strip_pick_one else R.string.image_strip_note),
            style = MaterialTheme.typography.bodySmall,
            color = if (remove.isEmpty()) colors.error else colors.onSurfaceVariant,
        )
    }
}

private fun groupLabel(group: MetadataGroup): Int = when (group) {
    MetadataGroup.LOCATION -> R.string.image_strip_location
    MetadataGroup.DEVICE -> R.string.image_strip_device
    MetadataGroup.DATE_TIME -> R.string.image_strip_date
    MetadataGroup.OTHER -> R.string.image_strip_other
}

/** What the first photo has in this group ("Found: samsung SM-S918B"), or that it has none. */
@Composable
private fun foundText(group: MetadataGroup, summary: MetadataSummary): String = when (group) {
    MetadataGroup.LOCATION ->
        if (summary.hasLocation) stringResource(R.string.image_strip_found_location) else none()
    MetadataGroup.DEVICE -> summary.device?.let { stringResource(R.string.image_strip_found, it) } ?: none()
    MetadataGroup.DATE_TIME -> summary.takenAt?.let { stringResource(R.string.image_strip_found, it) } ?: none()
    MetadataGroup.OTHER -> if (summary.hasOther) stringResource(R.string.image_strip_found_other) else none()
}

@Composable
private fun none(): String = stringResource(R.string.image_strip_not_found)
