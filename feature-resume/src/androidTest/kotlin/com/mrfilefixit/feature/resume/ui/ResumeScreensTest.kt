package com.mrfilefixit.feature.resume.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mrfilefixit.core.domain.convert.DocumentFormat
import com.mrfilefixit.core.domain.resume.Resume
import com.mrfilefixit.core.domain.resume.ResumeFactory
import com.mrfilefixit.core.domain.resume.ResumeTemplate
import com.mrfilefixit.core.domain.resume.ResumeTemplates
import com.mrfilefixit.core.domain.resume.SectionKind
import com.mrfilefixit.core.ui.theme.MffTheme
import com.mrfilefixit.feature.resume.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResumeScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun draft(): Resume {
        var n = 0
        return ResumeFactory.fromTemplate(ResumeTemplates.byId("SOFTWARE"), "r", 0L) { "e${n++}" }
    }

    @Test
    fun galleryOffersAllFifteenTemplates() {
        var chosen: ResumeTemplate? = null
        compose.setContent {
            MffTheme(
                dynamicColor = false,
            ) { TemplateGalleryScreen(current = null, onChoose = { chosen = it }, onBack = {}) }
        }
        compose.onNodeWithTag("resume_templates").assertIsDisplayed()
        val grid = compose.onNodeWithTag("resume_templates_grid")
        grid.performScrollToNode(hasTestTag("template_academic_cv"))
        compose.onNodeWithTag("template_academic_cv").performClick()
        assertEquals("ACADEMIC_CV", chosen?.id)
    }

    @Test
    fun editorAddsSectionsAndExports() {
        var resume = draft()
        var exported: DocumentFormat? = null
        val actions = ResumeEditorActions(
            onBack = {},
            onEdit = { transform -> resume = transform(resume) },
            onChangeTemplate = {},
            onPhoto = {},
            onExport = { exported = it },
            onCancelExport = {},
            onSave = {},
            onShare = {},
            newId = { "new" + resume.sections.size },
        )
        compose.setContent {
            MffTheme(dynamicColor = false) {
                ResumeEditorScreen(ResumeUiState(page = ResumePage.EDITOR, resume = draft()), actions)
            }
        }
        compose.onNodeWithText(context.getString(R.string.resume_editor_title)).assertIsDisplayed()
        compose.onNodeWithTag("resume_editor_list").performScrollToNode(hasTestTag("resume_add_custom"))
        compose.onNodeWithTag("resume_add_custom").performClick()
        assertEquals(SectionKind.CUSTOM, resume.sections.last().kind)
        compose.onNodeWithTag("resume_export_pdf").performClick()
        assertEquals(DocumentFormat.PDF, exported)
    }
}
