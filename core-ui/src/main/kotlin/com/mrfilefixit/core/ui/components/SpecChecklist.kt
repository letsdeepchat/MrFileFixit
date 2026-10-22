package com.mrfilefixit.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.mrfilefixit.core.domain.exam.IntBounds
import com.mrfilefixit.core.domain.exam.SpecCheck
import com.mrfilefixit.core.domain.exam.SpecReport
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.ui.R
import com.mrfilefixit.core.ui.format.formatBytes
import com.mrfilefixit.core.ui.format.formatNumber
import com.mrfilefixit.core.ui.preview.FontScalePreviews
import com.mrfilefixit.core.ui.preview.ThemePreviews
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.theme.LocalStatusColors
import com.mrfilefixit.core.ui.theme.MffTheme

/**
 * Live pass/fail checklist. Each row is one TalkBack stop that reads the whole verdict,
 * and pass/fail is shown with an icon and text, never colour alone.
 */
@Composable
fun SpecChecklist(report: SpecReport, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        report.checks.forEach { check -> SpecCheckRow(check) }
    }
}

@Composable
private fun SpecCheckRow(check: SpecCheck) {
    val label = when (check) {
        is SpecCheck.Format -> stringResource(R.string.check_format)
        is SpecCheck.FileSize -> stringResource(R.string.check_file_size)
        is SpecCheck.Width -> stringResource(R.string.check_width)
        is SpecCheck.Height -> stringResource(R.string.check_height)
    }
    val actual = when (check) {
        is SpecCheck.Format -> check.actual?.displayName() ?: stringResource(R.string.format_unknown)
        is SpecCheck.FileSize -> formatBytes(check.actualBytes)
        is SpecCheck.Width -> stringResource(R.string.value_px, formatNumber(check.actual))
        is SpecCheck.Height -> stringResource(R.string.value_px, formatNumber(check.actual))
    }
    val required = when (check) {
        is SpecCheck.Format -> check.allowed.sorted().joinToString(" / ") { it.displayName() }
        is SpecCheck.FileSize -> stringResource(R.string.range_kb, formatKb(check.minBytes), formatKb(check.maxBytes))
        is SpecCheck.Width -> boundsText(check.bounds)
        is SpecCheck.Height -> boundsText(check.bounds)
    }
    val verdict = stringResource(if (check.passed) R.string.check_passed else R.string.check_failed)
    val description = stringResource(R.string.check_row_description, label, verdict, actual, required)
    val colors = LocalStatusColors.current
    Row(
        modifier = Modifier
            .heightIn(min = Dimens.MinTouchTarget)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        Icon(
            imageVector = if (check.passed) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = if (check.passed) colors.pass else colors.fail,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("$label · $verdict", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "$actual  →  $required",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun formatKb(bytes: Long): String = formatNumber(bytes / BYTES_PER_KB)

private const val BYTES_PER_KB = 1024

@Composable
fun boundsText(bounds: IntBounds): String {
    val min = bounds.min
    val max = bounds.max
    return when {
        min != null && max != null && min == max -> stringResource(R.string.exact_px, formatNumber(min))
        min != null && max != null -> stringResource(R.string.range_px, formatNumber(min), formatNumber(max))
        min != null -> stringResource(R.string.at_least_px, formatNumber(min))
        max != null -> stringResource(R.string.at_most_px, formatNumber(max))
        else -> "—"
    }
}

/** Format names are not translated: portals show them in Latin script too. */
fun ImageFormat.displayName(): String = when (this) {
    ImageFormat.JPEG -> "JPG"
    ImageFormat.PNG -> "PNG"
    ImageFormat.WEBP -> "WebP"
    ImageFormat.HEIC -> "HEIC"
}

@ThemePreviews
@FontScalePreviews
@Composable
private fun SpecChecklistPreview() {
    MffTheme(dynamicColor = false) {
        SpecChecklist(
            SpecReport(
                listOf(
                    SpecCheck.Format(ImageFormat.JPEG, setOf(ImageFormat.JPEG)),
                    SpecCheck.FileSize(61_440, 20_480, 51_200),
                    SpecCheck.Width(200, IntBounds.exactly(200)),
                    SpecCheck.Height(240, IntBounds.exactly(230)),
                ),
            ),
            modifier = Modifier.padding(Dimens.SpaceM),
        )
    }
}
