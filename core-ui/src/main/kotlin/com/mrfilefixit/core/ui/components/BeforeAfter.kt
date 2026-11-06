package com.mrfilefixit.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mrfilefixit.core.ui.R
import com.mrfilefixit.core.ui.format.formatBytes
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.thumbnail.ThumbnailCache
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the comparison card shows under a picture. Null values are shown as "–". */
data class ImageFacts(val width: Int?, val height: Int?, val format: String?, val sizeBytes: Long?) {
    val aspect: Float? get() = if (width != null && height != null && height > 0) width.toFloat() / height else null
}

/** How the two pictures are compared. */
enum class CompareMode { SLIDER, BEFORE, AFTER }

/**
 * Before / after preview: the picked image right away, then the result next to it once there is one. With the
 * same shape on both sides, a draggable divider reveals one over the other (split-screen); otherwise (crop,
 * rotate) a toggle switches between them. A full-screen view supports pinch-to-zoom, and a small table compares
 * dimensions, format and exact file size.
 *
 * [beforeUri] and [afterUri] are `content://` or `file://` URIs. A live preview (made at a reduced size) passes
 * [showFacts] false, since its sizes are not the ones the saved file will have.
 */
@Composable
fun BeforeAfterCard(
    beforeUri: String,
    afterUri: String?,
    before: ImageFacts?,
    after: ImageFacts?,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    showFacts: Boolean = true,
) {
    val sameShape = sameShape(before?.aspect, after?.aspect)
    var mode by rememberSaveable(sameShape) {
        mutableStateOf(if (sameShape) CompareMode.SLIDER else CompareMode.AFTER)
    }
    val effectiveMode = when {
        afterUri == null -> CompareMode.BEFORE
        mode == CompareMode.SLIDER && !sameShape -> CompareMode.AFTER
        else -> mode
    }
    var fullScreen by remember { mutableStateOf(false) }
    SectionCard(modifier.testTag("before_after_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(stringResource(R.string.preview_title), Modifier.weight(1f))
            IconButton(onClick = { fullScreen = true }) {
                Icon(Icons.Filled.OpenInFull, contentDescription = stringResource(R.string.preview_open_full))
            }
        }
        if (afterUri != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                val modes = if (sameShape) CompareMode.entries else listOf(CompareMode.BEFORE, CompareMode.AFTER)
                modes.forEach { m ->
                    FilterChip(
                        selected = effectiveMode == m,
                        onClick = { mode = m },
                        label = { Text(modeLabel(m)) },
                        modifier = Modifier
                            .heightIn(min = Dimens.MinTouchTarget)
                            .testTag("compare_${m.name.lowercase()}"),
                    )
                }
            }
        }
        val aspect = (if (effectiveMode == CompareMode.AFTER) after?.aspect else before?.aspect)
            ?: before?.aspect ?: DEFAULT_ASPECT
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = windowHeight() * MAX_HEIGHT_SHARE),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .aspectRatio(aspect.coerceIn(MIN_ASPECT, MAX_ASPECT), matchHeightConstraintsFirst = aspect < 1f)
                    .clip(RoundedCornerShape(Dimens.ButtonCorner))
                    .checkerboard(),
            ) {
                when (effectiveMode) {
                    CompareMode.SLIDER -> SplitCompare(beforeUri, afterUri ?: beforeUri)
                    CompareMode.BEFORE -> UriImage(beforeUri, stringResource(R.string.preview_before))
                    CompareMode.AFTER -> UriImage(afterUri ?: beforeUri, stringResource(R.string.preview_after))
                }
                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
        if (loading) {
            Text(
                stringResource(R.string.preview_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showFacts) FactsTable(before, after.takeIf { afterUri != null })
    }
    if (fullScreen) {
        val uri = if (effectiveMode == CompareMode.BEFORE || afterUri == null) beforeUri else afterUri
        ZoomableImageDialog(uri = uri, onDismiss = { fullScreen = false })
    }
}

/** The usual "see-through" pattern, so a cut-out with a transparent background shows as one. */
private fun Modifier.checkerboard(): Modifier = drawBehind {
    val cell = CHECKER_CELL.toPx()
    drawRect(CHECKER_LIGHT)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else cell
        while (x < size.width) {
            drawRect(CHECKER_DARK, Offset(x, y), Size(cell, cell))
            x += 2 * cell
        }
        y += cell
        row++
    }
}

