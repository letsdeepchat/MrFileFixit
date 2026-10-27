package com.mrfilefixit.core.ui.navigation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The main tabs side by side, swipeable like WhatsApp's: a horizontal swipe across the middle of a root screen
 * moves to the next or previous tab, and [pagerState] stays in step with the navigation bar both ways.
 *
 * - Swiping is off while the current tab shows an inner screen (see [ReportInnerScreen]).
 * - A drag that starts within [systemEdge] of the left or right edge is left to Android's back gesture.
 * - Child scrollables (tab rows, sliders, crop frames) handle their own drags first; tab rows also swallow the
 *   rest of their drag (see `KeepHorizontalScroll`), so they never switch tabs.
 *
 * Pages keep their state (scroll positions, open screens) while off screen.
 */
@Composable
fun SwipeableTabs(
    pagerState: PagerState,
    trackers: List<InnerScreenTracker>,
    modifier: Modifier = Modifier,
    systemEdge: Dp = SYSTEM_EDGE,
    page: @Composable (Int) -> Unit,
) {
    val edgePx = with(LocalDensity.current) { systemEdge.toPx() }
    var edgeDrag by remember { mutableStateOf(false) }
    val currentInner = trackers.getOrNull(pagerState.currentPage)?.isInner == true
    HorizontalPager(
        state = pagerState,
        userScrollEnabled = !currentInner && !edgeDrag,
        beyondViewportPageCount = 0,
        key = { it },
        modifier = modifier
            .fillMaxSize()
            .testTag("main_pager")
            .pointerInput(edgePx) {
                // Watches touches without consuming them, before the pager sees them.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    edgeDrag = SwipePolicy.isSystemEdge(down.position.x, size.width.toFloat(), edgePx)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.pressed })
                    edgeDrag = false
                }
            },
    ) { index ->
        CompositionLocalProvider(LocalInnerScreenTracker provides trackers[index]) { page(index) }
    }
}

/** Pure rules behind [SwipeableTabs], unit-tested. */
object SwipePolicy {
    /** True when [x] is inside the system back-gesture zone at either edge of a [width]-wide screen. */
    fun isSystemEdge(x: Float, width: Float, edge: Float): Boolean = x < edge || x > width - edge

    /**
     * What Back does on the main tabs: an inner screen handles it itself; another root tab goes to the first
     * tab (like WhatsApp); on the first tab the app closes. Returns the page to go to, or null to let it through.
     */
    fun backTarget(currentPage: Int, currentIsInner: Boolean, homePage: Int = 0): Int? =
        if (currentIsInner || currentPage == homePage) null else homePage
}

/** Android's back gesture zone is about 20 to 24 dp wide; staying out of it keeps both gestures reliable. */
val SYSTEM_EDGE: Dp = 24.dp
