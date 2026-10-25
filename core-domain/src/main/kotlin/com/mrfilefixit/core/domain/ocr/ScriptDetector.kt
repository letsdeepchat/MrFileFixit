package com.mrfilefixit.core.domain.ocr

import java.lang.Character.UnicodeScript

/**
 * Picks the script for [OcrScript.AUTO] from what each recognizer read on the same page.
 *
 * Every non-Latin recognizer also reads Latin text, so a recognizer only wins when it finds a fair amount of
 * text in its own script: at least [MIN_NATIVE] characters, and at least [NATIVE_SHARE] of the Latin letters the
 * Latin recognizer found. The Chinese and Japanese models both read Han characters; Japanese wins only when its
 * text also has kana, which Chinese never uses.
 */
object ScriptDetector {
    const val MIN_NATIVE = 6
    const val NATIVE_SHARE = 0.25f
    const val MIN_KANA_SHARE = 0.1f

    fun pick(samples: Map<OcrScript, String>): OcrScript {
        val latin = samples[OcrScript.LATIN]?.let { count(it, UnicodeScript.LATIN) } ?: 0
        val japanese = samples[OcrScript.JAPANESE].orEmpty()
        val kana = count(japanese, UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA)
        val japaneseNative = kana + count(japanese, UnicodeScript.HAN)
        val scores = buildMap {
            samples[OcrScript.DEVANAGARI]?.let { put(OcrScript.DEVANAGARI, count(it, UnicodeScript.DEVANAGARI)) }
            samples[OcrScript.KOREAN]?.let { put(OcrScript.KOREAN, count(it, UnicodeScript.HANGUL)) }
            val isJapanese = japaneseNative > 0 && kana >= japaneseNative * MIN_KANA_SHARE
            if (isJapanese) {
                put(OcrScript.JAPANESE, japaneseNative)
            } else {
                samples[OcrScript.CHINESE]?.let { put(OcrScript.CHINESE, count(it, UnicodeScript.HAN)) }
            }
        }
        val best = scores.maxByOrNull { it.value } ?: return OcrScript.LATIN
        val enough = best.value >= MIN_NATIVE && best.value >= latin * NATIVE_SHARE
        return if (enough) best.key else OcrScript.LATIN
    }

    private fun count(text: String, vararg scripts: UnicodeScript): Int {
        var total = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (Character.isLetter(codePoint) && UnicodeScript.of(codePoint) in scripts) total++
            index += Character.charCount(codePoint)
        }
        return total
    }
}
