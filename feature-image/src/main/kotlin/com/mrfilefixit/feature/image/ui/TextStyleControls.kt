package com.mrfilefixit.feature.image.ui

import android.graphics.Color as AndroidColor
import android.graphics.Typeface as AndroidTypeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.unit.dp
import com.mrfilefixit.core.domain.image.OverlayFont
import com.mrfilefixit.core.domain.image.TextOverlay
import com.mrfilefixit.core.ui.components.SectionHeader
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.feature.image.R

/** Font family chips, each label drawn in its own font. */
@Composable
internal fun FontChips(selected: OverlayFont, onSelect: (OverlayFont) -> Unit) {
    SectionHeader(stringResource(R.string.image_text_font))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        OverlayFont.entries.forEach { font ->
            val family = remember(font) {
                FontFamily(Typeface(AndroidTypeface.create(font.family, AndroidTypeface.NORMAL)))
            }
            FilterChip(
                selected = font == selected,
                onClick = { onSelect(font) },
                label = { Text(fontLabel(font), fontFamily = family) },
                modifier = Modifier.testTag("edit_font_${font.name.lowercase()}"),
            )
        }
    }
}

/**
 * A colour: preset swatches, then a custom swatch that opens hue and shade sliders. The chosen colour is
 * applied to the live preview as soon as a slider is released. [title] defaults to the text colour heading.
 */
@Composable
internal fun ColorPicker(
    color: Int,
    onColor: (Int) -> Unit,
    title: String = stringResource(R.string.image_text_color),
) {
    val swatches = EditOptions.TEXT_SWATCHES
    var custom by rememberSaveable { mutableStateOf(color !in swatches) }
    SectionHeader(title)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        swatches.forEachIndexed { index, swatch ->
            Swatch(
                fill = SolidColor(Color(swatch)),
                selected = !custom && color == swatch,
                checkLight = TextOverlay.isLightColor(swatch),
                label = stringResource(SWATCH_NAMES[index]),
                onClick = {
                    custom = false
                    onColor(swatch)
                },
                modifier = Modifier.testTag("edit_color_$index"),
            )
        }
        Swatch(
            fill = Brush.sweepGradient(RAINBOW),
            selected = custom,
            checkLight = true,
            label = stringResource(R.string.image_color_custom),
            onClick = { custom = true },
            modifier = Modifier.testTag("edit_color_custom"),
            icon = !custom,
        )
    }
    if (custom) CustomColorSliders(color, onColor)
}

@Composable
private fun CustomColorSliders(color: Int, onColor: (Int) -> Unit) {
    val hsv = remember(color) { FloatArray(HSV).also { AndroidColor.colorToHSV(color, it) } }
    var hue by remember(color) { mutableFloatStateOf(hsv[0]) }
    var shade by remember(color) { mutableFloatStateOf(hsv[2]) }
    val preview = AndroidColor.HSVToColor(floatArrayOf(hue, FULL_SATURATION, shade))
    val apply = { onColor(AndroidColor.HSVToColor(floatArrayOf(hue, FULL_SATURATION, shade))) }
    Box(
        Modifier
            .size(PREVIEW_SIZE)
            .clip(CircleShape)
            .background(Color(preview))
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
    Text(stringResource(R.string.image_color_hue), style = MaterialTheme.typography.labelLarge)
    Slider(
        value = hue,
        onValueChange = { hue = it },
        onValueChangeFinished = apply,
        valueRange = 0f..MAX_HUE,
        modifier = Modifier.testTag("edit_color_hue"),
    )
    Text(stringResource(R.string.image_color_shade), style = MaterialTheme.typography.labelLarge)
    Slider(
        value = shade,
        onValueChange = { shade = it },
        onValueChangeFinished = apply,
        valueRange = MIN_SHADE..1f,
        modifier = Modifier.testTag("edit_color_shade"),
    )
}

@Composable
@Suppress("LongParameterList")
private fun Swatch(
    fill: Brush,
    selected: Boolean,
    checkLight: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Boolean = false,
) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier
            .size(Dimens.MinTouchTarget)
            .clip(CircleShape)
            .border(if (selected) RING_SELECTED else RING, ring, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(SWATCH_SIZE)
                .clip(CircleShape)
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            val tint = if (checkLight) Color.Black else Color.White
            when {
                selected -> Icon(Icons.Filled.Check, contentDescription = null, tint = tint)
                icon -> Icon(Icons.Filled.Palette, contentDescription = null, tint = tint)
            }
        }
    }
}

@Composable
private fun fontLabel(font: OverlayFont): String = stringResource(
    when (font) {
        OverlayFont.SANS -> R.string.image_font_SANS
        OverlayFont.SERIF -> R.string.image_font_SERIF
        OverlayFont.MONO -> R.string.image_font_MONO
        OverlayFont.CONDENSED -> R.string.image_font_CONDENSED
        OverlayFont.HANDWRITING -> R.string.image_font_HANDWRITING
        OverlayFont.CASUAL -> R.string.image_font_CASUAL
    },
)

/** Names of [EditOptions.TEXT_SWATCHES], in the same order, for screen readers. */
private val SWATCH_NAMES = listOf(
    R.string.image_color_white,
    R.string.image_color_black,
    R.string.image_color_yellow,
    R.string.image_color_orange,
    R.string.image_color_red,
    R.string.image_color_pink,
    R.string.image_color_blue,
    R.string.image_color_green,
)

private val RAINBOW = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
private val SWATCH_SIZE = 36.dp
private val PREVIEW_SIZE = 32.dp
private val RING = 1.dp
private val RING_SELECTED = 3.dp
private const val HSV = 3
private const val MAX_HUE = 360f
private const val MIN_SHADE = 0.15f
private const val FULL_SATURATION = 1f
