package com.mrfilefixit.feature.pdf.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.domain.pdf.CompressionLevel
import com.mrfilefixit.core.domain.pdf.PageSize
import com.mrfilefixit.core.domain.pdf.PdfInfo
import com.mrfilefixit.core.domain.pdf.Position
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.security.InputLimits
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.ads.BannerPlacement
import com.mrfilefixit.core.ui.ads.LocalAdSlots
import com.mrfilefixit.core.ui.components.CenteredEmptyState
import com.mrfilefixit.core.ui.components.ChoiceGrid
import com.mrfilefixit.core.ui.components.ErrorCard
import com.mrfilefixit.core.ui.components.JobProgressCard
import com.mrfilefixit.core.ui.components.ListReorderState
import com.mrfilefixit.core.ui.components.PrimaryActionButton
import com.mrfilefixit.core.ui.components.SectionHeader
import com.mrfilefixit.core.ui.components.StickyActionBar
import com.mrfilefixit.core.ui.components.SwitchRow
import com.mrfilefixit.core.ui.components.ToolScaffold
import com.mrfilefixit.core.ui.components.UploadDropZone
import com.mrfilefixit.core.ui.components.rememberListReorderState
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

/**
 * One screen for every PDF tool. Before a file is picked the upload card sits centred; after, the
 * files and options scroll and the primary action is pinned to the bottom bar.
 */
@Composable
internal fun PdfToolScreen(
    state: PdfToolUiState,
    actions: PdfToolActions,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    snackbarHost: @Composable () -> Unit = {},
) {
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
    val pick = {
        when {
            tool.acceptsImages -> pickImages.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
            tool.multipleInputs -> pickPdfs.launch(arrayOf(PDF_MIME))
            else -> pickPdf.launch(arrayOf(PDF_MIME))
        }
    }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val passwordsValid = tool != PdfTool.PROTECT || (password.isNotEmpty() && password == confirm)
    val run = {
        val chars = if (tool == PdfTool.PROTECT || tool == PdfTool.UNLOCK) password.toCharArray() else null
        actions.onRun(chars)
        // Drop the typed password from the UI as soon as it is handed to the job.
        password = ""
        confirm = ""
    }
    val idle = state.job !is JobState.Running && state.job !is JobState.Success

    ToolScaffold(
        title = toolLabel(tool),
        modifier = modifier,
        onBack = onBack,
        actions = {
            if (state.files.isNotEmpty() && state.job !is JobState.Running) {
                TextButton(onClick = actions.onReset) { Text(stringResource(CoreR.string.action_clear_all)) }
            }
        },
        bottomBar = { BottomActions(state, actions, enabled = passwordsValid, onRun = run) },
        snackbarHost = snackbarHost,
    ) { padding ->
        if (state.files.isEmpty() && idle) {
            CenteredEmptyState(Modifier.padding(padding)) {
                UploadDropZone(
                    title = stringResource(uploadTitle(tool)),
                    subtitle = stringResource(uploadSubtitle(tool)) + "\n" +
                        stringResource(CoreR.string.upload_limit_hint, formatBytes(InputLimits.DEFAULT.maxFileBytes)),
                    onClick = pick,
                    icon = when {
                        tool.acceptsImages -> Icons.Filled.AddPhotoAlternate
                        tool.multipleInputs -> Icons.Filled.FileCopy
                        else -> Icons.Filled.PictureAsPdf
                    },
                )
                state.inputError?.let { ErrorCard(it, onRetry = null, Modifier.widthIn(max = Dimens.CardMaxWidth)) }
            }
        } else if (tool == PdfTool.IMAGES_TO_PDF) {
            ImagesToPdfContent(state, actions, pick, Modifier.padding(padding))
        } else {
            DocumentContent(
                state = state,
                actions = actions,
                onAddMore = pick,
                password = PasswordState(password, { password = it }, confirm, { confirm = it }),
                modifier = Modifier.padding(padding),
            )
        }
    }
}

