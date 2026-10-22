package com.mrfilefixit.feature.pdf.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.domain.pdf.CompressionLevel
import com.mrfilefixit.core.domain.pdf.PageSize
import com.mrfilefixit.core.domain.pdf.Position
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.ads.BannerPlacement
import com.mrfilefixit.core.ui.ads.LocalAdSlots
import com.mrfilefixit.core.ui.components.ErrorCard
import com.mrfilefixit.core.ui.components.JobProgressCard
import com.mrfilefixit.core.ui.format.formatBytes
import com.mrfilefixit.core.ui.preview.AllPreviews
import com.mrfilefixit.core.ui.share.findActivity
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.theme.MffTheme
import com.mrfilefixit.feature.pdf.R

/** Every callback the tool screen needs; keeps the screen previewable without a ViewModel. */
internal class PdfToolActions(
    val onPick: (List<String>) -> Unit,
    val onRemove: (Int) -> Unit,
    val onMove: (Int, Int) -> Unit,
    val onOptions: ((PdfOptions) -> PdfOptions) -> Unit,
    val onRun: (CharArray?) -> Unit,
    val onCancel: () -> Unit,
    val onRevealed: () -> Unit,
    val onSave: (Int, String) -> Unit,
    val onShareAll: () -> Unit,
    val onReset: () -> Unit,
)

