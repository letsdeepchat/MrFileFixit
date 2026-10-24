package com.mrfilefixit.feature.scan.ui

import android.app.Activity
import android.content.ClipData
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.domain.ocr.OcrScript
import com.mrfilefixit.core.domain.scan.ScanCapture
import com.mrfilefixit.core.domain.scan.ScanFilter
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.components.ErrorCard
import com.mrfilefixit.core.ui.components.JobProgressCard
import com.mrfilefixit.core.ui.components.PrimaryActionButton
import com.mrfilefixit.core.ui.components.ToolScaffold
import com.mrfilefixit.core.ui.format.formatBytes
import com.mrfilefixit.core.ui.preview.AllPreviews
import com.mrfilefixit.core.ui.share.findActivity
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.theme.MffTheme
import com.mrfilefixit.core.ui.thumbnail.Thumbnail
import com.mrfilefixit.feature.scan.R

internal class ScanActions(
    val onCaptured: (ScanCapture) -> Unit,
    val onScannerUnavailable: () -> Unit,
    val onPickOcr: (List<String>) -> Unit,
    val onScript: (OcrScript) -> Unit,
    val onFilter: (ScanFilter) -> Unit,
    val onRecognize: () -> Unit,
    val onCancel: () -> Unit,
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
internal fun ScanScreen(
    state: ScanUiState,
    actions: ScanActions,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val scanLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val scan = result.data
                ?.takeIf { result.resultCode == Activity.RESULT_OK }
                ?.let(GmsDocumentScanningResult::fromActivityResultIntent)
            if (scan != null) {
                actions.onCaptured(
                    ScanCapture(
                        scan.pdf?.uri?.toString(),
                        scan.pages.orEmpty().map {
                            it.imageUri.toString()
                        },
                    ),
                )
            }
        }
    val ocrPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        actions.onPickOcr(uris.map { it.toString() })
    }
    val hasWork = state.scan !is JobState.Idle || state.ocrFiles.isNotEmpty() || state.ocr !is JobState.Idle
    ToolScaffold(
        title = stringResource(R.string.scan_title),
        modifier = modifier,
        actions = {
            if (hasWork) {
                TextButton(onClick = actions.onReset) { Text(stringResource(CoreR.string.action_clear_all)) }
            }
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        // Hero cards on top, the privacy note pinned to the bottom when there is room, scrolling otherwise.
        BoxWithConstraints(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.contentWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
                ) {
                    Text(
                        stringResource(R.string.scan_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HeroCard(
                        icon = Icons.Filled.DocumentScanner,
                        title = stringResource(R.string.scan_camera_title),
                        subtitle = stringResource(R.string.scan_camera_subtitle),
                        onClick = {
                            val activity = context.findActivity() ?: return@HeroCard
                            scannerClient().getStartScanIntent(activity)
                                .addOnSuccessListener { sender ->
                                    scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                }
                                .addOnFailureListener { actions.onScannerUnavailable() }
                        },
                        modifier = Modifier.testTag("scan_camera"),
                    )
                    HeroCard(
                        icon = Icons.AutoMirrored.Filled.TextSnippet,
                        title = stringResource(R.string.scan_storage_title),
                        subtitle = stringResource(R.string.scan_storage_subtitle),
                        onClick = { ocrPicker.launch(arrayOf("image/*", "application/pdf")) },
                        modifier = Modifier.testTag("scan_storage"),
                    )
                    state.inputError?.let { ErrorCard(it, onRetry = null) }
                    ScanSection(state, actions)
                    if (state.canRecognize || state.ocr !is JobState.Idle) OcrSection(state, actions)
                }
                PrivacyCard(Modifier.contentWidth().padding(top = Dimens.SpaceL))
            }
        }
    }
}

