package com.mrfilefixit.core.domain.ocr

import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.AppResult

enum class OcrScript { LATIN, DEVANAGARI }

data class OcrResult(val text: String, val pageCount: Int)

/** On-device text recognition. Works on images and PDFs (page by page). */
interface OcrProcessor {
    suspend fun recognize(
        sources: List<FileRef>,
        script: OcrScript,
        progress: ProgressReporter = ProgressReporter.NONE,
    ): AppResult<OcrResult>
}
