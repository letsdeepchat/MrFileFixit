package com.mrfilefixit.core.processing.image

import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File

/** Writes chosen EXIF tags from an original photo into its cleaned copy. */
internal object ExifRewrite {
    /**
     * Copies [tags] (plus the orientation) from the photo in [original] into [cleaned], which must be a JPEG or
     * PNG with no metadata left. Writes nothing when the original has none of them.
     */
    fun copyTags(original: ByteArray, cleaned: File, tags: List<String>) {
        val source = ExifInterface(ByteArrayInputStream(original))
        val values = (tags + ExifInterface.TAG_ORIENTATION).distinct()
            .mapNotNull { tag -> source.getAttribute(tag)?.let { tag to it } }
            .filterNot { (tag, value) -> tag == ExifInterface.TAG_ORIENTATION && value == UPRIGHT }
        if (values.isEmpty()) return
        val target = ExifInterface(cleaned)
        values.forEach { (tag, value) -> target.setAttribute(tag, value) }
        target.saveAttributes()
    }

    private const val UPRIGHT = "1"
}
