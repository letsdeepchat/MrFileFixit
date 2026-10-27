package com.mrfilefixit.feature.resume.ui

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrfilefixit.core.domain.biodata.BiodataSummary
import com.mrfilefixit.core.domain.biodata.BiodataTemplates
import com.mrfilefixit.core.domain.convert.DocumentFormat
import com.mrfilefixit.core.domain.resume.HeaderLayout
import com.mrfilefixit.core.domain.resume.HeadingStyle
import com.mrfilefixit.core.domain.resume.ResumeSummary
import com.mrfilefixit.core.domain.resume.ResumeTemplate
import com.mrfilefixit.core.domain.resume.ResumeTemplates
import com.mrfilefixit.core.ui.components.ErrorCard
import com.mrfilefixit.core.ui.components.IconBadge
import com.mrfilefixit.core.ui.components.SectionHeader
import com.mrfilefixit.core.ui.components.SegmentedChoice
import com.mrfilefixit.core.ui.components.ToolScaffold
import com.mrfilefixit.core.ui.preview.AllPreviews
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.theme.MffTheme
import com.mrfilefixit.feature.resume.R

/** Hub actions: start a resume or a biodata, re-open a Word resume, continue or delete drafts. */
internal class ResumeHubActions(
    val onCreateResume: () -> Unit,
    val onCreateBiodata: () -> Unit,
    val onOpenResume: (String) -> Unit,
    val onOpenBiodata: (String) -> Unit,
    val onDeleteResume: (String) -> Unit,
    val onDeleteBiodata: (String) -> Unit,
    val onImport: (String) -> Unit,
    val onDismissError: () -> Unit,
)

/** Which saved drafts the hub lists. */
internal enum class DraftKind(@StringRes val label: Int, @StringRes val empty: Int) {
    RESUMES(R.string.resume_tab_resumes, R.string.resume_saved_empty),
    BIODATA(R.string.resume_tab_biodata, R.string.biodata_saved_empty),
}

/**
 * First page of the Resume & Biodata tab: two large actions (a professional resume or a biodata), re-opening
 * an exported Word resume, and the saved drafts of either kind.
 */
@Composable
internal fun ResumeListScreen(
    state: ResumeUiState,
    biodataDrafts: List<BiodataSummary>,
    actions: ResumeHubActions,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
) {
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) actions.onImport(uri.toString())
    }
    var shown by rememberSaveable { mutableStateOf(DraftKind.RESUMES) }
    var confirmDelete by remember { mutableStateOf<Pair<DraftKind, String>?>(null) }
    ToolScaffold(
        title = stringResource(R.string.resume_title),
        modifier = modifier.testTag("resume_list"),
        snackbarHost = snackbarHost,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "create-resume") {
                HubCard(
                    icon = Icons.Filled.Description,
                    title = stringResource(R.string.resume_hub_resume_title),
                    body = stringResource(R.string.resume_hub_resume_body),
                    onClick = actions.onCreateResume,
                    modifier = Modifier
                        .contentWidth()
                        .testTag("resume_create"),
                )
            }
            item(key = "create-biodata") {
                HubCard(
                    icon = Icons.Filled.Favorite,
                    title = stringResource(R.string.resume_hub_biodata_title),
                    body = stringResource(R.string.resume_hub_biodata_body),
                    onClick = actions.onCreateBiodata,
                    modifier = Modifier
                        .contentWidth()
                        .testTag("biodata_create"),
                )
            }
            item(key = "import") {
                Column(Modifier.contentWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                    OutlinedButton(
                        onClick = { importer.launch(arrayOf(DocumentFormat.DOCX.mimeType, OCTET_STREAM)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.MinTouchTarget),
                    ) {
                        Icon(Icons.Filled.FileOpen, contentDescription = null)
                        Text(stringResource(R.string.resume_import), Modifier.padding(start = Dimens.SpaceS))
                    }
                    Text(
                        stringResource(R.string.resume_import_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.error?.let { error ->
                item(key = "error") {
                    Column(Modifier.contentWidth()) {
                        ErrorCard(error, onRetry = null)
                        TextButton(onClick = actions.onDismissError) { Text(stringResource(R.string.resume_cancel)) }
                    }
                }
            }
            item(key = "saved-header") {
                Column(Modifier.contentWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                    SectionHeader(stringResource(R.string.resume_saved_title))
                    SegmentedChoice(
                        options = DraftKind.entries,
                        selected = shown,
                        label = { stringResource(it.label) },
                        onSelect = { shown = it },
                        modifier = Modifier.testTag("drafts_kind"),
                    )
                }
            }
            val empty = if (shown == DraftKind.RESUMES) state.drafts.isEmpty() else biodataDrafts.isEmpty()
            if (empty) {
                item(key = "saved-empty") {
                    Text(
                        stringResource(shown.empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.contentWidth(),
                    )
                }
            }
            if (shown == DraftKind.RESUMES) {
                items(state.drafts, key = { "r-${it.id}" }) { draft ->
                    DraftRow(
                        draft,
                        onOpen = { actions.onOpenResume(draft.id) },
                        onDelete = { confirmDelete = DraftKind.RESUMES to draft.id },
                    )
                }
            } else {
                items(biodataDrafts, key = { "b-${it.id}" }) { draft ->
                    BiodataDraftRow(
                        draft,
                        onOpen = { actions.onOpenBiodata(draft.id) },
                        onDelete = { confirmDelete = DraftKind.BIODATA to draft.id },
                    )
                }
            }
        }
    }
    confirmDelete?.let { (kind, id) ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.resume_delete_title)) },
            text = { Text(stringResource(R.string.resume_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    if (kind == DraftKind.RESUMES) actions.onDeleteResume(id) else actions.onDeleteBiodata(id)
                    confirmDelete = null
                }) { Text(stringResource(R.string.resume_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.resume_cancel)) }
            },
        )
    }
}