/** Large tappable entry point: gradient icon, title, one-line explanation and a chevron. */
@Composable
private fun HeroCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(Dimens.CardCorner),
        border = BorderStroke(1.dp, colors.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(Dimens.SectionPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(Dimens.IconBadge)
                    .clip(RoundedCornerShape(Dimens.ButtonCorner))
                    .background(Brush.linearGradient(listOf(colors.primary, colors.tertiary))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colors.onPrimary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrivacyCard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(RoundedCornerShape(Dimens.CardCorner))
            .background(colors.secondaryContainer)
            .padding(Dimens.ScreenPadding)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = colors.onSecondaryContainer)
        Text(
            stringResource(R.string.scan_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSecondaryContainer,
        )
    }
}

private fun Modifier.contentWidth(): Modifier = widthIn(max = Dimens.CardMaxWidth).fillMaxWidth()

@Composable
private fun ScanSection(state: ScanUiState, actions: ScanActions) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        when (val job = state.scan) {
            is JobState.Running -> {
                Text(stringResource(R.string.scan_importing))
                JobProgressCard(job.progress, actions.onCancel)
            }
            is JobState.Failed -> ErrorCard(job.error, onRetry = null)
            is JobState.Success -> {
                if (job.value.pages.isNotEmpty()) FilterPicker(state.filter, actions.onFilter)
                when (val enhanced = state.enhanced) {
                    is JobState.Running -> {
                        Text(stringResource(R.string.scan_enhancing))
                        JobProgressCard(enhanced.progress, actions.onCancel)
                    }
                    is JobState.Failed -> ErrorCard(enhanced.error, onRetry = null)
                    else -> Unit
                }
                state.document?.let { ScanOutputs(it.pdf, it.pages, actions) }
            }
            else -> Unit
        }
    }
}

/**
 * Post-scan look, applied to every page at full resolution: Document clean (default) removes shadows and
 * whitens the paper, Magic colour keeps colour but evens out lighting, Grayscale keeps soft grey tones.
 */
@Composable
private fun FilterPicker(selected: ScanFilter, onSelect: (ScanFilter) -> Unit) {
    Text(stringResource(R.string.scan_filter_title), style = MaterialTheme.typography.titleSmall)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        ScanFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filterLabel(filter)) },
                modifier = Modifier
                    .heightIn(min = Dimens.MinTouchTarget)
                    .testTag("scan_filter_${filter.name.lowercase()}"),
            )
        }
    }
}

@Composable
private fun filterLabel(filter: ScanFilter): String = stringResource(
    when (filter) {
        ScanFilter.DOCUMENT_CLEAN -> R.string.scan_filter_DOCUMENT_CLEAN
        ScanFilter.MAGIC_COLOR -> R.string.scan_filter_MAGIC_COLOR
        ScanFilter.GRAYSCALE -> R.string.scan_filter_GRAYSCALE
        ScanFilter.ORIGINAL -> R.string.scan_filter_ORIGINAL
    },
)

@Composable
private fun ScanOutputs(pdf: OutputFile?, pages: List<OutputFile>, actions: ScanActions) {
    var pendingSave by remember { mutableStateOf<OutputFile?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val output = pendingSave
        if (uri != null && output != null) actions.onSave(output, uri.toString())
        pendingSave = null
    }
    Text(
        pluralStringResource(R.plurals.scan_pages, pages.size, pages.size),
        style = MaterialTheme.typography.titleSmall,
    )
    // Page previews, so the effect of the filter is visible before sharing.
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        itemsIndexed(pages, key = { _, page -> page.path }) { index, page ->
            Thumbnail(
                "file://${page.path}",
                stringResource(R.string.scan_page, index + 1),
                Dimens.ThumbnailSize,
                contentScale = ContentScale.Fit,
            )
        }
    }
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
                    FilledTonalButton(onClick = {
                        actions.onShare(output)
                    }) { Text(stringResource(CoreR.string.action_share)) }
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        if (state.ocrFiles.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.scan_ocr_files, state.ocrFiles.size, state.ocrFiles.size),
            )
        }
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
            is JobState.Success -> TextResult(job.value.text, job.value.pageCount, actions)
            else -> {
                if (job is JobState.Failed) ErrorCard(job.error, onRetry = null)
                if (job is JobState.Cancelled) Text(stringResource(CoreR.string.job_cancelled))
                PrimaryActionButton(
                    stringResource(R.string.scan_recognize),
                    onClick = actions.onRecognize,
                    enabled = state.canRecognize,
                )
            }
        }
    }
}

@Composable
private fun TextResult(text: String, pageCount: Int, actions: ScanActions) {
    Text(
        pluralStringResource(R.plurals.scan_text_result, pageCount, pageCount),
        style = MaterialTheme.typography.titleSmall,
    )
    Card(Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                text.ifBlank {
                    stringResource(R.string.scan_no_text)
                },
                Modifier.padding(Dimens.SpaceM),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (text.isNotBlank()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            FilledTonalButton(onClick = {
                actions.onCopyText(text)
            }, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
                Text(stringResource(R.string.scan_copy))
            }
            OutlinedButton(onClick = {
                actions.onShareText(text)
            }, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
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
            ScanUiState(),
            ScanActions({}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}),
        )
    }
}
