package com.mrfilefixit.feature.scan.ui

import android.app.Activity
import android.content.ClipData
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.domain.ocr.OcrScript
import com.mrfilefixit.core.domain.scan.ScanCapture
import com.mrfilefixit.core.ui.ads.LocalAdSlots
import com.mrfilefixit.core.ui.components.ErrorCard
import com.mrfilefixit.core.ui.components.JobProgressCard
import com.mrfilefixit.core.ui.format.formatBytes
import com.mrfilefixit.core.ui.preview.AllPreviews
import com.mrfilefixit.core.ui.share.findActivity
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.theme.MffTheme
import com.mrfilefixit.feature.scan.R
import com.mrfilefixit.core.ui.R as CoreR

internal class ScanActions(
    val onCaptured: (ScanCapture) -> Unit,
    val onScannerUnavailable: () -> Unit,
    val onPickOcr: (List<String>) -> Unit,
    val onScript: (OcrScript) -> Unit,
    val onRecognize: () -> Unit,
    val onCancel: () -> Unit,
    val onOcrRevealed: () -> Unit,
    val onSave: (OutputFile, String) -> Unit,
    val onShare: (OutputFile) -> Unit,
    val onCopyText: (String) -> Unit,
    val onShareText: (String) -> Unit,
    val onReset: () -> Unit,
)

/** The scanner runs in Google Play services: we get a JPEG per page plus one PDF, all on-device. */
private fun scannerClient(): GmsDocumentScanner = GmsDocumentScanning.getClient(
    GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(MAX_SCAN_PAGES)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .build(),
)

@Composable
internal fun ScanScreen(state: ScanUiState, actions: ScanActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data) ?: return@rememberLauncherForActivityResult
        actions.onCaptured(ScanCapture(scan.pdf?.uri?.toString(), scan.pages.orEmpty().map { it.imageUri.toString() }))
    }
    val ocrPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        actions.onPickOcr(uris.map { it.toString() })
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.SpaceM),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.scan_intro), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                Button(
                    onClick = {
                        val activity = context.findActivity() ?: return@Button
                        scannerClient().getStartScanIntent(activity)
                            .addOnSuccessListener { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
                            .addOnFailureListener { actions.onScannerUnavailable() }
                    },
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                ) { Text(stringResource(R.string.scan_start)) }
                OutlinedButton(
                    onClick = { ocrPicker.launch(arrayOf("image/*", "application/pdf")) },
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                ) { Text(stringResource(R.string.scan_pick_ocr)) }
            }
        }
        state.inputError?.let { item { ErrorCard(it, onRetry = null) } }
        item { ScanSection(state, actions) }
        if (state.canRecognize || state.ocr !is JobState.Idle) item { OcrSection(state, actions) }
    }
}

@Composable
private fun ScanSection(state: ScanUiState, actions: ScanActions) {
    Column(Modifier.widthIn(max = Dimens.CardMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        when (val job = state.scan) {
            is JobState.Running -> {
                Text(stringResource(R.string.scan_importing))
                JobProgressCard(job.progress, actions.onCancel)
            }
            is JobState.Failed -> ErrorCard(job.error, onRetry = null)
            is JobState.Success -> ScanOutputs(job.value.pdf, job.value.pages, actions)
            else -> Unit
        }
    }
}

@Composable
private fun ScanOutputs(pdf: OutputFile?, pages: List<OutputFile>, actions: ScanActions) {
    var pendingSave by remember { mutableStateOf<OutputFile?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val output = pendingSave
        if (uri != null && output != null) actions.onSave(output, uri.toString())
        pendingSave = null
    }
    Text(stringResource(R.string.scan_pages, pages.size), style = MaterialTheme.typography.titleSmall)
    val rows = listOfNotNull(pdf?.let { stringResource(R.string.scan_pdf) to it }) +
        pages.mapIndexed { index, page -> stringResource(R.string.scan_page, index + 1) to page }
    rows.forEach { (label, output) ->
        ListItem(
            headlineContent = { Text(label) },
            supportingContent = { Text(formatBytes(output.sizeBytes)) },
            trailingContent = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                    OutlinedButton(onClick = {
                        pendingSave = output
                        saver.launch(output.displayName)
                    }) { Text(stringResource(CoreR.string.action_save)) }
                    FilledTonalButton(onClick = { actions.onShare(output) }) { Text(stringResource(CoreR.string.action_share)) }
                }
            },
        )
    }
    OutlinedButton(onClick = actions.onReset, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
        Text(stringResource(CoreR.string.action_reset))
    }
}

@Composable
private fun OcrSection(state: ScanUiState, actions: ScanActions) {
    val context = LocalContext.current
    val adSlots = LocalAdSlots.current
    Column(Modifier.widthIn(max = Dimens.CardMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        if (state.ocrFiles.isNotEmpty()) Text(stringResource(R.string.scan_ocr_files, state.ocrFiles.size))
        Text(stringResource(R.string.scan_script), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            OcrScript.entries.forEach { script ->
                FilterChip(
                    selected = script == state.script,
                    onClick = { actions.onScript(script) },
                    label = { Text(scriptLabel(script)) },
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                )
            }
        }
        when (val job = state.ocr) {
            is JobState.Running -> JobProgressCard(job.progress, actions.onCancel)
            is JobState.Success -> if (!state.ocrRevealed) {
                LaunchedEffect(job) {
                    context.findActivity()?.let { adSlots.beforeResult(it) }
                    actions.onOcrRevealed()
                }
                JobProgressCard(1f, onCancel = {})
            } else {
                TextResult(job.value.text, job.value.pageCount, actions)
            }
            else -> {
                if (job is JobState.Failed) ErrorCard(job.error, onRetry = null)
                if (job is JobState.Cancelled) Text(stringResource(CoreR.string.job_cancelled))
                Button(
                    onClick = actions.onRecognize,
                    enabled = state.canRecognize,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouchTarget),
                ) { Text(stringResource(R.string.scan_recognize)) }
            }
        }
    }
}

@Composable
private fun TextResult(text: String, pageCount: Int, actions: ScanActions) {
    Text(stringResource(R.string.scan_text_result, pageCount), style = MaterialTheme.typography.titleSmall)
    Card(Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(text.ifBlank { stringResource(R.string.scan_no_text) }, Modifier.padding(Dimens.SpaceM), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (text.isNotBlank()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            FilledTonalButton(onClick = { actions.onCopyText(text) }, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
                Text(stringResource(R.string.scan_copy))
            }
            OutlinedButton(onClick = { actions.onShareText(text) }, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
                Text(stringResource(R.string.scan_share_text))
            }
        }
    }
}

@Composable
private fun scriptLabel(script: OcrScript): String = stringResource(
    when (script) {
        OcrScript.LATIN -> R.string.scan_script_LATIN
        OcrScript.DEVANAGARI -> R.string.scan_script_DEVANAGARI
    },
)

/** Clip data for OCR text. Marked sensitive so Android 13+ hides it from the clipboard preview. */
internal fun sensitiveClip(text: String): ClipData = ClipData.newPlainText("text", text).apply {
    description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
}

// ClipDescription.EXTRA_IS_SENSITIVE is API 33; the literal key is honoured there and ignored below.
private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
private const val MAX_SCAN_PAGES = 20

@AllPreviews
@Composable
private fun ScanPreview() {
    MffTheme(dynamicColor = false) {
        ScanScreen(
            ScanUiState(ocrFiles = emptyList()),
            ScanActions({}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}),
        )
    }
}
