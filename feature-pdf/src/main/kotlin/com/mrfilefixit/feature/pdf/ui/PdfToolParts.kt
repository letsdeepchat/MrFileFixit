package com.mrfilefixit.feature.pdf.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.domain.pdf.PageMargin
import com.mrfilefixit.core.domain.pdf.PageOrientation
import com.mrfilefixit.core.domain.pdf.PageSize
import com.mrfilefixit.core.domain.pdf.PdfInfo
import com.mrfilefixit.core.domain.pdf.SplitMode
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.components.ChoiceGrid
import com.mrfilefixit.core.ui.components.ErrorCard
import com.mrfilefixit.core.ui.components.GridReorderState
import com.mrfilefixit.core.ui.components.ListReorderState
import com.mrfilefixit.core.ui.components.SectionHeader
import com.mrfilefixit.core.ui.components.SegmentedChoice
import com.mrfilefixit.core.ui.components.gridReorderable
import com.mrfilefixit.core.ui.components.rememberGridReorderState
import com.mrfilefixit.core.ui.components.reorderHandle
import com.mrfilefixit.core.ui.components.reorderableItem
import com.mrfilefixit.core.ui.format.formatBytes
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.thumbnail.Thumbnail
import com.mrfilefixit.feature.pdf.R

/** Merge list: one card per PDF with a drag handle, its page count and size, and a remove button. */
internal fun LazyListScope.mergeFileItems(state: PdfToolUiState, actions: PdfToolActions, reorder: ListReorderState) {
    item(key = "files-header") {
        SectionHeader(
            pluralStringResource(R.plurals.pdf_files_selected, state.files.size, state.files.size),
            Modifier.contentWidth(),
        )
    }
    itemsIndexed(state.files, key = { _, file -> file.uri }) { index, file ->
        val dragging = reorder.draggingKey == file.uri
        MergeFileCard(
            index = index,
            file = file,
            pages = state.pageCounts[file.uri],
            lastIndex = state.files.lastIndex,
            locked = state.job is JobState.Running || state.job is JobState.Success,
            actions = actions,
            reorder = reorder,
            modifier = Modifier
                .contentWidth()
                .then(if (dragging) Modifier else Modifier.animateItem())
                .reorderableItem(reorder, file.uri),
        )
    }
}

@Composable
private fun MergeFileCard(
    index: Int,
    file: FileRef,
    pages: Int?,
    lastIndex: Int,
    locked: Boolean,
    actions: PdfToolActions,
    reorder: ListReorderState,
    modifier: Modifier = Modifier,
) {
    val moveUp = stringResource(CoreR.string.action_move_up)
    val moveDown = stringResource(CoreR.string.action_move_down)
    OutlinedCard(
        shape = RoundedCornerShape(Dimens.ButtonCorner),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.semantics {
            // TalkBack users reorder with actions instead of dragging.
            customActions = moveActions(moveUp, moveDown, index, lastIndex, actions.onMove)
        },
    ) {
        Row(
            Modifier.padding(vertical = Dimens.SpaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(Dimens.MinTouchTarget)
                    .then(if (locked) Modifier else Modifier.reorderHandle(reorder, file.uri)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.DragIndicator, contentDescription = stringResource(CoreR.string.cd_drag_handle))
            }
            OrderBadge(index + 1)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = Dimens.SpaceM),
            ) {
                Text(file.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(
                    pages?.let { pluralStringResource(R.plurals.pdf_pages_count, it, it) },
                    file.sizeBytes?.let { formatBytes(it) },
                ).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!locked) {
                IconButton(onClick = { actions.onRemove(index) }) {
                    Icon(Icons.Filled.Close, stringResource(CoreR.string.action_remove))
                }
            }
        }
    }
}

/** Move up/down for TalkBack, which cannot drag. */
private fun moveActions(
    upLabel: String,
    downLabel: String,
    index: Int,
    lastIndex: Int,
    onMove: (Int, Int) -> Unit,
): List<CustomAccessibilityAction> = listOfNotNull(
    CustomAccessibilityAction(upLabel) {
        onMove(index, index - 1)
        true
    }.takeIf { index > 0 },
    CustomAccessibilityAction(downLabel) {
        onMove(index, index + 1)
        true
    }.takeIf { index < lastIndex },
)

@Composable
private fun OrderBadge(number: Int, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier,
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Dimens.SpaceS, vertical = Dimens.SpaceXs),
        )
    }
}

