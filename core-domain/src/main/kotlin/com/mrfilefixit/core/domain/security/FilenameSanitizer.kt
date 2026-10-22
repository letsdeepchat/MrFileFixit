package com.mrfilefixit.core.domain.security

/**
 * Makes user- or document-provided names safe to use as output file names.
 *
 * Removes path separators and control characters (so a name can never escape its directory),
 * characters that are invalid on common file systems, `..` sequences, and leading dots. Keeps Unicode letters,
 * so Hindi names survive. The result is never empty and at most [MAX_LENGTH] characters.
 */
object FilenameSanitizer {
    const val MAX_LENGTH = 100
    const val FALLBACK = "file"

    private val PARENT_DIR = Regex("\\.{2,}")
    private val WHITESPACE = Regex("\\s+")
    private val UNDERSCORES = Regex("_+")
    private val FORBIDDEN = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    private val RESERVED = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    )

    fun sanitize(raw: String, fallback: String = FALLBACK): String {
        val cleaned = buildString(raw.length) {
            for (ch in raw) {
                when {
                    ch.isISOControl() -> Unit
                    ch in FORBIDDEN -> append('_')
                    Character.getType(ch) == Character.FORMAT.toInt() -> Unit // bidi overrides, zero-width chars
                    else -> append(ch)
                }
            }
        }
            .replace(PARENT_DIR, "")
            .replace(WHITESPACE, " ")
            .replace(UNDERSCORES, "_")
            .trim('.', ' ', '_')

        val safe = when {
            cleaned.isEmpty() -> fallback
            cleaned.substringBefore('.').uppercase() in RESERVED -> "_$cleaned"
            else -> cleaned
        }
        return truncateKeepingExtension(safe)
    }

    /** Sanitizes [baseName] (with any extension removed) and appends [extension]. */
    fun withExtension(baseName: String, extension: String): String {
        val base = sanitize(baseName.substringBeforeLast('.', baseName))
        val ext = extension.trimStart('.').filter { it.isLetterOrDigit() }.lowercase()
        val maxBase = MAX_LENGTH - ext.length - 1
        return "${base.take(maxBase).trimEnd('.', ' ').ifEmpty { FALLBACK }}.$ext"
    }

    private fun truncateKeepingExtension(name: String): String {
        if (name.length <= MAX_LENGTH) return name
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0 && name.length - dot <= MAX_EXTENSION_LENGTH) name.substring(dot) else ""
        return name.take(MAX_LENGTH - ext.length).trimEnd('.', ' ') + ext
    }

    private const val MAX_EXTENSION_LENGTH = 6
}
