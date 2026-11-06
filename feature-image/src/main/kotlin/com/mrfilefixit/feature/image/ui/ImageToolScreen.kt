package com.mrfilefixit.feature.image.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.mrfilefixit.core.domain.exam.DocumentKind
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.domain.scan.asSource
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.components.BeforeAfterCard
import com.mrfilefixit.core.ui.components.CenteredEmptyState
import com.mrfilefixit.core.ui.components.CropBox
import com.mrfilefixit.core.ui.components.ErrorCard
import com.mrfilefixit.core.ui.components.FullLabelTabRow
import com.mrfilefixit.core.ui.components.ImageFacts
import com.mrfilefixit.core.ui.components.JobProgressCard
import com.mrfilefixit.core.ui.components.KeepHorizontalScroll
import com.mrfilefixit.core.ui.components.PdfPageStrip
import com.mrfilefixit.core.ui.components.PrimaryActionButton
import com.mrfilefixit.core.ui.components.SectionCard
import com.mrfilefixit.core.ui.components.SectionHeader
import com.mrfilefixit.core.ui.components.StickyActionBar
import com.mrfilefixit.core.ui.components.SwitchRow
import com.mrfilefixit.core.ui.components.TabItem
import com.mrfilefixit.core.ui.components.ToolScaffold
import com.mrfilefixit.core.ui.components.UploadDropZone
import com.mrfilefixit.core.ui.components.UriImage
import com.mrfilefixit.core.ui.components.ZoomableImageDialog
import com.mrfilefixit.core.ui.components.displayName
import com.mrfilefixit.core.ui.components.windowHeight
import com.mrfilefixit.core.ui.format.formatBytes
import com.mrfilefixit.core.ui.navigation.ReportInnerScreen
import com.mrfilefixit.core.ui.navigation.rememberGuardedBack
import com.mrfilefixit.core.ui.preview.AllPreviews
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.theme.MffTheme
import com.mrfilefixit.core.ui.thumbnail.Thumbnail
import com.mrfilefixit.feature.image.R

internal class ImageToolActions(
    val onTool: (ImageTool) -> Unit,
    val onPick: (List<String>) -> Unit,
    val onOptions: ((ImageOptions) -> ImageOptions) -> Unit,
    val onResize: (ResizeEdit) -> Unit,
    val onRun: () -> Unit,
    val onCancel: () -> Unit,
    val onSave: (Int, String) -> Unit,
    val onShareAll: () -> Unit,
    val onReset: () -> Unit,
    val canEncode: (ImageFormat) -> Boolean,
)

private const val ASPECT_4_3 = 4f / 3f
private const val ASPECT_3_4 = 3f / 4f
private const val ASPECT_16_9 = 16f / 9f
private val CROP_ASPECTS = listOf<Float?>(null, 1f, ASPECT_4_3, ASPECT_3_4, ASPECT_16_9)

/**
 * Image tools: a scrollable tab per tool, a centred upload card until images are picked, then the
 * picked images, the tool's options in a card, and the primary action pinned at the bottom.
 */
