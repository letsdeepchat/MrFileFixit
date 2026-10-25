package com.mrfilefixit.feature.ocr.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.domain.ocr.OcrExportFormat
import com.mrfilefixit.core.domain.ocr.OcrResult
import com.mrfilefixit.core.domain.ocr.OcrScript
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.components.ErrorCard
import com.mrfilefixit.core.ui.components.JobProgressCard
import com.mrfilefixit.core.ui.components.PrimaryActionButton
import com.mrfilefixit.core.ui.components.SectionCard
import com.mrfilefixit.core.ui.components.SectionHeader
import com.mrfilefixit.core.ui.components.ToolScaffold
import com.mrfilefixit.core.ui.components.UploadDropZone
import com.mrfilefixit.core.ui.format.formatBytes
import com.mrfilefixit.core.ui.preview.AllPreviews
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.theme.MffTheme
import com.mrfilefixit.feature.ocr.R

internal class OcrActions(
    val onBack: (() -> Unit)?,
    val onPick: (List<String>) -> Unit,
    val onScript: (OcrScript) -> Unit,
    val onRecognize: () -> Unit,
    val onCancel: () -> Unit,
    val onCopy: (String) -> Unit,
    val onShareText: (String) -> Unit,
    val onExport: (OcrExportFormat) -> Unit,
    val onSave: (String) -> Unit,
    val onShare: () -> Unit,
    val onReset: () -> Unit,
)

/** Pick files, choose a language (or auto-detect), recognize, then copy the text or save it in four formats. */
@Composable
internal fun OcrScreen(
    state: OcrUiState,
    actions: OcrActions,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        actions.onPick(uris.map { it.toString() })
    }
    val pick = { picker.launch(PICKER_TYPES) }
    ToolScaffold(
        title = stringResource(R.string.ocr_title),
        modifier = modifier,
        onBack = actions.onBack,
        actions = {
            if (state.files.isNotEmpty()) {
                TextButton(onClick = actions.onReset) { Text(stringResource(CoreR.string.action_clear_all)) }
            }
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.ScreenPadding)
                .testTag("ocr_content"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        ) {
            val width = Modifier.contentWidth()
            if (state.files.isEmpty()) {
                UploadDropZone(
                    title = stringResource(R.string.ocr_pick_title),
                    subtitle = stringResource(R.string.ocr_pick_hint),
                    onClick = pick,
                    icon = Icons.Filled.DocumentScanner,
                    modifier = width.testTag("ocr_pick"),
                )
            } else {
                FilesCard(state, onChange = pick, modifier = width)
            }
            state.inputError?.let { ErrorCard(it, onRetry = null, modifier = width) }
            LanguageCard(state, actions, width)
            RecognizeStep(state, actions, width)
            state.result?.let { result ->
                ResultCard(result, actions, width)
                if (result.text.isNotBlank()) ExportCard(state, actions, width)
            }
        }
    }
}

