package com.mrfilefixit.core.domain.exam

import com.mrfilefixit.core.domain.image.ImageFormat

enum class ExamCategory { SSC, UPSC, IBPS, RRB, NEET, STATE_PSC, OTHER }

enum class DocumentKind { PHOTO, SIGNATURE, LEFT_THUMB, DECLARATION, OTHER }

enum class VerificationStatus {
    /** Checked by a maintainer against the official notification on [ExamProfile.lastVerified]. */
    VERIFIED,

    /** Sample data. Must be checked against the official notification before use. */
    UNVERIFIED,
}

/** Inclusive range. A null bound means "no limit on that side". */
data class IntBounds(val min: Int?, val max: Int?) {
    init {
        require(min == null || max == null || min <= max) { "min > max" }
    }

    operator fun contains(value: Int): Boolean = (min == null || value >= min) && (max == null || value <= max)

    companion object {
        fun exactly(value: Int) = IntBounds(value, value)
    }
}

/**
 * Requirements for one upload slot (photo, signature, ...).
 *
 * Sizes are in kilobytes as written in notifications. [kilobyteBytes] says how the portal counts a KB.
 * Most use 1024. When unsure, [targetBytes] aims for the overlap of both readings so the file passes
 * either way.
 */
data class DocumentSpec(
    val kind: DocumentKind,
    val formats: Set<ImageFormat>,
    val minKb: Int,
    val maxKb: Int,
    val width: IntBounds,
    val height: IntBounds,
    /** Preferred output size in pixels when the notification gives a range or only centimetres. */
    val targetWidth: Int,
    val targetHeight: Int,
    val kilobyteBytes: Int = KIB,
    val notes: String? = null,
) {
    init {
        require(formats.isNotEmpty()) { "At least one format" }
        require(minKb in 0..maxKb && maxKb > 0) { "Invalid KB range" }
        require(targetWidth in width && targetHeight in height) { "Target size outside allowed bounds" }
    }

    val minBytes: Long get() = minKb.toLong() * kilobyteBytes
    val maxBytes: Long get() = maxKb.toLong() * kilobyteBytes

    /**
     * Byte range to aim for when generating a file: the lower bound read as KiB and the upper bound read
     * as decimal KB, plus a small safety margin, so the output passes on portals that use either unit.
     * Falls back to the declared range when the safe range would be empty.
     */
    val targetBytes: LongRange
        get() {
            val safeMin = minKb.toLong() * KIB + SAFETY_MARGIN_BYTES
            val safeMax = maxKb.toLong() * DECIMAL_KB - SAFETY_MARGIN_BYTES
            return if (minKb == 0) 0L..safeMax else if (safeMin < safeMax) safeMin..safeMax else minBytes..maxBytes
        }

    companion object {
        const val KIB = 1024
        const val DECIMAL_KB = 1000
        const val SAFETY_MARGIN_BYTES = 256L
    }
}

/** Photo above signature in one image, for portals that ask for a combined upload. */
data class CompositeSpec(
    val document: DocumentSpec,
    /** Share of the height given to the photo, 0..1. */
    val photoFraction: Float,
) {
    init {
        require(photoFraction > 0f && photoFraction < 1f) { "photoFraction must be in (0, 1)" }
    }
}

data class ExamProfile(
    val id: String,
    val name: String,
    val category: ExamCategory,
    val conductingBody: String,
    val sourceUrl: String,
    /** ISO-8601 date (yyyy-MM-dd) of the last check against the official notification; null if never checked. */
    val lastVerified: String?,
    val verification: VerificationStatus,
    val documents: List<DocumentSpec>,
    val composite: CompositeSpec? = null,
    val notes: String? = null,
) {
    init {
        require(documents.isNotEmpty()) { "A profile needs at least one document" }
        require(verification == VerificationStatus.UNVERIFIED || lastVerified != null) {
            "A verified profile needs a lastVerified date"
        }
    }

    fun document(kind: DocumentKind): DocumentSpec? = documents.firstOrNull { it.kind == kind }
}
