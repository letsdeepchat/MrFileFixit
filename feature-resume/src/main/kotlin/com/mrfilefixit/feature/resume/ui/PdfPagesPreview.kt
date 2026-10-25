package com.mrfilefixit.feature.resume.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.feature.resume.R
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The exported PDF's pages at the width of the card, rendered with the platform [PdfRenderer] from the
 * job file, so the preview is exactly what will be saved. At most [MAX_PAGES] pages are drawn.
 */
@Composable
internal fun PdfPagesPreview(path: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceIn(1, MAX_WIDTH_PX)
        val pages by produceState(emptyList<ImageBitmap>(), path, widthPx) {
            value = withContext(Dispatchers.IO) { renderPages(File(path), widthPx) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            pages.forEachIndexed { index, page ->
                Image(
                    page,
                    contentDescription = stringResource(R.string.resume_preview_page, index + 1),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)),
                )
            }
        }
    }
}

private fun renderPages(file: File, widthPx: Int): List<ImageBitmap> = try {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            (0 until minOf(renderer.pageCount, MAX_PAGES)).map { index ->
                renderer.openPage(index).use { page ->
                    val height = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bitmap = createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                }
            }
        }
    }
} catch (_: IOException) {
    emptyList()
} catch (_: SecurityException) {
    emptyList()
}

private const val MAX_PAGES = 3
private const val MAX_WIDTH_PX = 1400
