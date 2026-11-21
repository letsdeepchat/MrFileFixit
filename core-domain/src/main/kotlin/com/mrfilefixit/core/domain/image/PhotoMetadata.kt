package com.mrfilefixit.core.domain.image

/**
 * The kinds of photo details "Remove metadata" lets the user pick. Thumbnails, maker notes, XMP blocks and
 * vendor data (such as Samsung's trailer after the picture) are always removed; these groups are what can be
 * kept on purpose.
 */
enum class MetadataGroup {
    LOCATION,
    DEVICE,
    DATE_TIME,
    OTHER,
    ;

    /** EXIF tag names in this group, as ExifInterface spells them. */
    val tags: List<String>
        get() = when (this) {
            LOCATION -> MetadataTags.LOCATION
            DEVICE -> MetadataTags.DEVICE
            DATE_TIME -> MetadataTags.DATE_TIME
            OTHER -> MetadataTags.OTHER
        }

    companion object {
        val ALL: Set<MetadataGroup> = entries.toSet()

        /** EXIF tags to write back into a cleaned photo when [removed] groups go and the rest stay. */
        fun keptTags(removed: Set<MetadataGroup>): List<String> {
            val kept = entries.filterNot { it in removed }.flatMap { it.tags }.distinct()
            // GPS carries its own date and time stamps: they go when the date is removed, even if location stays.
            return if (DATE_TIME in removed) kept - GPS_TIME_TAGS else kept
        }

        private val GPS_TIME_TAGS = setOf("GPSDateStamp", "GPSTimeStamp")
    }
}

/** What a photo carries, read before cleaning it, so the user sees what is there. */
data class MetadataSummary(
    /** "Samsung SM-S918B", or null when the photo does not name a device. */
    val device: String? = null,
    /** When it was taken, as the photo stores it ("2026:11:20 18:04:11"), or null. */
    val takenAt: String? = null,
    val hasLocation: Boolean = false,
    /** Camera settings, comments, author or copyright. */
    val hasOther: Boolean = false,
) {
    val isEmpty: Boolean get() = device == null && takenAt == null && !hasLocation && !hasOther

    /** Which groups this photo actually has. */
    val groups: Set<MetadataGroup>
        get() = buildSet {
            if (hasLocation) add(MetadataGroup.LOCATION)
            if (device != null) add(MetadataGroup.DEVICE)
            if (takenAt != null) add(MetadataGroup.DATE_TIME)
            if (hasOther) add(MetadataGroup.OTHER)
        }

    companion object {
        val NONE = MetadataSummary()

        /** "Samsung SM-S918B" from EXIF Make and Model, without repeating the maker when the model names it. */
        fun deviceName(make: String?, model: String?): String? {
            val m = make?.trim().orEmpty()
            val d = model?.trim().orEmpty()
            return when {
                m.isEmpty() && d.isEmpty() -> null
                m.isEmpty() -> d
                d.isEmpty() -> m
                d.startsWith(m, ignoreCase = true) -> d
                else -> "$m $d"
            }
        }
    }
}

/** EXIF tag names per group (ExifInterface's spelling, from the EXIF 2.32 and TIFF specifications). */
internal object MetadataTags {
    val LOCATION = listOf(
        "GPSVersionID", "GPSLatitudeRef", "GPSLatitude", "GPSLongitudeRef", "GPSLongitude", "GPSAltitudeRef",
        "GPSAltitude", "GPSTimeStamp", "GPSSatellites", "GPSStatus", "GPSMeasureMode", "GPSDOP", "GPSSpeedRef",
        "GPSSpeed", "GPSTrackRef", "GPSTrack", "GPSImgDirectionRef", "GPSImgDirection", "GPSMapDatum",
        "GPSDestLatitudeRef", "GPSDestLatitude", "GPSDestLongitudeRef", "GPSDestLongitude", "GPSDestBearingRef",
        "GPSDestBearing", "GPSDestDistanceRef", "GPSDestDistance", "GPSProcessingMethod", "GPSAreaInformation",
        "GPSDateStamp", "GPSDifferential", "GPSHPositioningError",
    )
    val DEVICE = listOf(
        "Make", "Model", "Software", "LensMake", "LensModel", "LensSerialNumber", "BodySerialNumber",
        "CameraOwnerName", "ImageUniqueID", "LensSpecification",
    )
    val DATE_TIME = listOf(
        "DateTime", "DateTimeOriginal", "DateTimeDigitized", "SubSecTime", "SubSecTimeOriginal",
        "SubSecTimeDigitized", "OffsetTime", "OffsetTimeOriginal", "OffsetTimeDigitized",
    )
    val OTHER = listOf(
        "Artist", "Copyright", "ImageDescription", "UserComment", "ExposureTime", "FNumber", "ExposureProgram",
        "PhotographicSensitivity", "ExposureBiasValue", "MaxApertureValue", "MeteringMode", "LightSource",
        "Flash", "FocalLength", "FocalLengthIn35mmFilm", "WhiteBalance", "ExposureMode", "DigitalZoomRatio",
        "SceneCaptureType", "Contrast", "Saturation", "Sharpness", "ShutterSpeedValue", "ApertureValue",
        "BrightnessValue", "SubjectDistance", "SubjectDistanceRange", "SensingMethod", "SceneType",
    )
}
