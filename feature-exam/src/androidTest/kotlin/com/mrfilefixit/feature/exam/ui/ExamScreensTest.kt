package com.mrfilefixit.feature.exam.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mrfilefixit.core.domain.exam.SpecCheck
import com.mrfilefixit.core.domain.exam.SpecReport
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.ImageInfo
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.theme.MffTheme
import com.mrfilefixit.feature.exam.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExamScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun pickerShowsDisclaimerAndSelectsExam() {
        var selected: String? = null
        compose.setContent {
            MffTheme(dynamicColor = false) {
                ExamPickerScreen(ExamPickerUiState.Loaded(PreviewProfiles), null, { selected = it }, {})
            }
        }
        compose.onNodeWithText(context.getString(CoreR.string.disclaimer_title)).assertIsDisplayed()
        compose.onNodeWithText("IBPS Probationary Officer (PO)").performClick()
        assertEquals("ibps-po", selected)
    }

    @Test
    fun unverifiedProfilesAreLabelled() {
        compose.setContent {
            MffTheme(dynamicColor = false) {
                ExamPickerScreen(ExamPickerUiState.Loaded(PreviewProfiles), null, {}, {})
            }
        }
        val label = context.getString(CoreR.string.verification_unverified)
        assertEquals(PreviewProfiles.size, compose.onAllNodesWithText(label).fetchSemanticsNodes().size)
    }

    @Test
    fun flowShowsRequirementsAndFailingChecks() {
        val profile = PreviewProfiles.first()
        val photo = profile.documents.first()
        val report = SpecReport(
            listOf(
                SpecCheck.Format(ImageFormat.JPEG, setOf(ImageFormat.JPEG)),
                SpecCheck.FileSize(3_000_000, photo.minBytes, photo.maxBytes),
                SpecCheck.Width(4000, photo.width),
                SpecCheck.Height(3000, photo.height),
            ),
        )
        val state = ExamFlowUiState(
            loading = false,
            profile = profile,
            slots = mapOf(
                SlotKey.Document(photo.kind) to SlotUiState(
                    spec = photo,
                    source = FileRef("content://test/1", "a.jpg", "image/jpeg", 3_000_000),
                    sourceInfo = ImageInfo(4000, 3000, ImageFormat.JPEG, 3_000_000),
                    sourceReport = report,
                ),
            ),
        )
        var fixed = false
        compose.setContent {
            MffTheme(dynamicColor = false) {
                ExamFlowScreen(
                    state,
                    object : ExamFlowActions {
                        override fun onPick(key: SlotKey, uri: String) = Unit
                        override fun onCrop(key: SlotKey, rect: NormalizedRect) = Unit
                        override fun onRotate(key: SlotKey) = Unit
                        override fun onFix(key: SlotKey) {
                            fixed = true
                        }
                        override fun onCancel(key: SlotKey) = Unit
                        override fun onReset(key: SlotKey) = Unit
                        override fun onRevealed(key: SlotKey) = Unit
                        override fun onSave(key: SlotKey, destination: String) = Unit
                        override fun onShare(key: SlotKey) = Unit
                        override fun onMakeComposite() = Unit
                        override fun onRetryLoad() = Unit
                    },
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.exam_your_file)).assertIsDisplayed()
        // Each checklist row is a single TalkBack node whose description carries the verdict.
        compose.onAllNodesWithContentDescription(context.getString(CoreR.string.check_failed), substring = true)
            .fetchSemanticsNodes().let { assertEquals(3, it.size) }
        // The crop box pushes the actions below the fold on small screens (the CI emulator is 320x640 dp).
        compose.onNodeWithText(context.getString(R.string.exam_fix_button)).performScrollTo().performClick()
        assertEquals(true, fixed)
    }
}