/** Both pictures stacked; the "before" one is clipped to the left of a draggable divider. */
@Composable
private fun SplitCompare(beforeUri: String, afterUri: String) {
    var fraction by rememberSaveable { mutableFloatStateOf(HALF) }
    val description = stringResource(R.string.cd_compare_divider)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .testTag("compare_slider")
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { value ->
                    fraction = value.coerceIn(0f, 1f)
                    true
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    fraction = (fraction + dragAmount / size.width).coerceIn(0f, 1f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset -> fraction = (offset.x / size.width).coerceIn(0f, 1f) }
            },
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val dividerPx = with(density) { DIVIDER_WIDTH.roundToPx() }
        val handlePx = with(density) { HANDLE_SIZE.roundToPx() }
        UriImage(afterUri, stringResource(R.string.preview_after))
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent { clipRect(right = size.width * fraction) { this@drawWithContent.drawContent() } },
        ) {
            UriImage(beforeUri, stringResource(R.string.preview_before))
        }
        val handleOffset = (widthPx * fraction).roundToInt()
        Box(
            Modifier
                .offset { IntOffset(handleOffset - dividerPx / 2, 0) }
                .width(DIVIDER_WIDTH)
                .fillMaxHeight()
                .background(Color.White),
        )
        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(handleOffset - handlePx / 2, 0) }
                .size(HANDLE_SIZE),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.CompareArrows,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.padding(Dimens.SpaceXs),
            )
        }
        CornerLabel(stringResource(R.string.preview_before), Alignment.TopStart)
        CornerLabel(stringResource(R.string.preview_after), Alignment.TopEnd)
    }
}

@Composable
private fun CornerLabel(text: String, alignment: Alignment) {
    Box(Modifier.fillMaxSize().padding(Dimens.SpaceS), contentAlignment = alignment) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.SpaceXs))
                .background(Color.Black.copy(alpha = LABEL_SCRIM))
                .padding(horizontal = Dimens.SpaceS, vertical = 2.dp),
        )
    }
}

/** Loads [uri] at the size it is drawn (capped), through the shared thumbnail cache. */
@Composable
fun UriImage(uri: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val px = with(LocalDensity.current) { maxOf(maxWidth, maxHeight).roundToPx() }.coerceIn(1, MAX_DECODE_PX)
        val image: ImageBitmap? by produceState(ThumbnailCache.get(uri, px)?.asImageBitmap(), uri, px) {
            value = ThumbnailCache.load(context, uri, px)?.asImageBitmap()
        }
        image?.let { Image(it, contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}

/** Original vs result: dimensions, format and exact size, side by side. */
@Composable
private fun FactsTable(before: ImageFacts?, after: ImageFacts?) {
    if (before == null && after == null) return
    val rows = listOf(
        stringResource(R.string.preview_dimensions) to { f: ImageFacts? ->
            if (f?.width != null && f.height != null) "${f.width} × ${f.height} px" else DASH
        },
        stringResource(R.string.preview_format) to { f: ImageFacts? -> f?.format ?: DASH },
    )
    FactsRow("", stringResource(R.string.preview_original), stringResource(R.string.preview_processed), header = true)
    rows.forEach { (label, value) -> FactsRow(label, value(before), if (after != null) value(after) else DASH) }
    FactsRow(
        stringResource(R.string.preview_file_size),
        before?.sizeBytes?.let { formatBytes(it) } ?: DASH,
        after?.sizeBytes?.let { formatBytes(it) } ?: DASH,
    )
}

@Composable
private fun FactsRow(label: String, original: String, result: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(label, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(original, style = style, color = color, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(result, style = style, color = color, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

/** Full-screen picture with pinch-to-zoom (1x to 5x), drag to pan and double-tap to reset. */
@Composable
fun ZoomableImageDialog(uri: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                ZoomableContent(Modifier.fillMaxSize()) { UriImage(uri, stringResource(R.string.preview_title)) }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Dimens.SpaceS),
                ) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_close), tint = Color.White)
                }
                Text(
                    stringResource(R.string.preview_zoom_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(Dimens.SpaceM),
                )
            }
        }
    }
}

/** Pinch, pan and double-tap-to-reset for any content; panning stops at the content's edges. */
@Composable
fun ZoomableContent(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .clipToBounds()
            .testTag("zoomable")
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                    val maxX = (scale - 1f) * size.width / 2f
                    val maxY = (scale - 1f) * size.height / 2f
                    offset = Offset(
                        (offset.x + pan.x).coerceIn(-maxX, maxX),
                        (offset.y + pan.y).coerceIn(-maxY, maxY),
                    )
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        ) { content() }
    }
}

@Composable
private fun modeLabel(mode: CompareMode): String = stringResource(
    when (mode) {
        CompareMode.SLIDER -> R.string.preview_mode_slider
        CompareMode.BEFORE -> R.string.preview_before
        CompareMode.AFTER -> R.string.preview_after
    },
)

/** Two pictures can share a split view when their shapes match within 2%. */
fun sameShape(a: Float?, b: Float?): Boolean = a != null && b != null && abs(a - b) / a <= SAME_SHAPE_TOLERANCE

private const val DASH = "–"
private const val HALF = 0.5f
private const val DEFAULT_ASPECT = 3f / 4f
private const val MIN_ASPECT = 0.25f
private const val MAX_ASPECT = 4f
private const val MAX_HEIGHT_SHARE = 0.6f
private const val MAX_DECODE_PX = 2048
private const val MAX_ZOOM = 5f
private const val SAME_SHAPE_TOLERANCE = 0.02f
private const val LABEL_SCRIM = 0.55f
private val DIVIDER_WIDTH = 3.dp
private val HANDLE_SIZE = 44.dp
private val CHECKER_CELL = 8.dp
private val CHECKER_LIGHT = Color(0xFFFFFFFF)
private val CHECKER_DARK = Color(0xFFDADCE0)
