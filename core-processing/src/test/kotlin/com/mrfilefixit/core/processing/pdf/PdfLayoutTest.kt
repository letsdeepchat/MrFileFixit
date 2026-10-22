package com.mrfilefixit.core.processing.pdf

import com.mrfilefixit.core.domain.pdf.PageSize
import com.mrfilefixit.core.domain.pdf.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PdfLayoutTest {
    @Test
    fun `A4 page turns landscape for landscape images`() {
        assertEquals(PdfLayout.Box(0f, 0f, 595f, 842f), PdfLayout.pageBox(PageSize.A4, 1000, 2000))
        assertEquals(PdfLayout.Box(0f, 0f, 842f, 595f), PdfLayout.pageBox(PageSize.A4, 2000, 1000))
    }

    @Test
    fun `fit keeps aspect ratio and centres inside margins`() {
        val box = PdfLayout.fit(imageW = 1000, imageH = 500, pageW = 600f, pageH = 800f, margin = 50f)
        assertEquals(500f, box.width, 0.01f)
        assertEquals(250f, box.height, 0.01f)
        assertEquals(50f, box.x, 0.01f)
        assertEquals(275f, box.y, 0.01f)
    }

    @Test
    fun `text origin respects position and margin`() {
        assertEquals(24f to 24f, PdfLayout.textOrigin(Position.BOTTOM_LEFT, 40f, 10f, 600f, 800f, 24f))
        assertEquals(280f to 24f, PdfLayout.textOrigin(Position.BOTTOM_CENTER, 40f, 10f, 600f, 800f, 24f))
        assertEquals(536f to 766f, PdfLayout.textOrigin(Position.TOP_RIGHT, 40f, 10f, 600f, 800f, 24f))
    }

    @Test
    fun `page labels and WinAnsi fallback`() {
        assertEquals("Page 3 of 9", PdfLayout.pageLabel("Page {n} of {total}", 3, 9))
        assertEquals("CONFIDENTIAL ??????", PdfLayout.toWinAnsi("CONFIDENTIAL गोपनीय"))
        assertEquals("Café", PdfLayout.toWinAnsi("Café"))
    }
}
