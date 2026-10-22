package com.mrfilefixit.core.ui.thumbnail

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Small in-memory thumbnail cache (1/8 of the heap). Keys include the requested size so a list and a
 * detail pane can share it. Decoding is sampled and bounded; full-resolution bitmaps are never created.
 */
object ThumbnailCache {
    private const val HEAP_FRACTION = 8
    private const val KIB = 1024
    private const val MAX_SOURCE_SIDE = 16_384

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / KIB / HEAP_FRACTION).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / KIB
    }

    fun get(uri: String, sizePx: Int): Bitmap? = cache.get(key(uri, sizePx))

    fun clear() = cache.evictAll()

    suspend fun load(context: Context, uri: String, sizePx: Int): Bitmap? {
        get(uri, sizePx)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) { decode(context, Uri.parse(uri), sizePx) } ?: return null
        cache.put(key(uri, sizePx), bitmap)
        return bitmap
    }

    private fun key(uri: String, sizePx: Int) = "$sizePx|$uri"

    @Suppress("ReturnCount")
    private fun decode(context: Context, uri: Uri, sizePx: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == ContentResolver.SCHEME_CONTENT) {
            context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
        } else {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth !in 1..MAX_SOURCE_SIDE || bounds.outHeight !in 1..MAX_SOURCE_SIDE) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
            val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
            if (rotation == 0) {
                decoded
            } else {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
                    .also { if (it !== decoded) decoded.recycle() }
            }
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}

@Composable
fun Thumbnail(
    uri: String,
    contentDescription: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val image: ImageBitmap? by produceState(ThumbnailCache.get(uri, sizePx)?.asImageBitmap(), uri, sizePx) {
        value = ThumbnailCache.load(context, uri, sizePx)?.asImageBitmap()
    }
    Box(modifier.size(size)) {
        val loaded = image
        if (loaded != null) {
            Image(loaded, contentDescription, Modifier.size(size), contentScale = contentScale)
        } else {
            Surface(Modifier.size(size), color = MaterialTheme.colorScheme.surfaceVariant) {}
        }
    }
}
