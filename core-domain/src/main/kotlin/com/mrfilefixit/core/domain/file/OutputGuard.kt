package com.mrfilefixit.core.domain.file

import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.flatMap
import com.mrfilefixit.core.domain.result.success

/**
 * A job that "succeeds" with an empty file is a failure: the user would otherwise save or share a 0 B file.
 * Every use case that produces files passes its result through here.
 */
fun AppResult<OutputFile>.requireContent(): AppResult<OutputFile> =
    flatMap { file -> if (file.sizeBytes > 0) success(file) else failure(AppError.EmptyOutput) }

/** [requireContent] for jobs with several outputs; an empty list is a failure too. */
fun AppResult<List<OutputFile>>.requireContents(): AppResult<List<OutputFile>> = flatMap { files ->
    if (files.isNotEmpty() && files.all { it.sizeBytes > 0 }) success(files) else failure(AppError.EmptyOutput)
}
