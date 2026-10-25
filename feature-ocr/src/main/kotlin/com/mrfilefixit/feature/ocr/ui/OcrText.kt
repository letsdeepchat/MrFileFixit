package com.mrfilefixit.feature.ocr.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mrfilefixit.core.domain.ocr.OcrExportFormat
import com.mrfilefixit.core.domain.ocr.OcrScript
import com.mrfilefixit.feature.ocr.R

/** Labels for the OCR enums. Exhaustive, so a new script or format cannot ship without its text. */
internal object OcrText {
    @Composable
    fun script(script: OcrScript): String = stringResource(
        when (script) {
            OcrScript.AUTO -> R.string.ocr_script_AUTO
            OcrScript.LATIN -> R.string.ocr_script_LATIN
            OcrScript.DEVANAGARI -> R.string.ocr_script_DEVANAGARI
            OcrScript.CHINESE -> R.string.ocr_script_CHINESE
            OcrScript.JAPANESE -> R.string.ocr_script_JAPANESE
            OcrScript.KOREAN -> R.string.ocr_script_KOREAN
        },
    )

    @Composable
    fun scriptHint(script: OcrScript): String = stringResource(
        when (script) {
            OcrScript.AUTO -> R.string.ocr_script_hint_AUTO
            OcrScript.LATIN -> R.string.ocr_script_hint_LATIN
            OcrScript.DEVANAGARI -> R.string.ocr_script_hint_DEVANAGARI
            OcrScript.CHINESE -> R.string.ocr_script_hint_CHINESE
            OcrScript.JAPANESE -> R.string.ocr_script_hint_JAPANESE
            OcrScript.KOREAN -> R.string.ocr_script_hint_KOREAN
        },
    )

    @Composable
    fun format(format: OcrExportFormat): String = stringResource(
        when (format) {
            OcrExportFormat.SEARCHABLE_PDF -> R.string.ocr_export_SEARCHABLE_PDF
            OcrExportFormat.TXT -> R.string.ocr_export_TXT
            OcrExportFormat.DOCX -> R.string.ocr_export_DOCX
            OcrExportFormat.TEXT_PDF -> R.string.ocr_export_TEXT_PDF
        },
    )
}
