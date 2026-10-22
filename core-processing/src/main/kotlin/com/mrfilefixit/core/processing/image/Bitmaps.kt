package com.mrfilefixit.core.processing.image

import android.graphics.Bitmap

/** Recycles the bitmap when [block] finishes, so at most one large bitmap per step stays alive. */
internal inline fun <R> Bitmap.use(block: (Bitmap) -> R): R = try {
    block(this)
} finally {
    if (!isRecycled) recycle()
}
