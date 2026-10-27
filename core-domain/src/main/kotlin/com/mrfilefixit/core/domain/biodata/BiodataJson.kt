package com.mrfilefixit.core.domain.biodata

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** JSON form of a [Biodata] draft on disk. */
object BiodataJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(biodata: Biodata): String = json.encodeToString(Biodata.serializer(), biodata)

    /** Null when [text] is not a biodata draft (or too large to be one), never an exception. */
    fun decode(text: String): Biodata? {
        if (text.length > MAX_CHARS) return null
        return try {
            json.decodeFromString(Biodata.serializer(), text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private const val MAX_CHARS = 1_000_000
}