/** Typed passwords live only in the UI until "Run"; this bundles them for [Options]. */
internal class PasswordState(
    val password: String,
    val onPassword: (String) -> Unit,
    val confirm: String,
    val onConfirm: (String) -> Unit,
)

@Composable
private fun DocumentContent(
    state: PdfToolUiState,
    actions: PdfToolActions,
    onAddMore: () -> Unit,
    password: PasswordState,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val keys = state.files.map { it.uri }
    val reorder: ListReorderState = rememberListReorderState(listState, keys, actions.onMove)
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        state.inputError?.let { item(key = "error") { ErrorCard(it, onRetry = null, Modifier.contentWidth()) } }
        if (state.tool == PdfTool.MERGE) {
            mergeFileItems(state, actions, reorder)
            if (state.job !is JobState.Running && state.job !is JobState.Success) {
                item(key = "add-more") {
                    OutlinedButton(
                        onClick = onAddMore,
                        shape = RoundedCornerShape(Dimens.ButtonCorner),
                        modifier = Modifier.contentWidth().heightIn(min = Dimens.MinTouchTarget),
                    ) { Text(stringResource(CoreR.string.action_add_more)) }
                }
            }
        } else {
            state.files.firstOrNull()?.let { file ->
                item(key = "summary") {
                    DocumentSummary(file, state.info, onChange = onAddMore, Modifier.contentWidth())
                }
            }
        }
        if (state.job !is JobState.Success) {
            item(key = "options") { Options(state, actions, password, Modifier.contentWidth()) }
        }
        item(key = "job") { JobSection(state, actions, Modifier.contentWidth()) }
    }
}

internal fun Modifier.contentWidth(): Modifier = this.widthIn(max = Dimens.CardMaxWidth).fillMaxWidth()

/** Pinned primary action: "Merge 3 PDFs", "Split into 4 files"... or Share and Start over after a job. */
@Composable
private fun BottomActions(state: PdfToolUiState, actions: PdfToolActions, enabled: Boolean, onRun: () -> Unit) {
    val showBanner = state.files.isNotEmpty()
    Column {
        when (state.job) {
            is JobState.Running -> Unit
            is JobState.Success -> if (state.resultRevealed) {
                StickyActionBar {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                        OutlinedButton(
                            onClick = actions.onReset,
                            shape = RoundedCornerShape(Dimens.ButtonCorner),
                            modifier = Modifier.weight(1f).heightIn(min = Dimens.PrimaryButtonHeight),
                        ) { Text(stringResource(CoreR.string.action_reset)) }
                        FilledTonalButton(
                            onClick = actions.onShareAll,
                            shape = RoundedCornerShape(Dimens.ButtonCorner),
                            modifier = Modifier.weight(1f).heightIn(min = Dimens.PrimaryButtonHeight),
                        ) {
                            Text(
                                stringResource(
                                    if (state.outputs.size > 1) R.string.pdf_share_all else CoreR.string.action_share,
                                ),
                            )
                        }
                    }
                }
            }
            else -> if (state.files.isNotEmpty()) {
                StickyActionBar {
                    runHint(state)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PrimaryActionButton(runLabel(state), onClick = onRun, enabled = state.canRun && enabled)
                }
            }
        }
        // The file list is one of the three screens allowed to show a banner.
        if (showBanner) LocalAdSlots.current.Banner(BannerPlacement.FILE_LIST, Modifier.fillMaxWidth())
    }
}

@Composable
private fun runLabel(state: PdfToolUiState): String = when (state.tool) {
    PdfTool.MERGE -> pluralStringResource(R.plurals.pdf_run_merge, state.files.size, state.files.size)
    PdfTool.IMAGES_TO_PDF -> pluralStringResource(R.plurals.pdf_run_images, state.files.size, state.files.size)
    PdfTool.SPLIT -> {
        val count = (state.splitPlan as? Result.Success)?.value?.size
        if (count == null) {
            toolLabel(state.tool)
        } else {
            pluralStringResource(R.plurals.pdf_run_split, count, count)
        }
    }
    else -> toolLabel(state.tool)
}

