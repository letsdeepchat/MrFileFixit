package com.mrfilefixit.core.domain.pdf

import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.AppResult

interface PdfProcessor {
    /** Page count and encryption flag. Encrypted files report [PdfInfo.encrypted] instead of failing. */
    suspend fun info(source: FileRef): AppResult<PdfInfo>

    suspend fun run(
        operation: PdfOperation,
        progress: ProgressReporter = ProgressReporter.NONE,
    ): AppResult<List<OutputFile>>
}