/** A large tappable action: icon, title and one line on what it makes. */
@Composable
private fun HubCard(icon: ImageVector, title: String, body: String, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(Dimens.CardCorner),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = HUB_BORDER_ALPHA)),
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainer),
        modifier = modifier.heightIn(min = Dimens.MinTouchTarget),
    ) {
        Row(
            Modifier.padding(Dimens.SectionPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun DraftRow(draft: ResumeSummary, onOpen: () -> Unit, onDelete: () -> Unit) {
    val template = ResumeTemplates.byId(draft.templateId)
    val edited = DateUtils.getRelativeTimeSpanString(draft.updatedAtMillis).toString()
    OutlinedCard(
        onClick = onOpen,
        shape = RoundedCornerShape(Dimens.CardCorner),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.contentWidth(),
    ) {
        ListItem(
            leadingContent = { TemplateSketch(template, Modifier.width(SKETCH_SMALL.dp)) },
            headlineContent = { Text(draft.name) },
            supportingContent = {
                Text("${templateName(template)} · ${stringResource(R.string.resume_edited, edited)}")
            },
            trailingContent = {
                TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
                    Text(stringResource(R.string.resume_delete))
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
    }
}

@Composable
private fun BiodataDraftRow(draft: BiodataSummary, onOpen: () -> Unit, onDelete: () -> Unit) {
    val template = BiodataTemplates.byId(draft.templateId)
    val edited = DateUtils.getRelativeTimeSpanString(draft.updatedAtMillis).toString()
    OutlinedCard(
        onClick = onOpen,
        shape = RoundedCornerShape(Dimens.CardCorner),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .contentWidth()
            .testTag("biodata_draft_${draft.id}"),
    ) {
        ListItem(
            leadingContent = { BiodataSketch(template, Modifier.width(SKETCH_SMALL.dp)) },
            headlineContent = { Text(draft.displayName) },
            supportingContent = {
                Text(
                    stringResource(
                        R.string.biodata_draft_photos,
                        stringResource(BiodataText.templateName(template)),
                        draft.photoCount,
                        stringResource(R.string.resume_edited, edited),
                    ),
                )
            },
            trailingContent = {
                TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
                    Text(stringResource(R.string.resume_delete))
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
    }
}

/** The 15 templates as cards with a sketch of their layout in their accent colour. */
@Composable
internal fun TemplateGalleryScreen(
    current: String?,
    onChoose: (ResumeTemplate) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScaffold(
        title = stringResource(R.string.resume_pick_template),
        modifier = modifier.testTag("resume_templates"),
        onBack = onBack,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = TEMPLATE_MIN_WIDTH.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("resume_templates_grid"),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.resume_pick_template_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(ResumeTemplates.all, key = { it.id }) { template ->
                TemplateCard(template, selected = template.id == current, onClick = { onChoose(template) })
            }
        }
    }
}

@Composable
private fun TemplateCard(template: ResumeTemplate, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(Dimens.CardCorner),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("template_${template.id.lowercase()}"),
    ) {
        Column(Modifier.padding(Dimens.SpaceM), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            TemplateSketch(template, Modifier.fillMaxWidth())
            Text(templateName(template), style = MaterialTheme.typography.titleSmall)
            Text(
                templateDescription(template),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                AssistChip(onClick = onClick, label = { Text(stringResource(R.string.resume_template_ats)) })
                if (template.photo) {
                    AssistChip(onClick = onClick, label = { Text(stringResource(R.string.resume_template_photo)) })
                }
            }
        }
    }
}

/**
 * A page-shaped sketch of the template: header style, heading style and text lines in the accent
 * colour. Decorative, so it has no content description; the card's text names the template.
 */
@Composable
internal fun TemplateSketch(template: ResumeTemplate, modifier: Modifier = Modifier) {
    val accent = Color(template.accentArgb)
    val ink = Color(SKETCH_INK)
    val paper = Color.White
    Canvas(
        modifier
            .aspectRatio(PAGE_ASPECT)
            .widthIn(min = SKETCH_SMALL.dp),
    ) {
        drawRoundRect(paper, cornerRadius = CornerRadius(4.dp.toPx()))
        val pad = size.width * PAD
        var y = pad
        val contentWidth = size.width - 2 * pad
        val centered = template.header == HeaderLayout.CENTERED
        if (template.header == HeaderLayout.BAND) {
            drawRect(accent, Offset(0f, y), Size(size.width, size.height * BAND_HEIGHT))
            line(Color.White, pad, y + size.height * BAND_HEIGHT / 2, contentWidth * NAME_WIDTH, centered = false)
            y += size.height * BAND_HEIGHT + pad / 2
        } else {
            line(accent, pad, y + LINE_GAP, contentWidth * NAME_WIDTH, centered, thick = true)
            y += size.height * BAND_HEIGHT * NAME_BLOCK
        }
        if (template.photo) {
            val w = size.width * PHOTO_WIDTH
            drawRect(accent.copy(alpha = PHOTO_ALPHA), Offset(size.width - pad - w, pad), Size(w, w * PHOTO_RATIO))
        }
        line(ink, pad, y, contentWidth * CONTACT_WIDTH, centered && !template.photo)
        y += size.height * SECTION_GAP
        repeat(SKETCH_SECTIONS) {
            if (y > size.height - pad) return@repeat
            when (template.heading) {
                HeadingStyle.BAND -> drawRect(
                    accent.copy(alpha = BAND_ALPHA),
                    Offset(pad, y - LINE_GAP),
                    Size(contentWidth, LINE_GAP * 2),
                )
                HeadingStyle.RULE -> drawRect(accent, Offset(pad, y + LINE_GAP), Size(contentWidth, 1f))
                HeadingStyle.CAPS -> Unit
            }
            line(accent, pad, y, contentWidth * HEADING_WIDTH, centered = false, thick = true)
            y += size.height * LINE_STEP
            repeat(TEXT_LINES) { i ->
                line(ink.copy(alpha = TEXT_ALPHA), pad, y, contentWidth * (1f - i * LINE_SHRINK), centered = false)
                y += size.height * LINE_STEP
            }
            y += size.height * LINE_STEP
        }
    }
}

private fun DrawScope.line(color: Color, x: Float, y: Float, width: Float, centered: Boolean, thick: Boolean = false) {
    val start = if (centered) (size.width - width) / 2 else x
    val height = if (thick) LINE_GAP * THICK else LINE_GAP
    drawRoundRect(color, Offset(start, y - height / 2), Size(width, height), CornerRadius(height / 2))
}

@Composable
internal fun templateName(template: ResumeTemplate): String = stringResource(TemplateText.name(template.field))

@Composable
private fun templateDescription(template: ResumeTemplate): String =
    stringResource(TemplateText.description(template.field))

internal fun Modifier.contentWidth(): Modifier = widthIn(max = Dimens.CardMaxWidth).fillMaxWidth()

private const val OCTET_STREAM = "application/octet-stream"
private const val TEMPLATE_MIN_WIDTH = 160
private const val SKETCH_SMALL = 44
private const val HUB_BORDER_ALPHA = 0.5f
private const val PAGE_ASPECT = 210f / 297f
private const val SKETCH_INK = 0xFF2B2B2B
private const val PAD = 0.09f
private const val BAND_HEIGHT = 0.11f
private const val NAME_BLOCK = 0.7f
private const val NAME_WIDTH = 0.55f
private const val CONTACT_WIDTH = 0.7f
private const val HEADING_WIDTH = 0.35f
private const val SECTION_GAP = 0.06f
private const val LINE_STEP = 0.035f
private const val LINE_GAP = 3f
private const val THICK = 1.6f
private const val LINE_SHRINK = 0.15f
private const val TEXT_LINES = 3
private const val SKETCH_SECTIONS = 4
private const val TEXT_ALPHA = 0.45f
private const val BAND_ALPHA = 0.18f
private const val PHOTO_ALPHA = 0.35f
private const val PHOTO_WIDTH = 0.18f
private const val PHOTO_RATIO = 531f / 413f

@AllPreviews
@Composable
private fun TemplateGalleryPreview() {
    MffTheme(dynamicColor = false) { TemplateGalleryScreen(current = null, onChoose = {}, onBack = {}) }
}

@AllPreviews
@Composable
private fun ResumeListPreview() {
    MffTheme(dynamicColor = false) {
        ResumeListScreen(ResumeUiState(), emptyList(), ResumeHubActions({}, {}, {}, {}, {}, {}, {}, {}))
    }
}
