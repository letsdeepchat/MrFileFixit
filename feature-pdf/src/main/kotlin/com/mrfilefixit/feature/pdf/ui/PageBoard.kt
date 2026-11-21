package com.mrfilefixit.feature.pdf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mrfilefixit.core.domain.pdf.PageSelection
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.components.PageMark
import com.mrfilefixit.core.ui.components.PdfPageStrip
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.feature.pdf.R

/**
 * The picked PDF's pages, marked for the current tool so the choice is visible on the pages themselves:
 * - Split: every page carries the number of the file it goes into; pages no file takes are faded.
 * - Organize: tap a page to pick or drop it; picked pages are outlined and numbered in their new order.
 * - Delete, Rotate, Page images: the pages typed in the pages field are outlined.
 */
@Composable
internal fun InputPageStrip(state: PdfToolUiState, uri: String, actions: PdfToolActions) {
    val count = state.info?.pageCount ?: 0
    val resources = LocalResources.current
    when (state.tool) {
        PdfTool.SPLIT -> {
            val plan = (state.splitPlan as? Result.Success)?.value
            val fileOf = remember(plan, count) { splitFileOfPage(plan, count) }
            val badges = remember(plan, resources) {
                List(plan?.size ?: 0) { fileIndex -> resources.getString(R.string.pdf_split_badge, fileIndex + 1) }
            }
            Text(stringResource(R.string.pdf_split_pages_hint), style = MaterialTheme.typography.bodySmall)
            PdfPageStrip(uri, mark = { index ->
                val badge = badges.getOrNull(fileOf.getOrElse(index) { -1 })
                if (badge == null) PageMark(dimmed = plan != null) else PageMark(badge = badge, picked = true)
            })
        }
        PdfTool.ORGANIZE -> OrganizeBoard(state, uri, count, actions)
        PdfTool.DELETE, PdfTool.ROTATE, PdfTool.TO_IMAGES -> {
            val picked = remember(state.options.pages, count) { typedPages(state.options.pages, count) }
            PdfPageStrip(uri, mark = { index -> PageMark(picked = picked != null && (index + 1) in picked) })
        }
        else -> PdfPageStrip(uri)
    }
}

/** Index of the split file each page goes into (first file wins for overlapping ranges), or -1. */
internal fun splitFileOfPage(plan: List<IntRange>?, pageCount: Int): IntArray {
    val fileOf = IntArray(pageCount) { -1 }
    plan?.forEachIndexed { file, range ->
        range.forEach { page -> if (page in 1..pageCount && fileOf[page - 1] < 0) fileOf[page - 1] = file }
    }
    return fileOf
}

/** The pages typed in the pages field, or null while it is empty or invalid (nothing outlined). */
private fun typedPages(text: String, pageCount: Int): Set<Int>? =
    if (text.isBlank()) null else (PageSelection.parse(text, pageCount) as? Result.Success)?.value?.toSet()

/**
 * Extract and reorder in one place. The board stays on screen after saving, so more pages can be picked or the
 * order changed and saved again without starting over.
 */
@Composable
private fun OrganizeBoard(state: PdfToolUiState, uri: String, count: Int, actions: PdfToolActions) {
    val order = state.organizedPages
    val position = remember(order) { order.withIndex().associate { (i, page) -> page to i } }
    val resources = LocalResources.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Text(stringResource(R.string.pdf_organize_hint), style = MaterialTheme.typography.bodySmall)
        Text(
            pluralStringResource(R.plurals.pdf_organize_picked, order.size, order.size, count),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.testTag("pdf_organize_count"),
        )
        PdfPageStrip(
            uri,
            mark = { index ->
                val at = position[index + 1]
                if (at == null) {
                    PageMark(dimmed = true)
                } else {
                    PageMark(badge = resources.getString(R.string.pdf_organize_badge, at + 1), picked = true)
                }
            },
            onPageClick = { index ->
                actions.onOptions { it.copy(pages = PageSelection.toggle(it.pages, index + 1, count)) }
            },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            OutlinedButton(
                onClick = { actions.onOptions { it.copy(pages = PageSelection.format((1..count).toList())) } },
                modifier = Modifier.heightIn(min = Dimens.MinTouchTarget).testTag("pdf_organize_all"),
            ) { Text(stringResource(R.string.pdf_organize_all)) }
            OutlinedButton(
                onClick = { actions.onOptions { it.copy(pages = "") } },
                modifier = Modifier.heightIn(min = Dimens.MinTouchTarget).testTag("pdf_organize_none"),
            ) { Text(stringResource(R.string.pdf_organize_none)) }
        }
        if (order.isNotEmpty()) OrderList(order, count, actions)
    }
}

/** The new page order, one chip per picked page with move earlier / later and remove. */
@Composable
private fun OrderList(order: List<Int>, count: Int, actions: PdfToolActions) {
    Text(stringResource(R.string.pdf_organize_order), style = MaterialTheme.typography.titleSmall)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        modifier = Modifier.fillMaxWidth(),
    ) {
        order.forEachIndexed { index, page ->
            OutlinedCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            actions.onOptions { it.copy(pages = PageSelection.move(it.pages, index, -1, count)) }
                        },
                        enabled = index > 0,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.pdf_organize_earlier, page),
                        )
                    }
                    Text(stringResource(CoreR.string.preview_page, page), style = MaterialTheme.typography.labelLarge)
                    IconButton(
                        onClick = {
                            actions.onOptions { it.copy(pages = PageSelection.move(it.pages, index, 1, count)) }
                        },
                        enabled = index < order.lastIndex,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            stringResource(R.string.pdf_organize_later, page),
                        )
                    }
                    IconButton(onClick = {
                        actions.onOptions { it.copy(pages = PageSelection.toggle(it.pages, page, count)) }
                    }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.pdf_organize_remove, page))
                    }
                }
            }
        }
    }
}
