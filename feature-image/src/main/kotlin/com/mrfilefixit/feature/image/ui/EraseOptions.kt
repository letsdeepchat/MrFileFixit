package com.mrfilefixit.feature.image.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import com.mrfilefixit.core.domain.image.EraseStroke
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.components.SectionCard
import com.mrfilefixit.core.ui.components.SectionHeader
import com.mrfilefixit.core.ui.components.SegmentedChoice
import com.mrfilefixit.core.ui.components.UriImage
import com.mrfilefixit.core.ui.components.windowHeight
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.feature.image.R
import kotlin.math.hypot
import kotlin.math.min

/**
 * Erase object: the photo with a paint layer on top. Painting marks what should go (shown in red); the live
 * preview above shows the photo with it filled in from the surroundings. Everything runs on the phone.
 */
@Composable
internal fun EraseOptionsCard(state: ImageToolUiState, actions: ImageToolActions, modifier: Modifier = Modifier) {
    val erase = state.options.erase
    val file = state.files.firstOrNull()
    val info = state.firstInfo
    SectionCard(modifier) {
        SectionHeader(stringResource(R.string.image_erase_title))
        Text(stringResource(R.string.image_erase_hint), style = MaterialTheme.typography.bodyMedium)
        if (state.files.size > 1) {
            Text(stringResource(R.string.image_erase_single_only), color = MaterialTheme.colorScheme.error)
        } else if (file != null && info != null) {
            PaintArea(
                uri = file.uri,
                aspect = info.width.toFloat() / info.height.coerceAtLeast(1),
                strokes = erase.strokes,
                radius = erase.brush.radius,
                onStroke = { stroke -> actions.editErase { it.copy(strokes = it.strokes + stroke) } },
            )
            Text(stringResource(R.string.image_erase_brush), style = MaterialTheme.typography.labelLarge)
            SegmentedChoice(
                options = EraseBrush.entries,
                selected = erase.brush,
                label = { brushLabel(it) },
                onSelect = { brush -> actions.editErase { it.copy(brush = brush) } },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                OutlinedButton(
                    onClick = { actions.editErase { it.copy(strokes = it.strokes.dropLast(1)) } },
                    enabled = erase.strokes.isNotEmpty(),
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget).testTag("erase_undo"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                    Text(stringResource(R.string.image_erase_undo), Modifier.padding(start = Dimens.SpaceS))
                }
                OutlinedButton(
                    onClick = { actions.editErase { it.copy(strokes = emptyList()) } },
                    enabled = erase.strokes.isNotEmpty(),
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget).testTag("erase_clear"),
                ) {
                    Icon(Icons.Filled.Clear, contentDescription = null)
                    Text(stringResource(CoreR.string.action_clear_all), Modifier.padding(start = Dimens.SpaceS))
                }
            }
            Text(
                stringResource(R.string.image_erase_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The photo at its own aspect ratio, so a touch maps straight to a spot on the picture, with the strokes on top. */
@Composable
private fun PaintArea(
    uri: String,
    aspect: Float,
    strokes: List<EraseStroke>,
    radius: Float,
    onStroke: (EraseStroke) -> Unit,
) {
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val currentRadius by rememberUpdatedState(radius)
    val emit by rememberUpdatedState(onStroke)
    val paint = MaterialTheme.colorScheme.error.copy(alpha = PAINT_ALPHA)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = windowHeight() * PAINT_MAX_HEIGHT_FRACTION),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .aspectRatio(aspect.coerceIn(MIN_ASPECT, MAX_ASPECT), matchHeightConstraintsFirst = aspect < 1f)
                .clip(RoundedCornerShape(Dimens.ButtonCorner))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag("erase_canvas"),
        ) {
            UriImage(uri, stringResource(CoreR.string.preview_original))
            Canvas(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectTapGestures { point ->
                            emit(EraseStroke(normalised(point, size).let { listOf(it.x, it.y) }, currentRadius))
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { point -> current = listOf(normalised(point, size)) },
                            onDrag = { change, _ ->
                                val next = normalised(change.position, size)
                                val last = current.lastOrNull()
                                if (last == null || hypot(next.x - last.x, next.y - last.y) > MIN_STEP) {
                                    current = current + next
                                }
                                change.consume()
                            },
                            onDragEnd = {
                                if (current.isNotEmpty()) {
                                    emit(EraseStroke(current.flatMap { listOf(it.x, it.y) }, currentRadius))
                                }
                                current = emptyList()
                            },
                            onDragCancel = { current = emptyList() },
                        )
                    },
            ) {
                strokes.forEach { stroke ->
                    val points = stroke.points.chunked(2).filter { it.size == 2 }.map { Offset(it[0], it[1]) }
                    drawStroke(points, stroke.radius, paint)
                }
                if (current.isNotEmpty()) drawStroke(current, radius, paint)
            }
        }
    }
}

private fun ImageToolActions.editErase(transform: (EraseOptions) -> EraseOptions) =
    onOptions { it.copy(erase = transform(it.erase)) }

private fun normalised(point: Offset, size: IntSize): Offset = Offset(
    (point.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
    (point.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
)

private fun DrawScope.drawStroke(points: List<Offset>, radius: Float, color: Color) {
    val side = min(size.width, size.height)
    val r = radius * side
    val scaled = points.map { Offset(it.x * size.width, it.y * size.height) }
    if (scaled.isEmpty()) return
    if (scaled.size == 1) {
        drawCircle(color, r, scaled.first())
        return
    }
    val path = Path().apply {
        moveTo(scaled.first().x, scaled.first().y)
        scaled.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, color, style = Stroke(width = 2 * r, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun brushLabel(brush: EraseBrush): String = stringResource(
    when (brush) {
        EraseBrush.SMALL -> R.string.image_erase_brush_small
        EraseBrush.MEDIUM -> R.string.image_erase_brush_medium
        EraseBrush.LARGE -> R.string.image_erase_brush_large
    },
)

private const val PAINT_ALPHA = 0.5f
private const val PAINT_MAX_HEIGHT_FRACTION = 0.55f
private const val MIN_ASPECT = 0.3f
private const val MAX_ASPECT = 3f
private const val MIN_STEP = 0.004f
