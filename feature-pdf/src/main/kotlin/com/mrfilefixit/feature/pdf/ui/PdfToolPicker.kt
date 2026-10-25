package com.mrfilefixit.feature.pdf.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CoPresent
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mrfilefixit.core.ui.components.IconBadge
import com.mrfilefixit.core.ui.components.ToolTopAppBar
import com.mrfilefixit.core.ui.preview.AllPreviews
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.theme.MffTheme
import com.mrfilefixit.feature.pdf.R

/** "Pick a tool": tools grouped by job (Organize, Convert, Optimize, Security) in an adaptive grid. */
@Composable
internal fun PdfToolPicker(selected: PdfTool?, onSelect: (PdfTool) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        ToolTopAppBar(stringResource(R.string.pdf_title))
        LazyVerticalGrid(
            // Two columns on phones, more on wide panes.
            columns = GridCells.Adaptive(minSize = TILE_MIN_WIDTH.dp),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            modifier = Modifier.weight(1f),
        ) {
            PdfToolGroup.entries.forEach { group ->
                item(key = "header-$group", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        groupLabel(group),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = Dimens.SpaceS)
                            .semantics { heading() },
                    )
                }
                items(PdfTool.entries.filter { it.group == group }, key = { it.name }) { tool ->
                    ToolTile(tool, selected = tool == selected, onClick = { onSelect(tool) })
                }
            }
        }
    }
}

@Composable
private fun ToolTile(tool: PdfTool, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(Dimens.CardCorner),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TILE_MIN_HEIGHT.dp)
            .semantics { this.selected = selected }
            .testTag("pdf_tool_${tool.name.lowercase()}"),
    ) {
        Column(Modifier.padding(Dimens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            IconBadge(toolIcon(tool))
            Text(toolLabel(tool), style = MaterialTheme.typography.titleSmall)
            Text(
                toolDescription(tool),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

internal fun toolIcon(tool: PdfTool): ImageVector = when (tool) {
    PdfTool.MERGE -> Icons.AutoMirrored.Filled.MergeType
    PdfTool.SPLIT -> Icons.AutoMirrored.Filled.CallSplit
    PdfTool.EXTRACT -> Icons.Filled.ContentCut
    PdfTool.REORDER -> Icons.Filled.SwapVert
    PdfTool.ROTATE -> Icons.AutoMirrored.Filled.RotateRight
    PdfTool.DELETE -> Icons.Filled.DeleteSweep
    PdfTool.IMAGES_TO_PDF -> Icons.Filled.AddPhotoAlternate
    PdfTool.TO_IMAGES -> Icons.Filled.Image
    PdfTool.COMPRESS -> Icons.Filled.Compress
    PdfTool.WATERMARK -> Icons.AutoMirrored.Filled.BrandingWatermark
    PdfTool.PAGE_NUMBERS -> Icons.Filled.FormatListNumbered
    PdfTool.PROTECT -> Icons.Filled.Lock
    PdfTool.UNLOCK -> Icons.Filled.LockOpen
    PdfTool.WORD_TO_PDF -> Icons.Filled.Description
    PdfTool.PDF_TO_WORD -> Icons.Filled.EditNote
    PdfTool.PPT_TO_PDF -> Icons.Filled.Slideshow
    PdfTool.PDF_TO_PPT -> Icons.Filled.CoPresent
    PdfTool.EXCEL_TO_PDF -> Icons.Filled.TableChart
    PdfTool.PDF_TO_EXCEL -> Icons.Filled.GridOn
}

@Composable
private fun groupLabel(group: PdfToolGroup): String = stringResource(
    when (group) {
        PdfToolGroup.ORGANIZE -> R.string.pdf_group_ORGANIZE
        PdfToolGroup.CONVERT -> R.string.pdf_group_CONVERT
        PdfToolGroup.OPTIMIZE -> R.string.pdf_group_OPTIMIZE
        PdfToolGroup.SECURITY -> R.string.pdf_group_SECURITY
    },
)

@Composable
private fun toolDescription(tool: PdfTool): String = stringResource(
    when (tool) {
        PdfTool.MERGE -> R.string.pdf_desc_MERGE
        PdfTool.SPLIT -> R.string.pdf_desc_SPLIT
        PdfTool.EXTRACT -> R.string.pdf_desc_EXTRACT
        PdfTool.REORDER -> R.string.pdf_desc_REORDER
        PdfTool.ROTATE -> R.string.pdf_desc_ROTATE
        PdfTool.DELETE -> R.string.pdf_desc_DELETE
        PdfTool.IMAGES_TO_PDF -> R.string.pdf_desc_IMAGES_TO_PDF
        PdfTool.TO_IMAGES -> R.string.pdf_desc_TO_IMAGES
        PdfTool.COMPRESS -> R.string.pdf_desc_COMPRESS
        PdfTool.WATERMARK -> R.string.pdf_desc_WATERMARK
        PdfTool.PAGE_NUMBERS -> R.string.pdf_desc_PAGE_NUMBERS
        PdfTool.PROTECT -> R.string.pdf_desc_PROTECT
        PdfTool.UNLOCK -> R.string.pdf_desc_UNLOCK
        PdfTool.WORD_TO_PDF -> R.string.pdf_desc_WORD_TO_PDF
        PdfTool.PDF_TO_WORD -> R.string.pdf_desc_PDF_TO_WORD
        PdfTool.PPT_TO_PDF -> R.string.pdf_desc_PPT_TO_PDF
        PdfTool.PDF_TO_PPT -> R.string.pdf_desc_PDF_TO_PPT
        PdfTool.EXCEL_TO_PDF -> R.string.pdf_desc_EXCEL_TO_PDF
        PdfTool.PDF_TO_EXCEL -> R.string.pdf_desc_PDF_TO_EXCEL
    },
)

private const val TILE_MIN_WIDTH = 150
private const val TILE_MIN_HEIGHT = 132

@AllPreviews
@Composable
private fun PdfToolPickerPreview() {
    MffTheme(dynamicColor = false) { PdfToolPicker(selected = PdfTool.MERGE, onSelect = {}) }
}
