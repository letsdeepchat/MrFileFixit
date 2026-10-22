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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.ads.BannerPlacement
import com.mrfilefixit.core.ui.ads.LocalAdSlots
import com.mrfilefixit.core.ui.components.CropBox
import com.mrfilefixit.core.ui.components.ErrorCard
import com.mrfilefixit.core.ui.components.JobProgressCard
import com.mrfilefixit.core.ui.components.displayName
import com.mrfilefixit.core.ui.format.formatBytes
import com.mrfilefixit.core.ui.preview.AllPreviews
import com.mrfilefixit.core.ui.share.findActivity
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.theme.MffTheme
import com.mrfilefixit.core.ui.thumbnail.Thumbnail
import com.mrfilefixit.feature.image.R
import kotlinx.coroutines.launch

internal class ImageToolActions(
    val onTool: (ImageTool) -> Unit,
    val onPick: (List<String>) -> Unit,
    val onOptions: ((ImageOptions) -> ImageOptions) -> Unit,
    val onRun: () -> Unit,
    val onCancel: () -> Unit,
    val onRevealed: () -> Unit,
    val onRewardEarned: () -> Unit,
    val onSave: (Int, String) -> Unit,
    val onShareAll: () -> Unit,
    val onReset: () -> Unit,
    val canEncode: (ImageFormat) -> Boolean,
)

private const val ASPECT_4_3 = 4f / 3f
private const val ASPECT_3_4 = 3f / 4f
private const val ASPECT_16_9 = 16f / 9f
private val CROP_ASPECTS = listOf<Float?>(null, 1f, ASPECT_4_3, ASPECT_3_4, ASPECT_16_9)

@Composable
internal fun ImageToolScreen(
    state: ImageToolUiState,
    remainingBatches: Int,
    actions: ImageToolActions,
    modifier: Modifier = Modifier,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris ->
        actions.onPick(uris.map { it.toString() })
    }
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(Dimens.SpaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    stringResource(R.string.image_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics {
                        heading()
                    },
                )
            }
            item {
                ChipRow(ImageTool.entries, state.tool, { toolLabel(it) }, actions.onTool)
            }
            item {
                Button(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                ) { Text(stringResource(R.string.image_pick)) }
            }
            if (state.files.isNotEmpty()) {
                item {
                    Text(pluralStringResource(R.plurals.image_selected, state.files.size, state.files.size))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                        items(state.files, key = { it.uri }) { file ->
                            Thumbnail(file.uri, stringResource(CoreR.string.cd_thumbnail), Dimens.ThumbnailSize)
                        }
                    }
                }
            }
            state.inputError?.let { error ->
                item {
                    if (error is AppError.QuotaExceeded) QuotaCard(actions) else ErrorCard(error, onRetry = null)
                }
            }
            item { Options(state, actions) }
            if (state.isBatch) {
                item {
                    Text(
                        stringResource(R.string.image_quota_left, remainingBatches) + "\n" +
                            stringResource(R.string.image_batch_background),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item { JobSection(state, actions) }
        }
        if (state.files.isNotEmpty()) LocalAdSlots.current.Banner(BannerPlacement.FILE_LIST, Modifier.fillMaxWidth())
    }
}

@Composable
private fun QuotaCard(actions: ImageToolActions) {
    val context = LocalContext.current
    val adSlots = LocalAdSlots.current
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Text(stringResource(R.string.image_quota_empty))
        // Opt-in only: the ad plays when the user taps, and the reward is granted only when it is earned.
        OutlinedButton(
            onClick = {
                val activity = context.findActivity() ?: return@OutlinedButton
                scope.launch { if (adSlots.showRewarded(activity)) actions.onRewardEarned() }
            },
            modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
        ) { Text(stringResource(R.string.image_watch_ad)) }
    }
}