/** Summary of the single picked PDF: first-page preview, name, pages and size. */
@Composable
internal fun DocumentSummary(file: FileRef, info: PdfInfo?, onChange: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(
        shape = RoundedCornerShape(Dimens.CardCorner),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(Dimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Render only once the file passed the checks and is not encrypted.
            if (info != null && !info.encrypted) {
                Thumbnail(
                    uri = file.uri,
                    contentDescription = stringResource(R.string.cd_first_page),
                    size = SUMMARY_THUMB.dp,
                    modifier = Modifier.clip(RoundedCornerShape(Dimens.SpaceS)),
                    pdf = true,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                Text(file.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                val details = listOfNotNull(
                    info?.let { pluralStringResource(R.plurals.pdf_pages_count, it.pageCount, it.pageCount) },
                    file.sizeBytes?.let { formatBytes(it) },
                ).joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodyMedium)
                if (info?.encrypted == true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, Modifier.size(LOCK_ICON.dp))
                        Text(
                            stringResource(R.string.pdf_encrypted),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = Dimens.SpaceXs),
                        )
                    }
                }
            }
            TextButton(onClick = onChange) { Text(stringResource(CoreR.string.action_change)) }
        }
    }
}

/** Split: method tabs, the matching input, and a live list of the files that will be produced. */
@Composable
internal fun SplitOptions(state: PdfToolUiState, actions: PdfToolActions) {
    val o = state.options
    SectionHeader(stringResource(R.string.pdf_split_method))
    SegmentedChoice(SplitMode.entries, o.splitMode, { splitModeLabel(it) }, { m ->
        actions.onOptions { it.copy(splitMode = m) }
    })
    val plan = state.splitPlan
    val invalid = plan is Result.Failure
    when (o.splitMode) {
        SplitMode.CUSTOM -> PagesField(
            value = o.pages,
            onValue = { v -> actions.onOptions { it.copy(pages = v) } },
            hint = stringResource(R.string.pdf_split_hint),
            isError = invalid && o.pages.isNotBlank(),
        )
        SplitMode.EVERY_N -> OutlinedTextField(
            value = o.everyN,
            onValueChange = { v -> actions.onOptions { it.copy(everyN = v.filter(Char::isDigit).take(MAX_DIGITS)) } },
            label = { Text(stringResource(R.string.pdf_split_every_label)) },
            isError = invalid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        SplitMode.SINGLE_PAGES -> Text(stringResource(R.string.pdf_split_single_note))
    }
    when (plan) {
        is Result.Success -> SplitPreview(plan.value)
        is Result.Failure -> if (o.splitMode != SplitMode.CUSTOM || o.pages.isNotBlank()) {
            Text(
                stringResource(CoreR.string.error_invalid_pages, state.info?.pageCount ?: 0),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        null -> Unit
    }
}

@Composable
private fun SplitPreview(ranges: List<IntRange>) {
    Text(
        pluralStringResource(R.plurals.pdf_split_preview, ranges.size, ranges.size),
        style = MaterialTheme.typography.titleSmall,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        ranges.take(MAX_PREVIEW_RANGES).forEachIndexed { index, range ->
            val pages = if (range.first == range.last) {
                range.first.toString()
            } else {
                stringResource(R.string.pdf_range, range.first, range.last)
            }
            Surface(
                shape = RoundedCornerShape(Dimens.SpaceS),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    stringResource(R.string.pdf_split_file, index + 1, pages),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Dimens.SpaceS, vertical = Dimens.SpaceXs),
                )
            }
        }
        if (ranges.size > MAX_PREVIEW_RANGES) {
            Text(stringResource(R.string.pdf_split_more, ranges.size - MAX_PREVIEW_RANGES))
        }
    }
}

/**
 * Images to PDF: page setup on top (page size as a balanced 2 x 2 grid, orientation and margins as
 * segmented controls), then the pages as a reorderable thumbnail grid with page numbers.
 */
@Composable
internal fun ImagesToPdfContent(
    state: PdfToolUiState,
    actions: PdfToolActions,
    onAddMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val keys = state.files.map { it.uri }
    val reorder = rememberGridReorderState(gridState, keys, actions.onMove)
    val editable = state.job !is JobState.Running && state.job !is JobState.Success
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = GRID_CELL.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        state.inputError?.let { fullSpan("error") { ErrorCard(it, onRetry = null) } }
        if (editable) pageSetup(state, actions)
        fullSpan("pages-header") {
            SectionHeader(
                pluralStringResource(R.plurals.pdf_images_header, state.files.size, state.files.size),
                Modifier.padding(top = Dimens.SpaceS),
            )
        }
        itemsIndexed(state.files, key = { _, file -> file.uri }) { index, file ->
            ImageCell(index, file, state.files.lastIndex, editable, actions, reorder)
        }
        if (editable) {
            fullSpan("add-more") {
                OutlinedButton(
                    onClick = onAddMore,
                    shape = RoundedCornerShape(Dimens.ButtonCorner),
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                ) { Text(stringResource(CoreR.string.action_add_more)) }
            }
        }
        fullSpan("job") { JobSection(state, actions) }
    }
}

