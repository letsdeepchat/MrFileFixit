package com.mrfilefixit.core.domain.ocr

import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.result.AppResult

/**
 * The writing systems the bundled recognizers read. [AUTO] tries every recognizer on the first page and keeps
 * the one that finds the most text in its own script (see [ScriptDetector]).
 */
enum class OcrScript {
    AUTO,
    LATIN,
    DEVANAGARI,
    CHINESE,
    JAPANESE,
    KOREAN,
    ;

    companion object {
        /** Every script that has its own recognizer, in the order [AUTO] tries them. */
        val recognizable: List<OcrScript> get() = entries - AUTO
    }
}

/** A box in the pixels of the page image the recognizer saw, with the origin at the top left. */
data class PixelBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class OcrLine(val text: String, val box: PixelBox)

/**
 * One recognized page. [sourceIndex] and [pageIndex] point back at the input (a PDF has many pages, an image
 * one), so a searchable PDF can put the text over the right page. [widthPx] and [heightPx] are the size of the
 * upright page image that the line boxes refer to.
 */
data class OcrPage(
    val sourceIndex: Int,
    val pageIndex: Int,
    val widthPx: Int,
    val heightPx: Int,
    val text: String,
    val lines: List<OcrLine> = emptyList(),
)

/** [script] is the script that was used: the one picked when [OcrScript.AUTO] was asked for. */
data class OcrResult(
    val text: String,
    val pageCount: Int,
    val pages: List<OcrPage> = emptyList(),
    val script: OcrScript = OcrScript.LATIN,
)

/** On-device text recognition. Works on images and PDFs (page by page). */
interface OcrProcessor {
    suspend fun recognize(
        sources: List<FileRef>,
        script: OcrScript,
        progress: ProgressReporter = ProgressReporter.NONE,
    ): AppResult<OcrResult>
}