@Composable
private fun Options(state: ImageToolUiState, actions: ImageToolActions) {
    val o = state.options
    Column(
        Modifier.widthIn(max = Dimens.CardMaxWidth).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        when (state.tool) {
            ImageTool.RESIZE -> {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                    NumberField(o.width, { v ->
                        actions.onOptions { it.copy(width = v) }
                    }, stringResource(R.string.image_width), Modifier.weight(1f))
                    NumberField(o.height, { v ->
                        actions.onOptions { it.copy(height = v) }
                    }, stringResource(R.string.image_height), Modifier.weight(1f))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                ) {
                    Text(stringResource(R.string.image_keep_aspect), Modifier.weight(1f))
                    Switch(o.keepAspect, { v -> actions.onOptions { it.copy(keepAspect = v) } })
                }
            }
            ImageTool.CROP -> {
                val info = state.firstInfo
                val file = state.files.firstOrNull()
                if (state.files.size > 1) Text(stringResource(R.string.image_crop_single_only))
                Text(stringResource(R.string.image_aspect), style = MaterialTheme.typography.titleSmall)
                ChipRow(CROP_ASPECTS, o.cropAspect, {
                    aspectLabel(it)
                }) { a -> actions.onOptions { it.copy(cropAspect = a) } }
                if (file != null && info != null && state.files.size == 1) {
                    val imageAspect = info.width.toFloat() / info.height
                    CropBox(
                        uri = file.uri,
                        imageAspect = imageAspect,
                        frameAspect = o.cropAspect ?: imageAspect,
                        onCropChanged = { rect: NormalizedRect -> actions.onOptions { it.copy(crop = rect) } },
                    )
                }
            }
            ImageTool.ROTATE -> ChipRow(listOf(90, 180, 270), o.degrees, {
                "$it°"
            }) { d -> actions.onOptions { it.copy(degrees = d) } }
            ImageTool.COMPRESS -> {
                NumberField(o.targetKb, { v ->
                    actions.onOptions { it.copy(targetKb = v) }
                }, stringResource(R.string.image_target_kb))
                QualitySlider(o.quality, actions)
            }
            ImageTool.CONVERT -> {
                Text(stringResource(R.string.image_format), style = MaterialTheme.typography.titleSmall)
                ChipRow(ImageFormat.entries, o.format, {
                    it.displayName()
                }) { f -> actions.onOptions { it.copy(format = f) } }
                if (o.format == ImageFormat.HEIC && !actions.canEncode(ImageFormat.HEIC)) {
                    Text(stringResource(R.string.image_heic_unavailable), color = MaterialTheme.colorScheme.error)
                }
                if (o.format.supportsQuality) QualitySlider(o.quality, actions)
            }
            ImageTool.STRIP_EXIF -> Text(
                stringResource(R.string.image_strip_note),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun QualitySlider(quality: Int, actions: ImageToolActions) {
    Text(stringResource(R.string.image_quality, quality))
    Slider(
        value = quality.toFloat(),
        onValueChange = { v ->
            actions.onOptions { it.copy(quality = v.toInt()) }
        },
        valueRange =
        10f..100f,
    )
}

@Composable
private fun NumberField(value: String, onValue: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValue(v.filter(Char::isDigit).take(MAX_DIGITS)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
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
private fun JobSection(state: ImageToolUiState, actions: ImageToolActions) {
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
                    onClick = actions.onRun,
                    enabled = state.canRun,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouchTarget),
                ) { Text(stringResource(R.string.image_run)) }
            }
        }
    }
}

@Composable
private fun Results(state: ImageToolUiState, actions: ImageToolActions) {
    val outcome = state.outcome ?: return
    var pendingSave by remember { mutableStateOf<Int?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { uri ->
        val index = pendingSave
        if (uri != null && index != null) actions.onSave(index, uri.toString())
        pendingSave = null
    }
    Text(
        pluralStringResource(R.plurals.image_done, outcome.images.size, outcome.images.size),
        style = MaterialTheme.typography.titleSmall,
    )
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
                OutlinedButton(onClick = {
                    pendingSave = index
                    saver.launch(image.file.displayName)
                }) { Text(stringResource(CoreR.string.action_save)) }
            },
        )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        FilledTonalButton(onClick = actions.onShareAll, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
            Text(stringResource(CoreR.string.action_share))
        }
        OutlinedButton(onClick = actions.onReset, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
            Text(stringResource(CoreR.string.action_reset))
        }
    }
}

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
private const val MAX_DIGITS = 5

@AllPreviews
@Composable
private fun ImageToolPreview() {
    MffTheme(dynamicColor = false) {
        ImageToolScreen(
            ImageToolUiState(tool = ImageTool.COMPRESS),
            remainingBatches = 3,
            actions = ImageToolActions({}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, { true }),
        )
    }
}
