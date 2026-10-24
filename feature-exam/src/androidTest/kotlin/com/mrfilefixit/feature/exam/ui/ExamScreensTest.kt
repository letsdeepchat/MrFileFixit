package com.mrfilefixit.feature.exam.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mrfilefixit.core.domain.exam.CertificateSpec
import com.mrfilefixit.core.domain.exam.DocumentKind
import com.mrfilefixit.core.domain.exam.SpecCheck
import com.mrfilefixit.core.domain.exam.SpecReport
import com.mrfilefixit.core.domain.exam.VerificationStatus
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.ImageInfo
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.format.formatIsoDate
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

    private fun catalogActions(onSelect: (String) -> Unit = {}) = ExamCatalogActions(onSelect, {}, {}, {}, {})

    @Test
    fun catalogShowsDisclaimerAndSelectsExam() {
        var selected: String? = null
        compose.setContent {
            MffTheme(dynamicColor = false) {
                ExamCatalogScreen(ExamPickerUiState.Loaded(PreviewProfiles), null, catalogActions { selected = it })
            }
        }
        compose.onNodeWithText(context.getString(CoreR.string.disclaimer_title)).assertIsDisplayed()
        compose.onNodeWithTag("exam_list").performScrollToNode(hasTestTag("exam_card_ibps-po"))
        compose.onNodeWithTag("exam_card_ibps-po").performClick()
        assertEquals("ibps-po", selected)
    }

    @Test
    fun onlyCheckedProfilesClaimTheOfficialPage() {
        val gate = PreviewProfiles.single { it.verification == VerificationStatus.VERIFIED }
        var verifiedLabel = ""
        compose.setContent {
            val date = formatIsoDate(gate.lastVerified.orEmpty())
            verifiedLabel = stringResource(CoreR.string.verification_verified, date)
            MffTheme(dynamicColor = false) {
                ExamCatalogScreen(ExamPickerUiState.Loaded(PreviewProfiles), null, catalogActions())
            }
        }
        val community = context.getString(CoreR.string.verification_community)
        PreviewProfiles.forEach { profile ->
            val card = "exam_card_${profile.id}"
            compose.onNodeWithTag("exam_list").performScrollToNode(hasTestTag(card))
            if (profile.verification == VerificationStatus.VERIFIED) {
                compose.onNodeWithTag(card).assert(hasText(verifiedLabel)).assert(hasText(community).not())
            } else {
                compose.onNodeWithTag(card).assert(hasText(community)).assert(hasText(verifiedLabel).not())
            }
        }
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
                        override fun onPrepareInImageTools(kind: DocumentKind) = Unit
                        override fun onCompressPdf(certificate: CertificateSpec) = Unit
                    },
                )
            }
        }
        val yourFile = context.getString(R.string.exam_your_file)
        compose.onNodeWithTag("exam_detail").performScrollToNode(hasText(yourFile))
        compose.onNodeWithText(yourFile).assertIsDisplayed()
        // Each checklist row is a single TalkBack node whose description carries the verdict.
        compose.onAllNodesWithContentDescription(context.getString(CoreR.string.check_failed), substring = true)
            .fetchSemanticsNodes().let { assertEquals(3, it.size) }
        // The crop box pushes the actions below the fold on small screens (the CI emulator is 320x640 dp).
        val fix = context.getString(R.string.exam_fix_button)
        compose.onNodeWithTag("exam_detail").performScrollToNode(hasText(fix))
        compose.onNodeWithText(fix).performClick()
        assertEquals(true, fixed)
    }
}
