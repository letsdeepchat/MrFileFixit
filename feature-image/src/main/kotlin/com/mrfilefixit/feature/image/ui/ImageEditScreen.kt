package com.mrfilefixit.feature.image.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mrfilefixit.core.domain.image.ColorAdjust
import com.mrfilefixit.core.domain.image.ImageFilter
import com.mrfilefixit.core.domain.image.OverlayPosition
import com.mrfilefixit.core.domain.image.Upscale
import com.mrfilefixit.core.ui.components.SectionCard
import com.mrfilefixit.core.ui.components.SectionHeader
import com.mrfilefixit.core.ui.components.SwitchRow
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.feature.image.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** The Edit tab: look and sliders, a line of text, the exam name and date bar, then the output format. */
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
            SwitchRow(
                label = stringResource(R.string.image_text_dark),
                checked = edit.textDark,
                onChecked = { on -> update { it.copy(textDark = on) } },
            )
        }
        StampCard(edit, update)
        if (!edit.usesStampSize) FormatCard(state, actions)
    }
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

/** The Enhance tab: 2x or 4x, how much to sharpen, then the output format. */
@Composable
internal fun EnhanceOptionsCards(state: ImageToolUiState, actions: ImageToolActions, modifier: Modifier = Modifier) {
    val enhance = state.options.enhance
    val update = { change: (EnhanceOptions) -> EnhanceOptions ->
        actions.onOptions { it.copy(enhance = change(it.enhance)) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
        SectionCard(Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(R.string.image_enhance_scale))
            ChipRow(UPSCALE_FACTORS, enhance.factor, { "$it×" }) { f -> update { it.copy(factor = f) } }
            Text(stringResource(R.string.image_enhance_sharpen, (enhance.sharpen * PERCENT).roundToInt()))
            Slider(
                value = enhance.sharpen,
                onValueChange = { v -> update { it.copy(sharpen = v) } },
                valueRange = 0f..1f,
                modifier = Modifier.testTag("enhance_sharpen"),
            )
            Text(
                stringResource(R.string.image_enhance_note, Upscale.MAX_SIDE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FormatCard(state, actions)
    }
}

@Composable
private fun FormatCard(state: ImageToolUiState, actions: ImageToolActions) {
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

private val UPSCALE_FACTORS = listOf(2, 4)
private const val PERCENT = 100
private const val DATE_PATTERN = "dd-MM-yyyy"

/** Long enough for a full name; the stamp shrinks text that would not fit the photo's width. */
private const val MAX_TEXT = 60
