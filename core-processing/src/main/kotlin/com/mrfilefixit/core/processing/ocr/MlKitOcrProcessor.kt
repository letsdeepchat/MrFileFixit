package com.mrfilefixit.core.processing.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.job.ProgressReporter
import com.mrfilefixit.core.domain.job.step
import com.mrfilefixit.core.domain.ocr.OcrLine
import com.mrfilefixit.core.domain.ocr.OcrPage
import com.mrfilefixit.core.domain.ocr.OcrProcessor
import com.mrfilefixit.core.domain.ocr.OcrResult
import com.mrfilefixit.core.domain.ocr.OcrScript
import com.mrfilefixit.core.domain.ocr.PixelBox
import com.mrfilefixit.core.domain.ocr.ScriptDetector
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.runCatchingAppFlat
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.processing.io.mapEngineError
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * On-device OCR with ML Kit's bundled models (no model download, works offline): Latin, Devanagari, Chinese,
 * Japanese and Korean. [OcrScript.AUTO] runs every recognizer on the first page, keeps the one
 * [ScriptDetector] picks and reads the remaining pages with it alone (a blank first page defers the choice).
 */
internal class MlKitOcrProcessor @Inject constructor(
    private val pages: OcrPageImages,
    private val dispatchers: DispatcherProvider,
) : OcrProcessor {

    override suspend fun recognize(
        sources: List<FileRef>,
        script: OcrScript,
        progress: ProgressReporter,
    ): AppResult<OcrResult> = withContext(dispatchers.default) {
        runCatchingAppFlat(::mapEngineError) {
            if (sources.isEmpty()) return@runCatchingAppFlat failure(AppError.EmptyFile)
            val recognizers = Recognizers(script)
            try {
                val read = mutableListOf<OcrPage>()
                sources.forEachIndexed { sourceIndex, source ->
                    coroutineContext.ensureActive()
                    read += pages.forEachPage(source) { page ->
                        coroutineContext.ensureActive()
                        val text = recognizers.read(page.bitmap)
                        progress.step(sourceIndex, sources.size, (page.index + 1f) / page.count)
                        text.toPage(sourceIndex, page)
                    }
                }
                val text = read.joinToString(PAGE_SEPARATOR) { it.text }.trim()
                success(OcrResult(text, read.size, read, recognizers.chosen ?: OcrScript.LATIN))
            } finally {
                recognizers.close()
            }
        }
    }

    /** The recognizer for the asked script, or for [OcrScript.AUTO] the one chosen on the first page. */
    private class Recognizers(asked: OcrScript) {
        private val open = mutableMapOf<OcrScript, TextRecognizer>()
        var chosen: OcrScript? = asked.takeIf { it != OcrScript.AUTO }
            private set

        suspend fun read(bitmap: Bitmap): Text {
            chosen?.let { return process(it, bitmap) }
            val samples = OcrScript.recognizable.associateWith { process(it, bitmap) }
            // A blank first page (a cover, a separator) says nothing; decide on the next page instead.
            if (samples.values.all { it.text.isBlank() }) return samples.getValue(OcrScript.LATIN)
            val pick = ScriptDetector.pick(samples.mapValues { it.value.text })
            chosen = pick
            // Only the chosen recognizer is needed for the remaining pages.
            (open - pick).values.forEach { it.close() }
            open.keys.retainAll(setOf(pick))
            return samples.getValue(pick)
        }

        private suspend fun process(script: OcrScript, bitmap: Bitmap): Text =
            open.getOrPut(script) { client(script) }.process(InputImage.fromBitmap(bitmap, 0)).await()

        fun close() = open.values.forEach { it.close() }

        private fun client(script: OcrScript): TextRecognizer = when (script) {
            OcrScript.LATIN, OcrScript.AUTO -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            OcrScript.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            OcrScript.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            OcrScript.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            OcrScript.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        }
    }

    private companion object {
        const val PAGE_SEPARATOR = "\n\n"
        const val BLOCK_SEPARATOR = "\n\n"

        /** Blocks separated by a blank line, lines by a newline; line boxes for the searchable PDF. */
        fun Text.toPage(sourceIndex: Int, page: PageImage): OcrPage {
            val lines = textBlocks.flatMap { block ->
                block.lines.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    OcrLine(line.text, PixelBox(box.left, box.top, box.right, box.bottom))
                }
            }
            val text = textBlocks.joinToString(BLOCK_SEPARATOR) { block -> block.lines.joinToString("\n") { it.text } }
            return OcrPage(sourceIndex, page.index, page.bitmap.width, page.bitmap.height, text.trim(), lines)
        }
    }
}
