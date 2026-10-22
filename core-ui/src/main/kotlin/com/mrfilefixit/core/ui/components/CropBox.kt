package com.mrfilefixit.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.ui.R
import com.mrfilefixit.core.ui.thumbnail.ThumbnailCache

/**
 * Fixed-aspect crop frame. The image moves and zooms under the frame (like most ID-photo tools),
 * and [onCropChanged] receives the visible area as a normalised rect of the upright image.
 *
 * The preview is a sampled thumbnail; the crop is applied to the full image by the engine.
 */
@Composable
fun CropBox(
    uri: String,
    imageAspect: Float,
    frameAspect: Float,
    onCropChanged: (NormalizedRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var preview by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) { preview = ThumbnailCache.load(context, uri, PREVIEW_PX)?.asImageBitmap() }

    var zoom by remember(uri, frameAspect) { mutableFloatStateOf(1f) }
    var offset by remember(uri, frameAspect) { mutableStateOf(Offset.Zero) }
    val description = stringResource(R.string.cd_crop_area)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .aspectRatio(frameAspect)
            .clipToBounds()
            .border(2.dp, MaterialTheme.colorScheme.primary)
            .semantics { contentDescription = description },
    ) {
        val frameW = with(LocalDensity.current) { maxWidth.toPx() }
        val frameH = frameW / frameAspect
        // Base scale makes the image just cover the frame (like ContentScale.Crop).
        val coverW = if (imageAspect > frameAspect) frameH * imageAspect else frameW
        val coverH = coverW / imageAspect

        fun clampAndReport() {
            val w = coverW * zoom
            val h = coverH * zoom
            val maxX = (w - frameW) / 2f
            val maxY = (h - frameH) / 2f
            offset = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
            val left = ((w - frameW) / 2f - offset.x) / w
            val top = ((h - frameH) / 2f - offset.y) / h
            onCropChanged(
                NormalizedRect(
                    left.coerceIn(0f, 1f),
                    top.coerceIn(0f, 1f),
                    (left + frameW / w).coerceIn(0f, 1f),
                    (top + frameH / h).coerceIn(0f, 1f),
                ),
            )
        }

        LaunchedEffect(uri, frameAspect, imageAspect) { clampAndReport() }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(uri, frameAspect) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                        offset += pan
                        clampAndReport()
                    }
                },
        ) {
            preview?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            }
        }
    }
}

private const val PREVIEW_PX = 1024
private const val MAX_ZOOM = 5f
