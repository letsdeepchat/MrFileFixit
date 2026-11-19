package com.mrfilefixit.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether a main tab is showing one of its inner screens (a tool, an exam's detail, an editor) rather than its
 * root. Inner screens have a back button, and while one is open the main tabs cannot be swiped, so a swipe
 * never jumps away from work in progress.
 */
@Stable
class InnerScreenTracker {
    private val owners = mutableStateMapOf<Any, Boolean>()

    val isInner: Boolean get() = owners.values.any { it }

    fun set(owner: Any, inner: Boolean) {
        if (owners[owner] != inner) owners[owner] = inner
    }

    fun remove(owner: Any) {
        owners.remove(owner)
    }
}

/** The tracker of the main tab a screen is shown in; null outside the main tabs (pushed screens). */
val LocalInnerScreenTracker = staticCompositionLocalOf<InnerScreenTracker?> { null }

/** Tells the enclosing main tab whether this screen is currently an inner screen. */
@Composable
fun ReportInnerScreen(inner: Boolean) {
    val tracker = LocalInnerScreenTracker.current ?: return
    val owner = remember { Any() }
    SideEffect { tracker.set(owner, inner) }
    DisposableEffect(tracker, owner) { onDispose { tracker.remove(owner) } }
}