@Composable
private fun FilesCard(state: OcrUiState, onChange: () -> Unit, modifier: Modifier) {
    SectionCard(modifier) {
        SectionHeader(pluralStringResource(R.plurals.ocr_files, state.files.size, state.files.size))
        state.files.take(MAX_LISTED).forEach { file ->
            Text(file.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        if (state.files.size > MAX_LISTED) Text("...", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onChange, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
            Text(stringResource(R.string.ocr_change_files))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageCard(state: OcrUiState, actions: OcrActions, modifier: Modifier) {
    SectionCard(modifier) {
        SectionHeader(stringResource(R.string.ocr_language))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            OcrScript.entries.forEach { script ->
                FilterChip(
                    selected = script == state.script,
                    onClick = { actions.onScript(script) },
                    enabled = state.ocr !is JobState.Running,
                    label = { Text(OcrText.script(script)) },
                    modifier = Modifier
                        .heightIn(min = Dimens.MinTouchTarget)
                        .testTag("ocr_script_${script.name.lowercase()}"),
                )
            }
        }
        Text(
            OcrText.scriptHint(state.script),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.ocr_models_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecognizeStep(state: OcrUiState, actions: OcrActions, modifier: Modifier) {
    when (val job = state.ocr) {
        is JobState.Running -> JobProgressCard(job.progress, actions.onCancel, modifier)
        is JobState.Success -> Unit
        else -> Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            if (job is JobState.Failed) ErrorCard(job.error, onRetry = actions.onRecognize)
            if (job is JobState.Cancelled) Text(stringResource(CoreR.string.job_cancelled))
            PrimaryActionButton(
                stringResource(R.string.ocr_recognize),
                onClick = actions.onRecognize,
                enabled = state.canRecognize,
                modifier = Modifier.testTag("ocr_recognize"),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(result: OcrResult, actions: OcrActions, modifier: Modifier) {
    SectionCard(modifier.testTag("ocr_result")) {
        SectionHeader(pluralStringResource(R.plurals.ocr_result_title, result.pageCount, result.pageCount))
        Text(
            stringResource(R.string.ocr_detected, OcrText.script(result.script)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(Modifier.fillMaxWidth()) {
            SelectionContainer {
                Text(
                    result.text.ifBlank { stringResource(R.string.ocr_no_text) },
                    Modifier.padding(Dimens.SpaceM),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (result.text.isNotBlank()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                FilledTonalButton(
                    onClick = { actions.onCopy(result.text) },
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                ) { Text(stringResource(R.string.ocr_copy)) }
                OutlinedButton(
                    onClick = { actions.onShareText(result.text) },
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                ) { Text(stringResource(R.string.ocr_share_text)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportCard(state: OcrUiState, actions: OcrActions, modifier: Modifier) {
    SectionCard(modifier) {
        SectionHeader(stringResource(R.string.ocr_export_title))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            OcrExportFormat.entries.forEach { format ->
                FilterChip(
                    selected = format == state.exportFormat,
                    onClick = { actions.onExport(format) },
                    enabled = state.export !is JobState.Running,
                    label = { Text(OcrText.format(format)) },
                    modifier = Modifier
                        .heightIn(min = Dimens.MinTouchTarget)
                        .testTag("ocr_export_${format.name.lowercase()}"),
                )
            }
        }
        Text(
            stringResource(R.string.ocr_export_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (val job = state.export) {
            is JobState.Running -> JobProgressCard(job.progress, actions.onCancel)
            is JobState.Failed -> ErrorCard(job.error, onRetry = null)
            is JobState.Cancelled -> Text(stringResource(CoreR.string.job_cancelled))
            is JobState.Success -> ExportedFile(job.value, actions)
            JobState.Idle -> Unit
        }
    }
}

@Composable
private fun ExportedFile(output: OutputFile, actions: OcrActions) {
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(output.mimeType)) { uri ->
        if (uri != null) actions.onSave(uri.toString())
    }
    Column(Modifier.testTag("ocr_exported"), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Text(
            stringResource(R.string.ocr_export_ready, output.displayName, formatBytes(output.sizeBytes)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            Button(
                onClick = { saver.launch(output.displayName) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.MinTouchTarget),
            ) { Text(stringResource(CoreR.string.action_save)) }
            OutlinedButton(
                onClick = actions.onShare,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.MinTouchTarget),
            ) { Text(stringResource(R.string.ocr_share)) }
        }
    }
}

private fun Modifier.contentWidth(): Modifier = widthIn(max = Dimens.CardMaxWidth).fillMaxWidth()

private const val MAX_LISTED = 5

/** PDF and the image types the decoder reads, plus any image, since file managers often label images loosely. */
private val PICKER_TYPES = arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp", "image/*")

@AllPreviews
@Composable
private fun OcrScreenPreview() {
    MffTheme(dynamicColor = false) {
        OcrScreen(
            OcrUiState(),
            OcrActions(null, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}