private fun LazyGridScope.fullSpan(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.pageSetup(state: PdfToolUiState, actions: PdfToolActions) {
    val o = state.options
    fullSpan("setup") {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            SectionHeader(stringResource(R.string.pdf_page_size))
            ChoiceGrid(PageSize.entries, o.pageSize, { pageSizeLabel(it) }, { p ->
                actions.onOptions { it.copy(pageSize = p) }
            })
            if (o.pageSize != PageSize.FIT_IMAGE) {
                SectionHeader(stringResource(R.string.pdf_orientation))
                SegmentedChoice(PageOrientation.entries, o.orientation, { orientationLabel(it) }, { v ->
                    actions.onOptions { it.copy(orientation = v) }
                })
            }
            SectionHeader(stringResource(R.string.pdf_margins))
            SegmentedChoice(PageMargin.entries, o.margin, { marginLabel(it) }, { m ->
                actions.onOptions { it.copy(margin = m) }
            })
        }
    }
}

@Composable
private fun ImageCell(
    index: Int,
    file: FileRef,
    lastIndex: Int,
    editable: Boolean,
    actions: PdfToolActions,
    reorder: GridReorderState,
) {
    val moveUp = stringResource(CoreR.string.action_move_up)
    val moveDown = stringResource(CoreR.string.action_move_down)
    val pageLabel = stringResource(R.string.pdf_page_number, index + 1)
    BoxWithConstraints(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(Dimens.ButtonCorner))
            .then(if (editable) Modifier.gridReorderable(reorder, file.uri) else Modifier)
            .semantics {
                if (editable) customActions = moveActions(moveUp, moveDown, index, lastIndex, actions.onMove)
            },
    ) {
        Thumbnail(file.uri, contentDescription = pageLabel, size = maxWidth)
        OrderBadge(index + 1, Modifier.align(Alignment.TopStart).padding(Dimens.SpaceXs))
        if (editable) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = SCRIM_ALPHA),
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                IconButton(onClick = { actions.onRemove(index) }) {
                    Icon(Icons.Filled.Close, stringResource(CoreR.string.action_remove))
                }
            }
        }
    }
}

@Composable
private fun splitModeLabel(mode: SplitMode): String = stringResource(
    when (mode) {
        SplitMode.CUSTOM -> R.string.pdf_split_CUSTOM
        SplitMode.EVERY_N -> R.string.pdf_split_EVERY_N
        SplitMode.SINGLE_PAGES -> R.string.pdf_split_SINGLE_PAGES
    },
)

@Composable
private fun orientationLabel(orientation: PageOrientation): String = stringResource(
    when (orientation) {
        PageOrientation.AUTO -> R.string.pdf_orientation_AUTO
        PageOrientation.PORTRAIT -> R.string.pdf_orientation_PORTRAIT
        PageOrientation.LANDSCAPE -> R.string.pdf_orientation_LANDSCAPE
    },
)

@Composable
private fun marginLabel(margin: PageMargin): String = stringResource(
    when (margin) {
        PageMargin.NONE -> R.string.pdf_margin_NONE
        PageMargin.SMALL -> R.string.pdf_margin_SMALL
        PageMargin.BIG -> R.string.pdf_margin_BIG
    },
)

private const val SUMMARY_THUMB = 72
private const val LOCK_ICON = 16
private const val GRID_CELL = 104
private const val MAX_DIGITS = 4
private const val MAX_PREVIEW_RANGES = 12
private const val SCRIM_ALPHA = 0.7f
