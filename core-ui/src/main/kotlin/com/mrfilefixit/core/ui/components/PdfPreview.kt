package com.mrfilefixit.core.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.mrfilefixit.core.ui.R
import com.mrfilefixit.core.ui.theme.Dimens
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One open PDF for previews, rendered with the platform [PdfRenderer] one page at a time (the document is
 * never loaded whole). Rendering and closing are serialised, so a page that is still drawing when the screen
 * goes away is finished before the file is closed.
 */
class PdfPageSource private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {
    private val lock = Any()
    private var closed = false

    val pageCount: Int = renderer.pageCount

    /** Page [index] as a bitmap [widthPx] wide on white, or null when it cannot be drawn. */
    suspend fun render(index: Int, widthPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (closed || index !in 0 until pageCount) return@synchronized null
            try {
                renderer.openPage(index).use { page ->
                    val width = widthPx.coerceIn(1, MAX_RENDER_PX)
                    val height = (width.toFloat() * page.height / page.width).toInt().coerceIn(1, MAX_RENDER_PX * 2)
                    val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.White.toArgb())
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                }
            } catch (_: IllegalStateException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    /** Width / height of page [index], read without drawing it. */
    fun aspect(index: Int): Float? = synchronized(lock) {
        if (closed || index !in 0 until pageCount) return@synchronized null
        try {
            renderer.openPage(index).use { it.width.toFloat() / it.height.coerceAtLeast(1) }
        } catch (_: IllegalStateException) {
            null
        }
    }

    override fun close() = synchronized(lock) {
        if (!closed) {
            closed = true
            renderer.close()
            descriptor.close()
        }
    }

    companion object {
        private const val MAX_RENDER_PX = 2048

        /** Opens a `file://`, `content://` or plain path. Null for damaged, protected or missing files. */
        suspend fun open(context: Context, uri: String): PdfPageSource? = withContext(Dispatchers.IO) {
            var descriptor: ParcelFileDescriptor? = null
            try {
                descriptor = if (uri.startsWith("content:")) {
                    context.contentResolver.openFileDescriptor(uri.toUri(), "r")
                } else {
                    ParcelFileDescriptor.open(File(uri.removePrefix("file://")), ParcelFileDescriptor.MODE_READ_ONLY)
                }
                descriptor?.let { PdfPageSource(it, PdfRenderer(it)) }
            } catch (_: IOException) {
                descriptor?.close()
                null
            } catch (_: SecurityException) {
                descriptor?.close()
                null
            } catch (_: IllegalArgumentException) {
                descriptor?.close()
                null
            }
        }
    }
}

/** Opens [uri] for previews while in composition and closes it when it leaves. */
@Composable
fun rememberPdfPageSource(uri: String): PdfPageSource? {
    val context = LocalContext.current
    val source by produceState<PdfPageSource?>(null, uri) {
        val opened = PdfPageSource.open(context, uri)
        value = opened
        awaitDispose { opened?.close() }
    }
    return source
}

/**
 * A horizontal strip of page thumbnails, so page order, watermarks and page numbers can be checked before
 * saving. Pages render lazily as they scroll into view; tapping one opens it full screen with zoom.
 */
@Composable
fun PdfPageStrip(uri: String, modifier: Modifier = Modifier, pageWidth: Dp = PAGE_WIDTH) {
    val source = rememberPdfPageSource(uri)
    var zoomPage by remember(uri) { mutableStateOf<Int?>(null) }
    Column(modifier.testTag("pdf_page_strip"), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        if (source == null) return@Column
        if (source.pageCount == 0) {
            Text(stringResource(R.string.preview_unavailable), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        Text(
            pluralStringResource(R.plurals.preview_page_count, source.pageCount, source.pageCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            contentPadding = PaddingValues(end = Dimens.SpaceS),
            modifier = Modifier.nestedScroll(KeepHorizontalScroll),
        ) {
            items(source.pageCount) { index ->
                PageThumbnail(source, index, pageWidth, onClick = { zoomPage = index })
            }
        }
    }
    val page = zoomPage
    if (source != null && page != null) PdfPageDialog(source, page, onDismiss = { zoomPage = null })
}

/** The first pages one under the other at full card width, as a document preview (resume, biodata). */
@Composable
fun PdfPagesColumn(uri: String, modifier: Modifier = Modifier, maxPages: Int = DEFAULT_COLUMN_PAGES) {
    val source = rememberPdfPageSource(uri) ?: return
    BoxWithConstraints(modifier) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            repeat(minOf(source.pageCount, maxPages)) { index ->
                val image by produceState<ImageBitmap?>(null, source, index, widthPx) {
                    value = source.render(index, widthPx)
                }
                val aspect = remember(source, index) { source.aspect(index) } ?: A4_ASPECT
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .background(Color.White)
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)),
                ) {
                    image?.let {
                        Image(
                            it,
                            stringResource(R.string.preview_page, index + 1),
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageThumbnail(source: PdfPageSource, index: Int, width: Dp, onClick: () -> Unit) {
    val widthPx = with(LocalDensity.current) { width.roundToPx() } * 2
    val image by produceState<ImageBitmap?>(null, source, index) { value = source.render(index, widthPx) }
    val label = stringResource(R.string.preview_page, index + 1)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier
                .width(width)
                .aspectRatio(remember(source, index) { source.aspect(index) } ?: A4_ASPECT)
                .background(Color.White)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant))
                .clickable(
                    role = Role.Image,
                    onClickLabel = stringResource(R.string.preview_open_full),
                    onClick = onClick,
                )
                .testTag("pdf_page_$index"),
        ) {
            image?.let { Image(it, label, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PdfPageDialog(source: PdfPageSource, index: Int, onDismiss: () -> Unit) {
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() } * 2
    val image by produceState<ImageBitmap?>(null, source, index) { value = source.render(index, widthPx) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                ZoomableContent(Modifier.fillMaxSize()) {
                    image?.let {
                        Image(
                            it,
                            stringResource(R.string.preview_page, index + 1),
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Dimens.SpaceS),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        stringResource(R.string.action_close),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

private const val A4_ASPECT = 595f / 842f
private const val DEFAULT_COLUMN_PAGES = 3
private val PAGE_WIDTH = 120.dp