@Composable
internal fun ImageToolScreen(
    state: ImageToolUiState,
    actions: ImageToolActions,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    onOpenOcr: () -> Unit = {},
    onExit: (() -> Unit)? = null,
) {
    // With images loaded the workspace is an inner screen: Back clears it (asking first if a result would be
    // lost); empty, Back leaves the tools when they have a screen of their own.
    val inner = state.files.isNotEmpty()
    ReportInnerScreen(inner)
    val clear = rememberGuardedBack(
        unsaved = state.outputs.isNotEmpty() && !state.delivered,
        onBack = actions.onReset,
        enabled = inner,
    )
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris ->
        actions.onPick(uris.map { it.toString() })
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    ToolScaffold(
        title = stringResource(R.string.image_title),
        modifier = modifier,
        onBack = if (inner) clear else onExit,
        actions = {
            if (state.files.isNotEmpty()) {
                TextButton(onClick = actions.onReset) { Text(stringResource(CoreR.string.action_clear_all)) }
            }
        },
        bottomBar = { if (state.files.isNotEmpty()) BottomActions(state, actions) },
        snackbarHost = snackbarHost,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            ToolTabs(state.tool, actions.onTool, onOpenOcr)
            if (state.files.isEmpty()) {
                CenteredEmptyState(Modifier.weight(1f)) {
                    UploadDropZone(
                        title = stringResource(R.string.image_pick_title),
                        subtitle = stringResource(R.string.image_pick_hint, MAX_PICK),
                        onClick = pick,
                        icon = Icons.Filled.AddPhotoAlternate,
                    )
                    // Resize options stay reachable before picking, so presets can be chosen first.
                    if (state.tool == ImageTool.RESIZE) ResizeOptions(state, actions, Modifier.contentWidth())
                }
            } else {
                LoadedContent(state, actions, onChange = pick, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** The tools with their full names, then a last tab that opens text recognition on its own screen. */
@Composable
private fun ToolTabs(selected: ImageTool, onSelect: (ImageTool) -> Unit, onOpenOcr: () -> Unit) {
    val tabs = ImageTool.entries.map { TabItem(toolLabel(it), "image_tab_${it.name.lowercase()}") } +
        TabItem(stringResource(R.string.image_tool_ocr), "image_tab_ocr")
    FullLabelTabRow(
        tabs = tabs,
        selectedIndex = ImageTool.entries.indexOf(selected),
        onSelect = { index -> ImageTool.entries.getOrNull(index)?.let(onSelect) ?: onOpenOcr() },
    )
}

@Composable
private fun LoadedContent(
    state: ImageToolUiState,
    actions: ImageToolActions,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "files") { SelectedFiles(state, onChange, Modifier.contentWidth()) }
        // Enhance, Edit and Remove background: the live before / after right under the photo, above the controls.
        if (state.tool in LIVE_PREVIEW_TOOLS && !state.isDone) {
            state.livePreview?.let { preview ->
                item(key = "live-preview") { LivePreviewCard(preview, Modifier.contentWidth()) }
            }
        }
        state.inputError?.let { error ->
            item(key = "input-error") {
                ErrorCard(error, onRetry = null, Modifier.contentWidth())
            }
        }
        if (!state.isDone) {
            // Keyed by tool, so nothing one tool remembers (a slider drag, a text field) carries into another.
            item(key = "options-${state.tool}") { ToolOptions(state, actions, Modifier.contentWidth()) }
        }
        if (state.isBatch) {
            item(key = "batch-note") {
                Text(
                    stringResource(R.string.image_batch_background),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.contentWidth(),
                )
            }
        }
        when (val job = state.shownJob) {
            is JobState.Failed -> item(key = "job-error") { ErrorCard(job.error, null, Modifier.contentWidth()) }
            is JobState.Cancelled -> item(key = "job-cancelled") {
                Text(stringResource(CoreR.string.job_cancelled), Modifier.contentWidth())
            }
            else -> Unit
        }
        if (state.isDone) item(key = "results") { Results(state, actions, Modifier.contentWidth()) }
    }
}

@Composable
private fun SelectedFiles(state: ImageToolUiState, onChange: () -> Unit, modifier: Modifier = Modifier) {
    val first = state.files.firstOrNull() ?: return
    var zoom by remember { mutableStateOf(false) }
    SectionCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(R.plurals.image_selected, state.files.size, state.files.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onChange, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
                Text(stringResource(CoreR.string.action_change))
            }
        }
        // The first picked image large right away (tap for full screen with zoom), the rest as thumbnails.
        val aspect = state.firstInfo?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: 1f
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = windowHeight() * PICKED_MAX_HEIGHT_FRACTION),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .aspectRatio(aspect.coerceIn(MIN_ASPECT, MAX_ASPECT), matchHeightConstraintsFirst = aspect < 1f)
                    .clip(RoundedCornerShape(Dimens.ButtonCorner))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClickLabel = stringResource(CoreR.string.preview_open_full)) { zoom = true }
                    .testTag("image_picked_preview"),
            ) {
                UriImage(first.uri, stringResource(CoreR.string.preview_original))
            }
        }
        if (state.files.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                modifier = Modifier.nestedScroll(KeepHorizontalScroll),
            ) {
                items(state.files, key = { it.uri }) { file ->
                    Thumbnail(file.uri, stringResource(CoreR.string.cd_thumbnail), Dimens.ThumbnailSize)
                }
            }
        }
        state.firstInfo?.let { info ->
            val format = info.format?.displayName()
            val size = formatBytes(info.sizeBytes)
            Text(
                listOfNotNull(stringResource(R.string.image_original_size, info.width, info.height), format, size)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (zoom) ZoomableImageDialog(first.uri, onDismiss = { zoom = false })
}

@Composable
private fun ToolOptions(state: ImageToolUiState, actions: ImageToolActions, modifier: Modifier = Modifier) {
    val o = state.options
    when (state.tool) {
        ImageTool.RESIZE -> ResizeOptions(state, actions, modifier)
        ImageTool.CROP -> SectionCard(modifier) {
            val info = state.firstInfo
            val file = state.files.firstOrNull()
            if (state.files.size > 1) Text(stringResource(R.string.image_crop_single_only))
            if (file != null && info != null && state.files.size == 1) {
                val imageAspect = info.width.toFloat() / info.height
                // Bounded canvas: at most 45% of the screen height, so tall photos never push the
                // aspect buttons off screen or draw over them.
                CropBox(
                    uri = file.uri,
                    imageAspect = imageAspect,
                    frameAspect = o.cropAspect ?: imageAspect,
                    onCropChanged = { rect: NormalizedRect -> actions.onOptions { it.copy(crop = rect) } },
                    maxHeight = windowHeight() * CROP_MAX_HEIGHT_FRACTION,
                )
            }
            SectionHeader(stringResource(R.string.image_aspect), Modifier.padding(top = Dimens.SpaceS))
            ChipRow(CROP_ASPECTS, o.cropAspect, { aspectLabel(it) }) { a ->
                actions.onOptions { it.copy(cropAspect = a) }
            }
        }
        ImageTool.ROTATE -> SectionCard(modifier) {
            SectionHeader(stringResource(R.string.image_rotate_by))
            ChipRow(listOf(90, 180, 270), o.degrees, { "$it°" }) { d -> actions.onOptions { it.copy(degrees = d) } }
        }
        ImageTool.COMPRESS -> SectionCard(modifier) {
            NumberField(
                o.targetKb,
                { v -> actions.onOptions { it.copy(targetKb = v.filter(Char::isDigit).take(MAX_DIGITS)) } },
                stringResource(R.string.image_target_kb),
                suffix = stringResource(R.string.image_unit_kb),
            )
            QualitySlider(o.quality, actions)
        }
        ImageTool.CONVERT -> SectionCard(modifier) {
            SectionHeader(stringResource(R.string.image_format))
            FormatChips(o, actions, offerPdf = true)
            if (o.toPdf) {
                Text(
                    stringResource(R.string.image_pdf_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ImageTool.EDIT -> EditOptionsCards(state, actions, modifier)
        ImageTool.ENHANCE -> EnhanceOptionsCards(state, actions, modifier)
        ImageTool.BACKGROUND -> BackgroundOptionsCards(state, actions, modifier)
        ImageTool.STRIP_EXIF -> SectionCard(modifier) {
            Text(stringResource(R.string.image_strip_note), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Width and height side by side, the aspect toggle as one row, then one-tap size presets. */
@Composable
private fun ResizeOptions(state: ImageToolUiState, actions: ImageToolActions, modifier: Modifier = Modifier) {
    val o = state.options
    SectionCard(modifier) {
        SectionHeader(stringResource(R.string.image_target_dimensions))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberField(
                o.width,
                { actions.onResize(ResizeEdit.Width(it)) },
                stringResource(R.string.image_width),
                Modifier
                    .weight(1f)
                    .testTag("resize_width"),
                suffix = stringResource(R.string.image_unit_px),
                imeAction = ImeAction.Next,
            )
            // Decorative: shows whether the two sides are linked. Each field announces its own label and the
            // switch below announces the state.
            val colors = MaterialTheme.colorScheme
            Icon(
                if (o.keepAspect) Icons.Filled.Link else Icons.Filled.LinkOff,
                contentDescription = null,
                tint = if (o.keepAspect) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics {},
            )
            NumberField(
                o.height,
                { actions.onResize(ResizeEdit.Height(it)) },
                stringResource(R.string.image_height),
                Modifier
                    .weight(1f)
                    .testTag("resize_height"),
                suffix = stringResource(R.string.image_unit_px),
            )
        }
        SwitchRow(
            label = stringResource(R.string.image_keep_aspect),
            checked = o.keepAspect,
            onChecked = { actions.onResize(ResizeEdit.KeepAspect(it)) },
            supporting = stringResource(R.string.image_keep_aspect_hint),
            modifier = Modifier.testTag("resize_keep_aspect"),
        )
        SectionHeader(stringResource(R.string.image_presets))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            state.presets.forEach { preset ->
                val label = presetLabel(preset)
                val size = stringResource(R.string.image_size_px, preset.width, preset.height)
                FilterChip(
                    selected = preset.id == o.presetId,
                    onClick = { actions.onResize(ResizeEdit.Preset(preset)) },
                    label = { Text("$label · $size") },
                    modifier = Modifier
                        .heightIn(min = Dimens.MinTouchTarget)
                        .testTag("preset_${preset.id}"),
                )
            }
        }
        state.activePreset?.spec?.let { spec ->
            Text(
                stringResource(
                    R.string.image_preset_summary,
                    spec.targetWidth,
                    spec.targetHeight,
                    spec.minKb,
                    spec.maxKb,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun presetLabel(preset: ResizePreset): String {
    val spec = preset.spec
    return if (preset.examName == null || spec == null) {
        stringResource(R.string.image_preset_passport)
    } else {
        stringResource(R.string.image_preset_exam, preset.examName, kindLabel(spec.kind))
    }
}

@Composable
private fun kindLabel(kind: DocumentKind): String = stringResource(
    when (kind) {
        DocumentKind.PHOTO -> R.string.image_kind_PHOTO
        DocumentKind.POSTCARD_PHOTO -> R.string.image_kind_POSTCARD_PHOTO
        DocumentKind.SIGNATURE -> R.string.image_kind_SIGNATURE
        DocumentKind.LEFT_THUMB -> R.string.image_kind_LEFT_THUMB
        DocumentKind.DECLARATION -> R.string.image_kind_DECLARATION
        DocumentKind.OTHER -> R.string.image_kind_OTHER
    },
)

@Composable
private fun QualitySlider(quality: Int, actions: ImageToolActions) {
    Text(stringResource(R.string.image_quality, quality))
    Slider(
        value = quality.toFloat(),
        onValueChange = { v -> actions.onOptions { it.copy(quality = v.toInt()) } },
        valueRange = MIN_QUALITY..MAX_QUALITY,
    )
}

@Composable
private fun NumberField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    imeAction: ImeAction = ImeAction.Done,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        suffix = if (suffix != null) {
            { Text(suffix) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * The image formats as chips, with the HEIC note and the quality slider. With [offerPdf], a PDF chip makes one
 * document from all the picked images instead.
 */
@Composable
internal fun FormatChips(o: ImageOptions, actions: ImageToolActions, offerPdf: Boolean = false) {
    val pdf = offerPdf && o.toPdf
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        ImageFormat.entries.forEach { format ->
            FilterChip(
                selected = !pdf && format == o.format,
                onClick = { actions.onOptions { it.copy(format = format, toPdf = false) } },
                label = { Text(format.displayName()) },
                modifier = Modifier
                    .heightIn(min = Dimens.MinTouchTarget)
                    .testTag("image_format_${format.name.lowercase()}"),
            )
        }
        if (offerPdf) {
            FilterChip(
                selected = pdf,
                onClick = { actions.onOptions { it.copy(toPdf = true) } },
                label = { Text(stringResource(R.string.image_format_pdf)) },
                modifier = Modifier
                    .heightIn(min = Dimens.MinTouchTarget)
                    .testTag("image_format_pdf"),
            )
        }
    }
    if (pdf) return
    if (o.format == ImageFormat.HEIC && !actions.canEncode(ImageFormat.HEIC)) {
        Text(stringResource(R.string.image_heic_unavailable), color = MaterialTheme.colorScheme.error)
    }
    if (o.format.supportsQuality) QualitySlider(o.quality, actions)
}

@Composable
internal fun <T> ChipRow(values: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
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

/** Sticky bottom bar: progress while running, Share / Start over after success, otherwise the run button. */
@Composable
private fun BottomActions(state: ImageToolUiState, actions: ImageToolActions) {
    StickyActionBar {
        when (val job = state.shownJob) {
            is JobState.Running -> JobProgressCard(job.progress, actions.onCancel)
            is JobState.Success -> {
                PrimaryActionButton(stringResource(CoreR.string.action_share), actions.onShareAll)
                TextButton(
                    onClick = actions.onReset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.MinTouchTarget),
                ) { Text(stringResource(R.string.image_start_over)) }
            }
            else -> PrimaryActionButton(
                text = pluralStringResource(R.plurals.image_run, state.files.size, state.files.size),
                onClick = actions.onRun,
                enabled = state.canRun,
                modifier = Modifier.testTag("image_run"),
            )
        }
    }
}

@Composable
private fun Results(state: ImageToolUiState, actions: ImageToolActions, modifier: Modifier = Modifier) {
    val outputs = state.outputs
    if (outputs.isEmpty()) return
    val failures = state.outcome?.failures.orEmpty()
    var pendingSave by remember { mutableStateOf<Int?>(null) }
    val saveType = outputs.map { it.mimeType }.distinct().singleOrNull() ?: ANY_IMAGE
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(saveType)) { uri ->
        val index = pendingSave
        if (uri != null && index != null) actions.onSave(index, uri.toString())
        pendingSave = null
    }
    ResultPreview(state, modifier)
    SectionCard(modifier) {
        SectionHeader(pluralStringResource(R.plurals.image_done, outputs.size, outputs.size))
        if (failures.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.image_some_failed, failures.size, failures.size),
                color = MaterialTheme.colorScheme.error,
            )
        }
        outputs.forEachIndexed { index, file ->
            ListItem(
                headlineContent = { Text(file.displayName) },
                supportingContent = {
                    val size = formatBytes(file.sizeBytes)
                    Text(if (file.width != null) "$size · ${file.width} × ${file.height}" else size)
                },
                trailingContent = {
                    OutlinedButton(
                        onClick = {
                            pendingSave = index
                            saver.launch(file.displayName)
                        },
                        modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                    ) { Text(stringResource(CoreR.string.action_save)) }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        }
    }
}

/**
 * The first result next to its original: a before / after compare for images, the pages for a PDF. The facts
 * table shows original and result dimensions, format and exact size.
 */
@Composable
private fun ResultPreview(state: ImageToolUiState, modifier: Modifier = Modifier) {
    val output = state.outputs.firstOrNull() ?: return
    val source = state.files.firstOrNull() ?: return
    if (output.mimeType == PDF_MIME) {
        SectionCard(modifier) {
            SectionHeader(stringResource(CoreR.string.preview_pages_title))
            PdfPageStrip(output.asSource().uri)
        }
        return
    }
    val info = state.firstInfo
    BeforeAfterCard(
        beforeUri = source.uri,
        afterUri = output.asSource().uri,
        before = ImageFacts(
            info?.width,
            info?.height,
            info?.format?.displayName(),
            info?.sizeBytes ?: source.sizeBytes,
        ),
        after = ImageFacts(output.width, output.height, formatOf(output.mimeType), output.sizeBytes),
        modifier = modifier,
    )
}

private fun formatOf(mimeType: String): String? = ImageFormat.fromMimeType(mimeType)?.displayName()

private fun Modifier.contentWidth(): Modifier = widthIn(max = Dimens.CardMaxWidth).fillMaxWidth()

@Composable
private fun toolLabel(tool: ImageTool): String = stringResource(
    when (tool) {
        ImageTool.RESIZE -> R.string.image_tool_RESIZE
        ImageTool.CROP -> R.string.image_tool_CROP
        ImageTool.ROTATE -> R.string.image_tool_ROTATE
        ImageTool.COMPRESS -> R.string.image_tool_COMPRESS
        ImageTool.CONVERT -> R.string.image_tool_CONVERT
        ImageTool.EDIT -> R.string.image_tool_EDIT
        ImageTool.ENHANCE -> R.string.image_tool_ENHANCE
        ImageTool.BACKGROUND -> R.string.image_tool_BACKGROUND
        ImageTool.STRIP_EXIF -> R.string.image_tool_STRIP_EXIF
    },
)

@Composable
private fun aspectLabel(aspect: Float?): String = when (aspect) {
    null -> stringResource(R.string.image_aspect_free)
    1f -> "1:1"
    ASPECT_4_3 -> "4:3"
    ASPECT_3_4 -> "3:4"
    else -> "16:9"
}

private const val MAX_PICK = 100
private val LIVE_PREVIEW_TOOLS = setOf(ImageTool.ENHANCE, ImageTool.EDIT, ImageTool.BACKGROUND)
private const val PDF_MIME = "application/pdf"
private const val PICKED_MAX_HEIGHT_FRACTION = 0.4f
private const val MIN_ASPECT = 0.3f
private const val MAX_ASPECT = 3f
private const val ANY_IMAGE = "image/*"
private const val CROP_MAX_HEIGHT_FRACTION = 0.45f
private const val MAX_DIGITS = 5
private const val MIN_QUALITY = 10f
private const val MAX_QUALITY = 100f

private val PreviewActions = ImageToolActions({}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, { true })

@AllPreviews
@Composable
private fun ImageToolEmptyPreview() {
    MffTheme(dynamicColor = false) {
        ImageToolScreen(ImageToolUiState(), actions = PreviewActions)
    }
}

@AllPreviews
@Composable
private fun ImageToolCompressPreview() {
    MffTheme(dynamicColor = false) {
        ImageToolScreen(ImageToolUiState(tool = ImageTool.COMPRESS), actions = PreviewActions)
    }
}
