package com.mrfilefixit.feature.image.ui

import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.image.BackgroundFill
import com.mrfilefixit.core.domain.image.BackgroundReplace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackgroundOptionsTest {
    @Test
    fun `white is the default, ready at once`() {
        val options = BackgroundOptions()
        assertTrue(options.isReady)
        assertEquals(BackgroundFill.Solid(BackgroundReplace.WHITE), options.toEffects().background?.fill)
    }

    @Test
    fun `every choice maps to its fill`() {
        assertEquals(
            BackgroundFill.Solid(BackgroundReplace.EXAM_BLUE),
            BackgroundOptions(BackgroundChoice.EXAM_BLUE).fill,
        )
        assertEquals(BackgroundFill.Transparent, BackgroundOptions(BackgroundChoice.TRANSPARENT).fill)
        assertEquals(
            BackgroundFill.Solid(0xFF112233.toInt()),
            BackgroundOptions(BackgroundChoice.COLOR, color = 0x00112233).fill,
        )
        assertEquals(
            BackgroundFill.Picture("content://media/7"),
            BackgroundOptions(BackgroundChoice.PICTURE, pictureUri = "content://media/7").fill,
        )
    }

    @Test
    fun `photo without a picked photo cannot run yet`() {
        val options = BackgroundOptions(BackgroundChoice.PICTURE)
        assertFalse(options.isReady)
        assertTrue(options.toEffects().isNone)
        val state = ImageToolUiState(
            tool = ImageTool.BACKGROUND,
            files = listOf(FileRef("content://a", "a.jpg", "image/jpeg", 1L)),
            options = ImageOptions(background = options),
        )
        assertFalse(state.canRun)
        assertTrue(state.copy(options = ImageOptions(background = BackgroundOptions())).canRun)
    }

    @Test
    fun `edge softness is kept in range`() {
        assertEquals(1f, BackgroundOptions(edge = 3f).toEffects().background?.edge)
        assertEquals(0f, BackgroundOptions(edge = -1f).toEffects().background?.edge)
    }
}
