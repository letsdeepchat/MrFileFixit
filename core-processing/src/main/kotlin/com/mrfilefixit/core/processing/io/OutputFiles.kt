package com.mrfilefixit.core.processing.io

import com.mrfilefixit.core.domain.security.FilenameSanitizer
import java.io.File

/**
 * Creates a new, unique file in [dir] (an app-private job directory). The name is always sanitized,
 * and the resolved path is checked to stay inside [dir].
 */
internal fun newOutputFile(dir: String, baseName: String, extension: String): File {
    val directory = File(dir).apply { mkdirs() }
    val canonicalDir = directory.canonicalFile
    val first = FilenameSanitizer.withExtension(baseName, extension)
    var candidate = File(canonicalDir, first)
    var index = 1
    while (candidate.exists()) {
        candidate = File(canonicalDir, FilenameSanitizer.withExtension("${first.substringBeforeLast('.')}-$index", extension))
        index++
    }
    check(candidate.canonicalFile.parentFile == canonicalDir) { "Output escaped its job directory" }
    return candidate
}
