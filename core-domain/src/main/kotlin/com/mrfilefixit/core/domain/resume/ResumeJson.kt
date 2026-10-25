package com.mrfilefixit.core.domain.resume

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** JSON form of a [Resume], used for drafts on disk and for the copy embedded in exported DOCX files. */
object ResumeJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(resume: Resume): String = json.encodeToString(Resume.serializer(), resume)

    /** Null when [text] is not a resume (or too large to be one), never an exception. */
    fun decode(text: String): Resume? {
        if (text.length > MAX_CHARS) return null
        return try {
            json.decodeFromString(Resume.serializer(), text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** 2 MB of text is far more than any real resume. */
    private const val MAX_CHARS = 2_000_000
}
