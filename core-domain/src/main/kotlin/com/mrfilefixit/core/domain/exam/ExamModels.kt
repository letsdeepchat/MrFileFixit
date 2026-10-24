package com.mrfilefixit.core.domain.exam

import com.mrfilefixit.core.domain.image.ImageFormat

/** Declaration order is the order of the category filter pills. */
enum class ExamCategory { SSC, UPSC, BANKING, RAILWAYS, DEFENCE, ENTRANCE, TEACHING, STATE_PSC, OTHER }

enum class DocumentKind { PHOTO, POSTCARD_PHOTO, SIGNATURE, LEFT_THUMB, DECLARATION, OTHER }

enum class VerificationStatus {
    /** Checked by a maintainer against the official page or notification on [ExamProfile.lastVerified]. */
    VERIFIED,

    /** Collected from public guides and past notices. Must be checked against the official notification. */
    COMMUNITY,
}

enum class CertificateFormat { PDF, JPEG }

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
    /** Background the notice asks for, as written there (e.g. "Plain white"). */
    val background: String? = null,
    /** Ink colour for signatures, thumb impressions and declarations. */
    val ink: String? = null,
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
            return if (minKb == 0) {
                0L..safeMax
            } else if (safeMin < safeMax) {
                safeMin..safeMax
            } else {
                minBytes..maxBytes
            }
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

/**
 * A supporting document (marksheet, category certificate, ID) the portal asks for. The app does not
 * generate these; it shows the limits and sends the user to the PDF tools to compress a scan.
 */
data class CertificateSpec(
    val name: String,
    val formats: Set<CertificateFormat>,
    val minKb: Int,
    val maxKb: Int,
    val notes: String? = null,
) {
    init {
        require(name.isNotBlank()) { "Certificate needs a name" }
        require(formats.isNotEmpty()) { "At least one format" }
        require(minKb in 0..maxKb && maxKb > 0) { "Invalid KB range" }
    }
}

data class ExamProfile(
    val id: String,
    /** Short label used on cards and chips, e.g. "SSC CGL". */
    val shortName: String,
    /** Full exam name, e.g. "Combined Graduate Level Examination". */
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
    val certificates: List<CertificateSpec> = emptyList(),
) {
    init {
        require(documents.isNotEmpty()) { "A profile needs at least one document" }
        require(verification == VerificationStatus.COMMUNITY || lastVerified != null) {
            "A verified profile needs a lastVerified date"
        }
    }

    fun document(kind: DocumentKind): DocumentSpec? = documents.firstOrNull { it.kind == kind }
}