@Composable
private fun runHint(state: PdfToolUiState): String? = when {
    state.tool == PdfTool.MERGE && state.files.size < 2 -> stringResource(R.string.pdf_merge_need_two)
    state.info?.encrypted == true && state.tool != PdfTool.UNLOCK -> stringResource(R.string.pdf_unlock_first)
    else -> null
}

@Composable
internal fun JobSection(state: PdfToolUiState, actions: PdfToolActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adSlots = LocalAdSlots.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
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
            is JobState.Failed -> ErrorCard(job.error, onRetry = null)
            is JobState.Cancelled -> Text(stringResource(CoreR.string.job_cancelled))
            JobState.Idle -> Unit
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
    SectionHeader(pluralStringResource(R.plurals.pdf_result_files, outputs.size, outputs.size))
    outputs.forEachIndexed { index, file ->
        Card(Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(file.displayName) },
                supportingContent = {
                    val target = state.sizeTarget
                    val size = formatBytes(file.sizeBytes)
                    Text(
                        when {
                            target == null -> size
                            target.contains(file.sizeBytes) -> stringResource(R.string.pdf_target_met, size)
                            else -> stringResource(R.string.pdf_target_missed, size, target.maxKb)
                        },
                    )
                },
                trailingContent = {
                    OutlinedButton(onClick = {
                        pendingSave = index
                        saver.launch(file.displayName)
                    }, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
                        Text(stringResource(CoreR.string.action_save))
                    }
                },
            )
        }
    }
}

