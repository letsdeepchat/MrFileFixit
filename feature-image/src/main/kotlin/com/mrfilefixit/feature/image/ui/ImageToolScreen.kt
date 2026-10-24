package com.mrfilefixit.feature.image.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mrfilefixit.core.domain.exam.DocumentKind
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.components.CenteredEmptyState
import com.mrfilefixit.core.ui.components.CropBox
import com.mrfilefixit.core.ui.components.ErrorCard
import com.mrfilefixit.core.ui.components.JobProgressCard
import com.mrfilefixit.core.ui.components.PrimaryActionButton
import com.mrfilefixit.core.ui.components.SectionCard
import com.mrfilefixit.core.ui.components.SectionHeader
import com.mrfilefixit.core.ui.components.StickyActionBar
import com.mrfilefixit.core.ui.components.SwitchRow
import com.mrfilefixit.core.ui.components.ToolScaffold
import com.mrfilefixit.core.ui.components.UploadDropZone
import com.mrfilefixit.core.ui.components.displayName
import com.mrfilefixit.core.ui.format.formatBytes
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
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris ->
        actions.onPick(uris.map { it.toString() })
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    ToolScaffold(
        title = stringResource(R.string.image_title),
        modifier = modifier,
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
            ToolTabs(state.tool, actions.onTool)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolTabs(selected: ImageTool, onSelect: (ImageTool) -> Unit) {
    PrimaryScrollableTabRow(
        selectedTabIndex = ImageTool.entries.indexOf(selected),
        edgePadding = Dimens.ScreenPadding,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        ImageTool.entries.forEach { tool ->
            Tab(
                selected = tool == selected,
                onClick = { onSelect(tool) },
                text = { Text(toolLabel(tool)) },
                modifier = Modifier
                    .heightIn(min = Dimens.MinTouchTarget)
                    .testTag("image_tab_${tool.name.lowercase()}"),
            )
        }
    }
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
        state.inputError?.let { error ->
            item(key = "input-error") {
                ErrorCard(error, onRetry = null, Modifier.contentWidth())
            }
        }
        if (state.outcome == null) {
            item(key = "options") { ToolOptions(state, actions, Modifier.contentWidth()) }
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
        when (val job = state.job) {
            is JobState.Failed -> item(key = "job-error") { ErrorCard(job.error, null, Modifier.contentWidth()) }
            is JobState.Cancelled -> item(key = "job-cancelled") {
                Text(stringResource(CoreR.string.job_cancelled), Modifier.contentWidth())
            }
            else -> Unit
        }
        if (state.outcome != null) item(key = "results") { Results(state, actions, Modifier.contentWidth()) }
    }
}

@Composable
private fun SelectedFiles(state: ImageToolUiState, onChange: () -> Unit, modifier: Modifier = Modifier) {
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            items(state.files, key = { it.uri }) { file ->
                Thumbnail(file.uri, stringResource(CoreR.string.cd_thumbnail), Dimens.ThumbnailSize)
            }
        }
        state.firstInfo?.let { info ->
            Text(
                stringResource(R.string.image_original_size, info.width, info.height),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
                    maxHeight = LocalConfiguration.current.screenHeightDp.dp * CROP_MAX_HEIGHT_FRACTION,
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
            ChipRow(ImageFormat.entries, o.format, { it.displayName() }) { f ->
                actions.onOptions { it.copy(format = f) }
            }
            if (o.format == ImageFormat.HEIC && !actions.canEncode(ImageFormat.HEIC)) {
                Text(stringResource(R.string.image_heic_unavailable), color = MaterialTheme.colorScheme.error)
            }
            if (o.format.supportsQuality) QualitySlider(o.quality, actions)
        }
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

@Composable
private fun <T> ChipRow(values: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
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
        when (val job = state.job) {
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
    val outcome = state.outcome ?: return
    var pendingSave by remember { mutableStateOf<Int?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { uri ->
        val index = pendingSave
        if (uri != null && index != null) actions.onSave(index, uri.toString())
        pendingSave = null
    }
    SectionCard(modifier) {
        SectionHeader(pluralStringResource(R.plurals.image_done, outcome.images.size, outcome.images.size))
        if (outcome.failures.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.image_some_failed, outcome.failures.size, outcome.failures.size),
                color = MaterialTheme.colorScheme.error,
            )
        }
        outcome.images.forEachIndexed { index, image ->
            ListItem(
                headlineContent = { Text(image.file.displayName) },
                supportingContent = {
                    Text("${formatBytes(image.file.sizeBytes)} · ${image.file.width} × ${image.file.height}")
                },
                trailingContent = {
                    OutlinedButton(
                        onClick = {
                            pendingSave = index
                            saver.launch(image.file.displayName)
                        },
                        modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                    ) { Text(stringResource(CoreR.string.action_save)) }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        }
    }
}

private fun Modifier.contentWidth(): Modifier = widthIn(max = Dimens.CardMaxWidth).fillMaxWidth()

@Composable
private fun toolLabel(tool: ImageTool): String = stringResource(
    when (tool) {
        ImageTool.RESIZE -> R.string.image_tool_RESIZE
        ImageTool.CROP -> R.string.image_tool_CROP
        ImageTool.ROTATE -> R.string.image_tool_ROTATE
        ImageTool.COMPRESS -> R.string.image_tool_COMPRESS
        ImageTool.CONVERT -> R.string.image_tool_CONVERT
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
