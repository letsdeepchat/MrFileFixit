package com.mrfilefixit.core.processing.scan

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.mrfilefixit.core.domain.scan.ScanFilter
import com.mrfilefixit.core.domain.scan.ScanFilterMath
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.ensureActive

/**
 * The scan filters on a bitmap, shared by Scan and the image Edit tool. The paper brightness is estimated on
 * a 1/[MAP_SCALE] copy, and the filter is applied row by row so only one extra row buffer is needed.
 */
internal object ScanFiltering {
    private const val MAP_SCALE = 8
    private const val MIN_RADIUS = 3

    /** The paper map's smoothing radius is 1/40 of its short side: wider than ink, narrower than shadows. */
    private const val RADIUS_DIVISOR = 40
    private const val CANCEL_CHECK_ROWS = 64

    /** Returns a new bitmap; [source] is left as it is. */
    suspend fun apply(source: Bitmap, filter: ScanFilter): Bitmap {
        val width = source.width
        val height = source.height
        val mapWidth = max(1, width / MAP_SCALE)
        val mapHeight = max(1, height / MAP_SCALE)
        val luma = IntArray(mapWidth * mapHeight)
        val small = source.scale(mapWidth, mapHeight)
        try {
            small.getPixels(luma, 0, mapWidth, 0, 0, mapWidth, mapHeight)
        } finally {
            if (small !== source) small.recycle()
        }
        for (i in luma.indices) luma[i] = ScanFilterMath.luminance(luma[i])
        val radius = max(MIN_RADIUS, min(mapWidth, mapHeight) / RADIUS_DIVISOR)
        val map = ScanFilterMath.illuminationMap(luma, mapWidth, mapHeight, radius)

        val output = createBitmap(width, height)
        val row = IntArray(width)
        for (y in 0 until height) {
            if (y % CANCEL_CHECK_ROWS == 0) coroutineContext.ensureActive()
            source.getPixels(row, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val background = ScanFilterMath.sampleMap(map, mapWidth, mapHeight, x, y, width, height)
                row[x] = ScanFilterMath.apply(filter, row[x], background)
            }
            output.setPixels(row, 0, width, 0, y, width, 1)
        }
        return output
    }
}
