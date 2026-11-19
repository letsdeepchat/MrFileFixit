package com.mrfilefixit.core.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mrfilefixit.core.ui.components.FullLabelTabRow
import com.mrfilefixit.core.ui.components.TabItem
import com.mrfilefixit.core.ui.theme.MffTheme
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val labels = listOf(
        "Photo",
        "Signature",
        "Left Thumb Impression",
        "Handwritten Declaration",
        "Other Documents",
        "Certificates",
    )

    @Test
    fun tabLabelsAreNeverCutOrWrapped() {
        compose.setContent {
            MffTheme(dynamicColor = false) {
                // Narrower than any phone, so the row has to scroll rather than squeeze its labels.
                Box(Modifier.width(240.dp)) {
                    FullLabelTabRow(labels.mapIndexed { i, label -> TabItem(label, "tab_$i") }, 0, {})
                }
            }
        }
        labels.forEachIndexed { index, label ->
            compose.onNodeWithTag("tab_$index").performScrollTo()
            val node = compose.onNode(hasText(label), useUnmergedTree = true).fetchSemanticsNode()
            val layout = textLayout(label)
            assertEquals(label, 1, layout.lineCount)
            assertFalse(label, layout.isLineEllipsized(0))
            // The label's box is as wide as the whole text, so nothing is clipped.
            val needed = layout.multiParagraph.maxIntrinsicWidth
            assertTrue("$label: $needed > ${node.size.width}", node.size.width + 1f >= needed)
        }
    }

    @Test
    fun pagerAndNavigationStayInStep() {
        var current = -1
        compose.setContent {
            MffTheme(dynamicColor = false) { Tabs(onPage = { current = it }) }
        }
        swipeAcross()
        compose.waitForIdle()
        assertEquals(1, current)
        compose.onNodeWithText("page 1").assertIsDisplayed()
        // The bar drives the pager the other way.
        compose.onNodeWithTag("nav_3").performClick()
        compose.waitForIdle()
        assertEquals(3, current)
        compose.onNodeWithText("page 3").assertIsDisplayed()
    }

    @Test
    fun swipesFromTheSystemEdgeAreLeftForBack() {
        var current = -1
        compose.setContent {
            MffTheme(dynamicColor = false) { Tabs(onPage = { current = it }) }
        }
        compose.onNodeWithTag("main_pager").performTouchInput { swipeLeft(startX = right - 4f, endX = left + 4f) }
        compose.waitForIdle()
        assertEquals(0, current)
    }

    @Test
    fun innerScreensCannotBeSwipedAway() {
        var current = -1
        compose.setContent {
            MffTheme(dynamicColor = false) { Tabs(onPage = { current = it }, innerOnFirst = true) }
        }
        swipeAcross()
        compose.waitForIdle()
        assertEquals(0, current)
    }

    @Test
    fun backAsksBeforeDiscardingUnsavedWork() {
        var left = 0
        compose.setContent {
            MffTheme(dynamicColor = false) {
                rememberGuardedBack(unsaved = true, onBack = { left++ })
                Text("editor")
            }
        }
        pressBack()
        compose.onNodeWithTag("discard_dialog").assertIsDisplayed()
        compose.onNodeWithTag("discard_stay").performClick()
        assertEquals(0, left)
        pressBack()
        compose.onNodeWithTag("discard_leave").performClick()
        assertEquals(1, left)
    }

    @Test
    fun backLeavesAtOnceWhenNothingIsUnsaved() {
        var left = 0
        compose.setContent {
            MffTheme(dynamicColor = false) { rememberGuardedBack(unsaved = false, onBack = { left++ }) }
        }
        pressBack()
        compose.waitForIdle()
        assertEquals(1, left)
        compose.onAllNodesWithTag("discard_dialog").assertCountEquals(0)
    }

    /** A thumb swipe across the middle of the screen, well clear of the edge zones. */
    private fun swipeAcross() {
        compose.onNodeWithTag("main_pager").performTouchInput {
            swipeLeft(startX = centerX + width / 4f, endX = left + width / 8f)
        }
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun textLayout(label: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = compose.onNode(hasText(label), useUnmergedTree = true).fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    @Composable
    private fun Tabs(onPage: (Int) -> Unit, innerOnFirst: Boolean = false) {
        val count = 4
        val pagerState = rememberPagerState { count }
        val trackers = remember { List(count) { InnerScreenTracker() } }
        val scope = rememberCoroutineScope()
        // Read during composition so the test hears about every page change.
        val page = pagerState.currentPage
        SideEffect { onPage(page) }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                SwipeableTabs(pagerState, trackers) { page ->
                    if (page == 0 && innerOnFirst) ReportInnerScreen(true)
                    Box(Modifier.fillMaxSize()) { Text("page $page") }
                }
            }
            Row {
                repeat(count) { i ->
                    Text(
                        "tab $i",
                        Modifier
                            .testTag("nav_$i")
                            .clickable { scope.launch { pagerState.scrollToPage(i) } },
                    )
                }
            }
        }
    }
}
