package com.mrfilefixit.feature.image.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mrfilefixit.core.domain.image.ColorAdjust
import com.mrfilefixit.core.domain.image.ImageFilter
import com.mrfilefixit.core.domain.image.NaturalEnhance
import com.mrfilefixit.core.domain.image.OverlayPosition
import com.mrfilefixit.core.domain.image.TextOverlay
import com.mrfilefixit.core.ui.components.BeforeAfterCard
import com.mrfilefixit.core.ui.components.ImageFacts
import com.mrfilefixit.core.ui.components.SectionCard
import com.mrfilefixit.core.ui.components.SectionHeader
import com.mrfilefixit.core.ui.components.SwitchRow
import com.mrfilefixit.core.ui.error.appErrorMessage
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.feature.image.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * The Edit tab: look and sliders, a line of text with its font and colour, the exam name and date bar, then the
 * output format. A live preview of all of it sits above these cards.
 */
@Composable
internal fun EditOptionsCards(state: ImageToolUiState, actions: ImageToolActions, modifier: Modifier = Modifier) {
    val edit = state.options.edit
    val update = { change: (EditOptions) -> EditOptions -> actions.onOptions { it.copy(edit = change(it.edit)) } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
        SectionCard(Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(R.string.image_edit_look))
            ChipRow(ImageFilter.entries, edit.filter, { filterLabel(it) }) { f -> update { it.copy(filter = f) } }
            val brightness = stringResource(R.string.image_brightness, edit.brightness)
            AdjustSlider(brightness, edit.brightness, "edit_brightness") {
                update { e -> e.copy(brightness = it) }
            }
            AdjustSlider(stringResource(R.string.image_contrast, edit.contrast), edit.contrast, "edit_contrast") {
                update { e -> e.copy(contrast = it) }
            }
            val saturation = stringResource(R.string.image_saturation, edit.saturation)
            AdjustSlider(saturation, edit.saturation, "edit_saturation") {
                update { e -> e.copy(saturation = it) }
            }
        }
        SectionCard(Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(R.string.image_text_title))
            OutlinedTextField(
                value = edit.text,
                onValueChange = { v -> update { it.copy(text = v.take(MAX_TEXT)) } },
                label = { Text(stringResource(R.string.image_text_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_text"),
            )
            ChipRow(OverlayPosition.entries, edit.textPosition, { positionLabel(it) }) { p ->
                update { it.copy(textPosition = p) }
            }
            TextStyleSample(edit.text, edit.textFont, edit.textColor)
            FontChips(edit.textFont) { f -> update { it.copy(textFont = f) } }
            TextSizeSlider(edit.textSize) { size -> update { it.copy(textSize = size) } }
            ColorPicker(edit.textColor, { c -> update { it.copy(textColor = c) } })
        }
        StampCard(edit, update)
        if (!edit.usesStampSize) FormatCard(state, actions)
    }
}

/** Text height as a share of the picture; like the other sliders here it updates the preview when released. */
@Composable
private fun TextSizeSlider(size: Float, onSize: (Float) -> Unit) {
    var value by remember(size) { mutableFloatStateOf(size) }
    Text(stringResource(R.string.image_text_size, (value * PERCENT).roundToInt()))
    Slider(
        value = value,
        onValueChange = { value = it },
        onValueChangeFinished = { onSize(value) },
        valueRange = TextOverlay.MIN_SIZE..EditOptions.MAX_TEXT_SIZE,
        modifier = Modifier.testTag("edit_text_size"),
    )
}

@Composable
private fun StampCard(edit: EditOptions, update: ((EditOptions) -> EditOptions) -> Unit) {
    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_PATTERN)) }
    SectionCard(Modifier.fillMaxWidth()) {
        SwitchRow(
            label = stringResource(R.string.image_stamp_title),
            checked = edit.stamp,
            onChecked = { on -> update { it.copy(stamp = on, stampDate = it.stampDate.ifEmpty { today }) } },
            supporting = stringResource(R.string.image_stamp_hint),
            modifier = Modifier.testTag("edit_stamp"),
        )
        if (!edit.stamp) return@SectionCard
        OutlinedTextField(
            value = edit.stampName,
            onValueChange = { v -> update { it.copy(stampName = v.take(MAX_TEXT)) } },
            label = { Text(stringResource(R.string.image_stamp_name)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("edit_stamp_name"),
        )
        OutlinedTextField(
            value = edit.stampDate,
            onValueChange = { v -> update { it.copy(stampDate = v.take(MAX_TEXT)) } },
            label = { Text(stringResource(R.string.image_stamp_date)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SwitchRow(
            label = stringResource(R.string.image_stamp_dark),
            checked = edit.stampDark,
            onChecked = { on -> update { it.copy(stampDark = on) } },
        )
        SwitchRow(
            label = stringResource(R.string.image_stamp_size),
            checked = edit.stampKeepSize,
            onChecked = { on -> update { it.copy(stampKeepSize = on) } },
            supporting = stringResource(R.string.image_stamp_size_hint),
        )
    }
}

/**
 * The Enhance tab: Natural Enhance strength (0 to 100%) with a live before / after preview, the output size
 * (original, 2x or 4x with Lanczos resampling), then the output format.
 */
@Composable
internal fun EnhanceOptionsCards(state: ImageToolUiState, actions: ImageToolActions, modifier: Modifier = Modifier) {
    val enhance = state.options.enhance
    val update = { change: (EnhanceOptions) -> EnhanceOptions ->
        actions.onOptions { it.copy(enhance = change(it.enhance)) }
    }
    // The slider moves freely; the strength (and so the preview) is only updated when it is released.
    var strength by remember(enhance.strength) { mutableFloatStateOf(enhance.strength) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
        SectionCard(Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(R.string.image_enhance_title))
            Text(
                stringResource(R.string.image_enhance_explain),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.image_enhance_strength, (strength * PERCENT).roundToInt()))
            Slider(
                value = strength,
                onValueChange = { strength = it },
                onValueChangeFinished = { update { it.copy(strength = strength) } },
                valueRange = 0f..1f,
                modifier = Modifier.testTag("enhance_strength"),
            )
            SectionHeader(stringResource(R.string.image_enhance_scale))
            ChipRow(NaturalEnhance.SCALES, enhance.scale, { scaleLabel(it) }) { f -> update { it.copy(scale = f) } }
            Text(
                stringResource(R.string.image_enhance_note, NaturalEnhance.MAX_SIDE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FormatCard(state, actions)
    }
}

/**
 * Live preview of the first image for Enhance, Edit and Remove background: split slider, before/after toggle and
 * full-screen zoom. When the effect cannot be applied (no person found), the reason shows instead of a spinner.
 */
@Composable
internal fun LivePreviewCard(preview: LivePreview, modifier: Modifier = Modifier) {
    val facts = ImageFacts(preview.width, preview.height, null, null)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        BeforeAfterCard(
            beforeUri = preview.beforeUri,
            afterUri = preview.afterUri,
            before = facts,
            after = facts,
            loading = preview.afterUri == null && preview.error == null,
            showFacts = false,
            modifier = Modifier.fillMaxWidth(),
        )
        preview.error?.let { error ->
            Text(
                appErrorMessage(error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("live_preview_error"),
            )
        }
        Text(
            stringResource(R.string.image_enhance_preview_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun scaleLabel(scale: Int): String =
    if (scale == 1) stringResource(R.string.image_enhance_scale_original) else "$scale×"

@Composable
internal fun FormatCard(state: ImageToolUiState, actions: ImageToolActions) {
    SectionCard(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.image_format))
        FormatChips(state.options, actions)
    }
}

@Composable
private fun AdjustSlider(label: String, value: Int, tag: String, onValue: (Int) -> Unit) {
    Text(label)
    Slider(
        value = value.toFloat(),
        onValueChange = { onValue(it.roundToInt()) },
        valueRange = ColorAdjust.RANGE.first.toFloat()..ColorAdjust.RANGE.last.toFloat(),
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun filterLabel(filter: ImageFilter): String = stringResource(
    when (filter) {
        ImageFilter.NONE -> R.string.image_filter_NONE
        ImageFilter.GRAYSCALE -> R.string.image_filter_GRAYSCALE
        ImageFilter.DOCUMENT -> R.string.image_filter_DOCUMENT
    },
)

@Composable
private fun positionLabel(position: OverlayPosition): String = stringResource(
    when (position) {
        OverlayPosition.TOP -> R.string.image_pos_TOP
        OverlayPosition.CENTER -> R.string.image_pos_CENTER
        OverlayPosition.BOTTOM -> R.string.image_pos_BOTTOM
    },
)

private const val PERCENT = 100
private const val DATE_PATTERN = "dd-MM-yyyy"

/** Long enough for a full name; the stamp shrinks text that would not fit the photo's width. */
private const val MAX_TEXT = 60