@Composable
private fun Options(
    state: PdfToolUiState,
    actions: PdfToolActions,
    password: PasswordState,
    modifier: Modifier = Modifier,
) {
    val o = state.options
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        state.sizeTarget?.let { target ->
            Text(
                stringResource(R.string.pdf_target_hint, target.minKb, target.maxKb),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        when (state.tool) {
            PdfTool.SPLIT -> SplitOptions(state, actions)
            PdfTool.EXTRACT, PdfTool.REORDER, PdfTool.DELETE, PdfTool.ROTATE, PdfTool.TO_IMAGES -> {
                PagesField(
                    value = o.pages,
                    onValue = { v -> actions.onOptions { it.copy(pages = v) } },
                    hint = stringResource(
                        if (state.tool == PdfTool.REORDER) R.string.pdf_order_hint else R.string.pdf_pages_hint,
                    ),
                )
                if (state.tool == PdfTool.ROTATE) {
                    SectionHeader(stringResource(R.string.pdf_rotate_by))
                    ChoiceGrid(listOf(90, 180, 270), o.degrees, { "$it°" }, { d ->
                        actions.onOptions { it.copy(degrees = d) }
                    }, columns = 3)
                }
                if (state.tool == PdfTool.TO_IMAGES) {
                    Text(stringResource(R.string.pdf_dpi, o.dpi))
                    Slider(
                        value = o.dpi.toFloat(),
                        onValueChange = { v -> actions.onOptions { it.copy(dpi = v.toInt()) } },
                        valueRange = MIN_DPI..MAX_DPI,
                    )
                }
            }
            PdfTool.COMPRESS -> {
                SectionHeader(stringResource(R.string.pdf_compress_level))
                ChoiceGrid(CompressionLevel.entries, o.level, { compressionLabel(it) }, { l ->
                    actions.onOptions { it.copy(level = l) }
                }, columns = 1)
                Text(stringResource(R.string.pdf_compress_note), style = MaterialTheme.typography.bodySmall)
            }
            PdfTool.WATERMARK -> WatermarkOptions(o, actions)
            PdfTool.PAGE_NUMBERS -> {
                SectionHeader(stringResource(R.string.pdf_position))
                ChoiceGrid(
                    listOf(Position.BOTTOM_CENTER, Position.BOTTOM_RIGHT, Position.TOP_RIGHT),
                    o.position,
                    { positionLabel(it) },
                    { p -> actions.onOptions { it.copy(position = p) } },
                    columns = 3,
                )
                SectionHeader(stringResource(R.string.pdf_number_style))
                ChoiceGrid(NumberStyle.entries, o.numberStyle, {
                    it.pattern.replace("{n}", "1").replace("{total}", "9")
                }, { n -> actions.onOptions { it.copy(numberStyle = n) } }, columns = 3)
            }
            PdfTool.PROTECT, PdfTool.UNLOCK -> {
                PasswordField(password.password, password.onPassword, stringResource(R.string.pdf_password))
                if (state.tool == PdfTool.PROTECT) {
                    PasswordField(password.confirm, password.onConfirm, stringResource(R.string.pdf_password_confirm))
                    if (password.confirm.isNotEmpty() && password.confirm != password.password) {
                        Text(stringResource(R.string.pdf_password_mismatch), color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(stringResource(R.string.pdf_password_note), style = MaterialTheme.typography.bodySmall)
            }
            PdfTool.MERGE, PdfTool.IMAGES_TO_PDF -> Unit
        }
    }
}

@Composable
internal fun PagesField(value: String, onValue: (String) -> Unit, hint: String, isError: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(stringResource(R.string.pdf_pages_label)) },
        supportingText = { Text(hint) },
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WatermarkOptions(o: PdfOptions, actions: PdfToolActions) {
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
        onValueChange = { v -> actions.onOptions { it.copy(opacity = v) } },
        valueRange = MIN_OPACITY..1f,
    )
    SwitchRow(
        label = stringResource(R.string.pdf_watermark_diagonal),
        checked = o.diagonal,
        onChecked = { v -> actions.onOptions { it.copy(diagonal = v) } },
    )
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

private fun uploadTitle(tool: PdfTool): Int = when {
    tool == PdfTool.MERGE -> R.string.pdf_upload_merge_title
    tool.acceptsImages -> R.string.pdf_upload_images_title
    else -> R.string.pdf_upload_single_title
}

private fun uploadSubtitle(tool: PdfTool): Int = when {
    tool == PdfTool.MERGE -> R.string.pdf_upload_merge_subtitle
    tool.acceptsImages -> R.string.pdf_upload_images_subtitle
    else -> R.string.pdf_upload_single_subtitle
}

@Composable
private fun compressionLabel(level: CompressionLevel): String = stringResource(
    when (level) {
        CompressionLevel.LOW -> R.string.pdf_compress_LOW
        CompressionLevel.MEDIUM -> R.string.pdf_compress_MEDIUM
        CompressionLevel.HIGH -> R.string.pdf_compress_HIGH
    },
)

@Composable
internal fun pageSizeLabel(size: PageSize): String = stringResource(
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

internal const val PDF_MIME = "application/pdf"
private const val PERCENT = 100
private const val MAX_WATERMARK = 60
private const val MIN_DPI = 72f
private const val MAX_DPI = 300f
private const val MIN_OPACITY = 0.1f

private const val PREVIEW_SIZE = 1_200L
private const val PREVIEW_PAGES = 4
private val PreviewActions = PdfToolActions({}, {}, { _, _ -> }, {}, {}, {}, {}, { _, _ -> }, {}, {})

@AllPreviews
@Composable
private fun PdfToolEmptyPreview() {
    MffTheme(dynamicColor = false) { PdfToolScreen(PdfToolUiState(PdfTool.MERGE), PreviewActions, onBack = {}) }
}

@AllPreviews
@Composable
private fun PdfToolWatermarkPreview() {
    MffTheme(dynamicColor = false) {
        PdfToolScreen(
            PdfToolUiState(
                PdfTool.WATERMARK,
                files = listOf(FileRef("content://x/1", "report.pdf", PDF_MIME, PREVIEW_SIZE)),
                info = PdfInfo(PREVIEW_PAGES, encrypted = false, sizeBytes = PREVIEW_SIZE),
                options = PdfOptions(watermarkText = "COPY"),
            ),
            PreviewActions,
            onBack = {},
        )
    }
}
