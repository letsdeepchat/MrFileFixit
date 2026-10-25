package com.mrfilefixit.core.processing.convert

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDStream

/**
 * Removes the text objects (BT … ET) from a page's content stream, so the page can be rendered as a
 * background picture behind editable text boxes. Colour and graphics-state operators inside text objects
 * are kept because they stay in effect after ET. Text drawn inside form XObjects is left as it is.
 */
internal object PdfTextRemoval {
    private val KEPT_IN_TEXT = setOf("cs", "CS", "sc", "SC", "scn", "SCN", "g", "G", "rg", "RG", "k", "K", "gs")

    fun removeText(document: PDDocument, page: PDPage) {
        val parser = PDFStreamParser(page)
        val kept = mutableListOf<Any>()
        val pending = mutableListOf<Any>()
        var inText = false
        var token = parser.parseNextToken()
        while (token != null) {
            if (token is Operator) {
                when {
                    token.name == "BT" -> inText = true
                    token.name == "ET" -> inText = false
                    !inText -> {
                        kept.addAll(pending)
                        kept += token
                    }
                    token.name in KEPT_IN_TEXT -> {
                        kept.addAll(pending)
                        kept += token
                    }
                }
                pending.clear()
            } else {
                pending += token
            }
            token = parser.parseNextToken()
        }
        val stream = PDStream(document)
        stream.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(kept) }
        page.setContents(stream)
    }
}