@Composable
internal fun PdfToolScreen(state: PdfToolUiState, actions: PdfToolActions, modifier: Modifier = Modifier) {
    val tool = state.tool
    val pickPdfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        actions.onPick(uris.map { it.toString() })
    }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { actions.onPick(listOf(it.toString())) }
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        actions.onPick(uris.map { it.toString() })
    }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(Dimens.SpaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    toolLabel(tool),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics {
                        heading()
                    },
                )
            }
            item {
                Button(
                    onClick = {
                        when {
                            tool.acceptsImages -> pickImages.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                            tool.multipleInputs -> pickPdfs.launch(arrayOf(PDF_MIME))
                            else -> pickPdf.launch(arrayOf(PDF_MIME))
                        }
                    },
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                ) {
                    Text(
                        stringResource(
                            if (tool.multipleInputs) R.string.pdf_add_files else CoreR.string.action_choose_file,
                        ),
                    )
                }
            }
            state.inputError?.let { item { ErrorCard(it, onRetry = null) } }
            itemsIndexed(state.files, key = { index, file -> "$index-${file.uri}" }) { index, file ->
                ListItem(
                    headlineContent = { Text(file.displayName) },
                    supportingContent = {
                        val info = state.info
                        val details = buildList {
                            file.sizeBytes?.let { add(formatBytes(it)) }
                            if (!tool.multipleInputs && info != null) {
                                add(stringResource(R.string.pdf_pages_count, info.pageCount))
                                if (info.encrypted) add(stringResource(R.string.pdf_encrypted))
                            }
                        }
                        Text(details.joinToString(" · "))
                    },
                    trailingContent = {
                        Row {
                            if (tool.multipleInputs) {
                                IconButton(onClick = { actions.onMove(index, index - 1) }, enabled = index > 0) {
                                    Icon(Icons.Filled.ArrowUpward, stringResource(CoreR.string.action_move_up))
                                }
                                IconButton(
                                    onClick = { actions.onMove(index, index + 1) },
                                    enabled =
                                    index < state.files.lastIndex,
                                ) {
                                    Icon(Icons.Filled.ArrowDownward, stringResource(CoreR.string.action_move_down))
                                }
                            }
                            IconButton(onClick = { actions.onRemove(index) }) {
                                Icon(Icons.Filled.Close, stringResource(CoreR.string.action_remove))
                            }
                        }
                    },
                    modifier = Modifier.widthIn(max = Dimens.CardMaxWidth),
                )
            }
            item {
                Options(state, actions, password, { password = it }, confirm, { confirm = it })
            }
            item {
                JobSection(state, actions, onRun = {
                    val chars = if (tool == PdfTool.PROTECT || tool == PdfTool.UNLOCK) password.toCharArray() else null
                    actions.onRun(chars)
                    // Drop the typed password from the UI as soon as it is handed to the job.
                    password = ""
                    confirm = ""
                }, passwordsValid = tool != PdfTool.PROTECT || (password.isNotEmpty() && password == confirm))
            }
        }
        // The file list is one of the three screens allowed to show a banner.
        if (state.files.isNotEmpty()) LocalAdSlots.current.Banner(BannerPlacement.FILE_LIST, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Options(
    state: PdfToolUiState,
    actions: PdfToolActions,
    password: String,
    onPassword: (String) -> Unit,
    confirm: String,
    onConfirm: (String) -> Unit,
) {
    val o = state.options
    Column(
        Modifier.widthIn(max = Dimens.CardMaxWidth).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        when (state.tool) {
            PdfTool.SPLIT, PdfTool.EXTRACT, PdfTool.REORDER, PdfTool.DELETE, PdfTool.ROTATE, PdfTool.TO_IMAGES -> {
                val hint = when (state.tool) {
                    PdfTool.SPLIT -> R.string.pdf_split_hint
                    PdfTool.REORDER -> R.string.pdf_order_hint
                    else -> R.string.pdf_pages_hint
                }
                OutlinedTextField(
                    value = o.pages,
                    onValueChange = { v -> actions.onOptions { it.copy(pages = v) } },
                    label = { Text(stringResource(R.string.pdf_pages_label)) },
                    supportingText = { Text(stringResource(hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.tool == PdfTool.ROTATE) {
                    Text(stringResource(R.string.pdf_rotate_by), style = MaterialTheme.typography.titleSmall)
                    ChipRow(listOf(90, 180, 270), o.degrees, {
                        "$it°"
                    }) { d -> actions.onOptions { it.copy(degrees = d) } }
                }
                if (state.tool == PdfTool.TO_IMAGES) {
                    Text(stringResource(R.string.pdf_dpi, o.dpi))
                    Slider(
                        value = o.dpi.toFloat(),
                        onValueChange = { v -> actions.onOptions { it.copy(dpi = v.toInt()) } },
                        valueRange = 72f..300f,
                    )
                }
            }
            PdfTool.COMPRESS -> {
                Text(stringResource(R.string.pdf_compress_level), style = MaterialTheme.typography.titleSmall)
                ChipRow(CompressionLevel.entries, o.level, {
                    compressionLabel(it)
                }) { l -> actions.onOptions { it.copy(level = l) } }
                Text(stringResource(R.string.pdf_compress_note), style = MaterialTheme.typography.bodySmall)
            }
            PdfTool.IMAGES_TO_PDF -> {
                Text(stringResource(R.string.pdf_page_size), style = MaterialTheme.typography.titleSmall)
                ChipRow(PageSize.entries, o.pageSize, {
                    pageSizeLabel(it)
                }) { p -> actions.onOptions { it.copy(pageSize = p) } }
            }
            PdfTool.WATERMARK -> {
                OutlinedTextField(
                    value = o.watermarkText,
                    onValueChange = { v -> actions.onOptions { it.copy(watermarkText = v.take(MAX_WATERMARK)) } },
                    label = { Text(stringResource(R.string.pdf_watermark_text)) },
                    supportingText = { Text(stringResource(R.string.pdf_watermark_latin_only)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.pdf_opacity, (o.opacity * PERCENT).toInt()))
                Slider(
                    value = o.opacity,
                    onValueChange = { v ->
                        actions.onOptions { it.copy(opacity = v) }
                    },
                    valueRange =
                    0.1f..1f,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                ) {
                    Text(stringResource(R.string.pdf_watermark_diagonal), Modifier.weight(1f))
                    Switch(checked = o.diagonal, onCheckedChange = { v -> actions.onOptions { it.copy(diagonal = v) } })
                }
            }
            PdfTool.PAGE_NUMBERS -> {
                Text(stringResource(R.string.pdf_position), style = MaterialTheme.typography.titleSmall)
                ChipRow(
                    listOf(Position.BOTTOM_CENTER, Position.BOTTOM_RIGHT, Position.TOP_RIGHT),
                    o.position,
                    { positionLabel(it) },
                ) { p -> actions.onOptions { it.copy(position = p) } }
                Text(stringResource(R.string.pdf_number_style), style = MaterialTheme.typography.titleSmall)
                ChipRow(NumberStyle.entries, o.numberStyle, {
                    it.pattern.replace("{n}", "1").replace("{total}", "9")
                }) { n ->
                    actions.onOptions { it.copy(numberStyle = n) }
                }
            }
            PdfTool.PROTECT, PdfTool.UNLOCK -> {
                PasswordField(password, onPassword, stringResource(R.string.pdf_password))
                if (state.tool == PdfTool.PROTECT) {
                    PasswordField(confirm, onConfirm, stringResource(R.string.pdf_password_confirm))
                    if (confirm.isNotEmpty() && confirm != password) {
                        Text(stringResource(R.string.pdf_password_mismatch), color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(stringResource(R.string.pdf_password_note), style = MaterialTheme.typography.bodySmall)
            }
            PdfTool.MERGE -> Unit
        }
    }
}

@Composable
private fun PasswordField(value: String, onValue: (String) -> Unit, label: String) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        // No autofill/suggestions: the keyboard must not learn the password.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    stringResource(if (visible) R.string.cd_hide_password else R.string.cd_show_password),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun <T> ChipRow(values: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        values.forEach { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label(value)) },
                modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
            )
        }
    }
}

@Composable
private fun JobSection(state: PdfToolUiState, actions: PdfToolActions, onRun: () -> Unit, passwordsValid: Boolean) {
    val context = LocalContext.current
    val adSlots = LocalAdSlots.current
    Column(
        Modifier.widthIn(max = Dimens.CardMaxWidth).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        when (val job = state.job) {
            is JobState.Running -> JobProgressCard(job.progress, actions.onCancel)
            is JobState.Success -> if (!state.resultRevealed) {
                LaunchedEffect(job) {
                    context.findActivity()?.let { adSlots.beforeResult(it) }
                    actions.onRevealed()
                }
                JobProgressCard(1f, onCancel = {})
            } else {
                Results(state, actions)
            }
            else -> {
                if (job is JobState.Failed) ErrorCard(job.error, onRetry = null)
                if (job is JobState.Cancelled) Text(stringResource(CoreR.string.job_cancelled))
                Button(
                    onClick = onRun,
                    enabled = state.canRun && passwordsValid,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouchTarget),
                ) { Text(stringResource(R.string.pdf_run)) }
            }
        }
    }
}

@Composable
private fun Results(state: PdfToolUiState, actions: PdfToolActions) {
    val outputs = state.outputs
    var pendingSave by remember { mutableStateOf<Int?>(null) }
    val saver =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(outputs.firstOrNull()?.mimeType ?: PDF_MIME),
        ) { uri ->
            val index = pendingSave
            if (uri != null && index != null) actions.onSave(index, uri.toString())
            pendingSave = null
        }
    Text(stringResource(R.string.pdf_result_files, outputs.size), style = MaterialTheme.typography.titleSmall)
    outputs.forEachIndexed { index, file ->
        Card(Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(file.displayName) },
                supportingContent = { Text(formatBytes(file.sizeBytes)) },
                trailingContent = {
                    OutlinedButton(onClick = {
                        pendingSave = index
                        saver.launch(file.displayName)
                    }) { Text(stringResource(CoreR.string.action_save)) }
                },
            )
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        FilledTonalButton(onClick = actions.onShareAll, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
            Text(stringResource(if (outputs.size > 1) R.string.pdf_share_all else CoreR.string.action_share))
        }
        OutlinedButton(onClick = actions.onReset, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
            Text(stringResource(CoreR.string.action_reset))
        }
    }
}

@Composable
internal fun toolLabel(tool: PdfTool): String = stringResource(
    when (tool) {
        PdfTool.MERGE -> R.string.pdf_tool_MERGE
        PdfTool.SPLIT -> R.string.pdf_tool_SPLIT
        PdfTool.EXTRACT -> R.string.pdf_tool_EXTRACT
        PdfTool.REORDER -> R.string.pdf_tool_REORDER
        PdfTool.ROTATE -> R.string.pdf_tool_ROTATE
        PdfTool.DELETE -> R.string.pdf_tool_DELETE
        PdfTool.COMPRESS -> R.string.pdf_tool_COMPRESS
        PdfTool.IMAGES_TO_PDF -> R.string.pdf_tool_IMAGES_TO_PDF
        PdfTool.TO_IMAGES -> R.string.pdf_tool_TO_IMAGES
        PdfTool.WATERMARK -> R.string.pdf_tool_WATERMARK
        PdfTool.PAGE_NUMBERS -> R.string.pdf_tool_PAGE_NUMBERS
        PdfTool.PROTECT -> R.string.pdf_tool_PROTECT
        PdfTool.UNLOCK -> R.string.pdf_tool_UNLOCK
    },
)

@Composable
private fun compressionLabel(level: CompressionLevel): String = stringResource(
    when (level) {
        CompressionLevel.LOW -> R.string.pdf_compress_LOW
        CompressionLevel.MEDIUM -> R.string.pdf_compress_MEDIUM
        CompressionLevel.HIGH -> R.string.pdf_compress_HIGH
    },
)

@Composable
private fun pageSizeLabel(size: PageSize): String = stringResource(
    when (size) {
        PageSize.A4 -> R.string.pdf_page_size_A4
        PageSize.LETTER -> R.string.pdf_page_size_LETTER
        PageSize.LEGAL -> R.string.pdf_page_size_LEGAL
        PageSize.FIT_IMAGE -> R.string.pdf_page_size_FIT_IMAGE
    },
)

@Composable
private fun positionLabel(position: Position): String = stringResource(
    when (position) {
        Position.BOTTOM_RIGHT -> R.string.pdf_position_BOTTOM_RIGHT
        Position.TOP_RIGHT -> R.string.pdf_position_TOP_RIGHT
        else -> R.string.pdf_position_BOTTOM_CENTER
    },
)

private const val PDF_MIME = "application/pdf"
private const val PERCENT = 100
private const val MAX_WATERMARK = 60

@AllPreviews
@Composable
private fun PdfToolPreview() {
    MffTheme(dynamicColor = false) {
        PdfToolScreen(
            PdfToolUiState(PdfTool.WATERMARK, options = PdfOptions(watermarkText = "COPY")),
            PdfToolActions({}, {}, { _, _ -> }, {}, {}, {}, {}, { _, _ -> }, {}, {}),
        )
    }
}
