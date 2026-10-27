package com.mrfilefixit.core.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import com.mrfilefixit.core.ui.preview.FontScalePreviews
import com.mrfilefixit.core.ui.preview.ThemePreviews
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.theme.MffTheme

/** One tab of a [FullLabelTabRow]. [testTag] lets UI tests find it. */
class TabItem(val label: String, val testTag: String? = null)

/**
 * A horizontally scrollable tab row that always shows every label in full on one line (no ellipsis, no
 * wrapping), whatever the screen width or font scale. Tabs size to their text; when they do not fit, the
 * row scrolls with momentum and keeps [Dimens.ScreenPadding] at both ends.
 *
 * Horizontal drags on the row stay in the row: whatever it does not use is consumed here, so swiping the
 * tab bar never switches the app's main tabs behind it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullLabelTabRow(
    tabs: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)),
        edgePadding = Dimens.ScreenPadding,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.nestedScroll(KeepHorizontalScroll),
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                text = { Text(tab.label, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible) },
                modifier = Modifier
                    .heightIn(min = Dimens.MinTouchTarget)
                    .let { m -> tab.testTag?.let { m.testTag(it) } ?: m },
            )
        }
    }
}

/** Consumes the horizontal part of any scroll or fling a child left over, so parents (the main pager) never see it. */
val KeepHorizontalScroll: NestedScrollConnection = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        Offset(available.x, 0f)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = Velocity(available.x, 0f)
}

@ThemePreviews
@FontScalePreviews
@Composable
private fun FullLabelTabRowPreview() {
    MffTheme(dynamicColor = false) {
        FullLabelTabRow(
            tabs = listOf("Photo", "Signature", "Left Thumb Impression", "Handwritten Declaration", "Certificates")
                .map { TabItem(it) },
            selectedIndex = 1,
            onSelect = {},
        )
    }
}
