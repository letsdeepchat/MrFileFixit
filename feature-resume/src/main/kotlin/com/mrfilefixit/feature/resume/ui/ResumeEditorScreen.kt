package com.mrfilefixit.feature.resume.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mrfilefixit.core.domain.convert.DocumentFormat
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.domain.resume.ContactInfo
import com.mrfilefixit.core.domain.resume.Resume
import com.mrfilefixit.core.domain.resume.ResumeEdits
import com.mrfilefixit.core.domain.resume.ResumeFactory
import com.mrfilefixit.core.domain.resume.ResumeFont
import com.mrfilefixit.core.domain.resume.ResumePaper
import com.mrfilefixit.core.domain.resume.ResumeStyle
import com.mrfilefixit.core.domain.resume.ResumeTemplates
import com.mrfilefixit.core.domain.resume.SectionKind
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.components.CropBox
import com.mrfilefixit.core.ui.components.ErrorCard
import com.mrfilefixit.core.ui.components.JobProgressCard
import com.mrfilefixit.core.ui.components.PdfPagesColumn
import com.mrfilefixit.core.ui.components.SectionCard
import com.mrfilefixit.core.ui.components.SectionHeader
import com.mrfilefixit.core.ui.components.SegmentedChoice
import com.mrfilefixit.core.ui.components.StickyActionBar
import com.mrfilefixit.core.ui.components.ToolScaffold
import com.mrfilefixit.core.ui.format.formatBytes
import com.mrfilefixit.core.ui.preview.AllPreviews
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.theme.MffTheme
import com.mrfilefixit.feature.resume.R
import kotlin.math.roundToInt

internal class ResumeEditorActions(
    val onBack: () -> Unit,
    val onEdit: ((Resume) -> Resume) -> Unit,
    val onChangeTemplate: () -> Unit,
    val onPhoto: (PhotoEvent) -> Unit,
    val onExport: (DocumentFormat) -> Unit,
    val onCancelExport: () -> Unit,
    val onSave: (String) -> Unit,
    val onShare: () -> Unit,
    val newId: () -> String,
)

/**
 * The editor: template and look, photo, contact details, then every section as a card that can be
 * renamed, hidden, moved and deleted. Export is pinned at the bottom; the result card offers Save, Share
 * and, for PDFs, a page preview.
 */
@Composable
internal fun ResumeEditorScreen(
    state: ResumeUiState,
    actions: ResumeEditorActions,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
) {
    val resume = state.resume ?: return
    ToolScaffold(
        title = stringResource(R.string.resume_editor_title),
        modifier = modifier.testTag("resume_editor"),
        onBack = actions.onBack,
        bottomBar = { ExportBar(state.export, actions) },
        snackbarHost = snackbarHost,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("resume_editor_list"),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "template") { TemplateCard(state, actions, Modifier.contentWidth()) }
            item(key = "style") { StyleCard(resume.style, actions, Modifier.contentWidth()) }
            item(key = "photo") { PhotoCard(state, actions, Modifier.contentWidth()) }
            item(key = "contact") { ContactCard(resume.contact, actions, Modifier.contentWidth()) }
            item(key = "sections-header") {
                Column(Modifier.contentWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                    SectionHeader(stringResource(R.string.resume_sections))
                    Text(
                        stringResource(R.string.resume_sections_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(resume.sections, key = { it.id }) { section ->
                SectionEditor(
                    section = section,
                    isFirst = section.id == resume.sections.first().id,
                    isLast = section.id == resume.sections.last().id,
                    actions = actions,
                    modifier = Modifier.contentWidth(),
                )
            }
            item(key = "add-section") { AddSectionCard(actions, Modifier.contentWidth()) }
            when (val job = state.export) {
                is JobState.Failed -> item(key = "export-error") { ErrorCard(job.error, null, Modifier.contentWidth()) }
                is JobState.Success -> item(key = "export-result") {
                    ExportResult(job.value, actions, Modifier.contentWidth())
                }
                else -> Unit
            }
            item(key = "tips") {
                Text(
                    stringResource(R.string.resume_ats_tips),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.contentWidth(),
                )
            }
        }
    }
}

@Composable
private fun TemplateCard(state: ResumeUiState, actions: ResumeEditorActions, modifier: Modifier = Modifier) {
    val template = state.template ?: return
    SectionCard(modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TemplateSketch(template, Modifier.size(SKETCH_WIDTH.dp, SKETCH_HEIGHT.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                Text(
                    stringResource(R.string.resume_current_template, templateName(template)),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.resume_autosaved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = actions.onChangeTemplate,
                    modifier = Modifier
                        .heightIn(min = Dimens.MinTouchTarget)
                        .testTag("resume_change_template"),
                ) { Text(stringResource(R.string.resume_change_template)) }
            }
        }
    }
}

@Composable
private fun StyleCard(style: ResumeStyle, actions: ResumeEditorActions, modifier: Modifier = Modifier) {
    fun update(transform: (ResumeStyle) -> ResumeStyle) = actions.onEdit { it.copy(style = transform(it.style)) }
    SectionCard(modifier) {
        SectionHeader(stringResource(R.string.resume_style))
        Text(stringResource(R.string.resume_font), style = MaterialTheme.typography.labelLarge)
        SegmentedChoice(
            options = ResumeFont.entries,
            selected = style.font,
            label = { stringResource(TemplateText.font(it)) },
            onSelect = { font -> update { it.copy(font = font) } },
        )
        Text(
            stringResource(R.string.resume_text_size, formatPoints(style.bodySizePt)),
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = style.bodySizePt,
            onValueChange = { v -> update { it.copy(bodySizePt = snapHalf(v)) } },
            valueRange = ResumeStyle.MIN_BODY_SIZE..ResumeStyle.MAX_BODY_SIZE,
            steps = SIZE_STEPS,
            modifier = Modifier.testTag("resume_text_size"),
        )
        Text(stringResource(R.string.resume_accent), style = MaterialTheme.typography.labelLarge)
        AccentPicker(style.accentArgb) { color -> update { it.copy(accentArgb = color) } }
        Text(stringResource(R.string.resume_paper), style = MaterialTheme.typography.labelLarge)
        SegmentedChoice(
            options = ResumePaper.entries,
            selected = style.paper,
            label = { stringResource(TemplateText.paper(it)) },
            onSelect = { paper -> update { it.copy(paper = paper) } },
        )
    }
}

/** Round swatches, each a 48 dp radio button, with a check on the selected colour. */
@Composable
private fun AccentPicker(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        val options = (ACCENTS + selected).distinct()
        options.forEachIndexed { index, argb ->
            val isSelected = argb == selected
            val label = stringResource(R.string.resume_accent_option, index + 1)
            Box(
                Modifier
                    .size(Dimens.MinTouchTarget)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(argb) })
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(SWATCH.dp)
                        .background(Color(argb), CircleShape)
                        .border(
                            BorderStroke(if (isSelected) 3.dp else 1.dp, MaterialTheme.colorScheme.outline),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PhotoCard(state: ResumeUiState, actions: ResumeEditorActions, modifier: Modifier = Modifier) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) actions.onPhoto(PhotoEvent.Picked(uri.toString()))
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    SectionCard(modifier) {
        SectionHeader(stringResource(R.string.resume_photo))
        val crop = state.photoCrop
        if (crop != null) {
            Text(stringResource(R.string.resume_photo_crop), style = MaterialTheme.typography.bodyMedium)
            CropBox(
                uri = crop.file.uri,
                imageAspect = crop.imageAspect,
                frameAspect = PHOTO_ASPECT,
                onCropChanged = { actions.onPhoto(PhotoEvent.CropChanged(it)) },
                maxHeight = LocalConfiguration.current.screenHeightDp.dp * CROP_MAX_HEIGHT_FRACTION,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                Button(
                    onClick = { actions.onPhoto(PhotoEvent.Confirm) },
                    enabled = !state.photoBusy,
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                ) { Text(stringResource(R.string.resume_photo_use)) }
                TextButton(
                    onClick = { actions.onPhoto(PhotoEvent.Cancel) },
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                ) { Text(stringResource(R.string.resume_cancel)) }
            }
        } else {
            PhotoSummary(state, actions, pick)
        }
    }
}

@Composable
private fun PhotoSummary(state: ResumeUiState, actions: ResumeEditorActions, pick: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM), verticalAlignment = Alignment.Top) {
        PhotoFrame(state.photo)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            Text(stringResource(R.string.resume_photo_hint), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(R.string.resume_photo_ats),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        OutlinedButton(
            onClick = pick,
            enabled = !state.photoBusy,
            modifier = Modifier
                .heightIn(min = Dimens.MinTouchTarget)
                .testTag("resume_photo_pick"),
        ) {
            Text(stringResource(if (state.photo == null) R.string.resume_photo_add else R.string.resume_photo_change))
        }
        if (state.photo != null) {
            TextButton(
                onClick = { actions.onPhoto(PhotoEvent.Remove) },
                modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
            ) { Text(stringResource(R.string.resume_photo_remove)) }
        }
    }
}

/** The 35 × 45 frame with the stored photo, or an add-photo placeholder. */
@Composable
private fun PhotoFrame(photo: PhotoBytes?) {
    val bitmap = remember(photo) {
        photo?.bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap()
    }
    val frame = Modifier
        .size(FRAME_WIDTH.dp, (FRAME_WIDTH / PHOTO_ASPECT).dp)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (bitmap != null) {
        Image(
            bitmap,
            contentDescription = stringResource(R.string.cd_resume_photo),
            modifier = frame,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(frame, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ContactCard(contact: ContactInfo, actions: ResumeEditorActions, modifier: Modifier = Modifier) {
    fun update(transform: (ContactInfo) -> ContactInfo) = actions.onEdit { it.copy(contact = transform(it.contact)) }
    SectionCard(modifier) {
        SectionHeader(stringResource(R.string.resume_contact))
        EditField(
            contact.fullName,
            { v -> update { it.copy(fullName = v) } },
            stringResource(R.string.resume_full_name),
        )
        EditField(contact.headline, { v -> update { it.copy(headline = v) } }, stringResource(R.string.resume_headline))
        EditField(
            contact.email,
            { v -> update { it.copy(email = v) } },
            stringResource(R.string.resume_email),
            keyboardType = KeyboardType.Email,
        )
        EditField(
            contact.phone,
            { v -> update { it.copy(phone = v) } },
            stringResource(R.string.resume_phone),
            keyboardType = KeyboardType.Phone,
        )
        EditField(contact.location, { v -> update { it.copy(location = v) } }, stringResource(R.string.resume_location))
        EditField(
            contact.links.joinToString("\n"),
            { v -> update { it.copy(links = v.split('\n')) } },
            stringResource(R.string.resume_links),
            singleLine = false,
            keyboardType = KeyboardType.Uri,
        )
    }
}

@Composable
private fun AddSectionCard(actions: ResumeEditorActions, modifier: Modifier = Modifier) {
    val titles = SectionKind.entries.associateWith { stringResource(TemplateText.defaultTitle(it)) }
    SectionCard(modifier) {
        SectionHeader(stringResource(R.string.resume_add_section))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            SectionKind.entries.forEach { kind ->
                AssistChip(
                    onClick = {
                        actions.onEdit { ResumeEdits.addSection(it, kind, titles.getValue(kind), actions.newId) }
                    },
                    label = { Text(stringResource(TemplateText.kind(kind))) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    modifier = Modifier
                        .heightIn(min = Dimens.MinTouchTarget)
                        .testTag("resume_add_${kind.name.lowercase()}"),
                )
            }
        }
    }
}

@Composable
private fun ExportBar(job: JobState<OutputFile>, actions: ResumeEditorActions) {
    StickyActionBar {
        if (job is JobState.Running) {
            JobProgressCard(job.progress, actions.onCancelExport)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                Button(
                    onClick = { actions.onExport(DocumentFormat.PDF) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Dimens.PrimaryButtonHeight)
                        .testTag("resume_export_pdf"),
                ) { Text(stringResource(R.string.resume_export_pdf)) }
                OutlinedButton(
                    onClick = { actions.onExport(DocumentFormat.DOCX) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Dimens.PrimaryButtonHeight)
                        .testTag("resume_export_docx"),
                ) { Text(stringResource(R.string.resume_export_docx)) }
            }
        }
    }
}

@Composable
private fun ExportResult(output: OutputFile, actions: ResumeEditorActions, modifier: Modifier = Modifier) {
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(output.mimeType)) { uri ->
        if (uri != null) actions.onSave(uri.toString())
    }
    val isPdf = output.mimeType == DocumentFormat.PDF.mimeType
    SectionCard(modifier.testTag("resume_export_result")) {
        SectionHeader(
            stringResource(
                R.string.resume_export_ready,
                stringResource(if (isPdf) R.string.resume_export_pdf_name else R.string.resume_export_docx_name),
            ),
        )
        Text(
            output.pageCount?.let {
                pluralStringResource(R.plurals.resume_export_pages, it, formatBytes(output.sizeBytes), it)
            } ?: formatBytes(output.sizeBytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            Button(
                onClick = { saver.launch(output.displayName) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.MinTouchTarget),
            ) { Text(stringResource(CoreR.string.action_save)) }
            OutlinedButton(
                onClick = actions.onShare,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.MinTouchTarget),
            ) { Text(stringResource(CoreR.string.action_share)) }
        }
        if (isPdf) {
            SectionHeader(stringResource(R.string.resume_preview), Modifier.padding(top = Dimens.SpaceS))
            PdfPagesColumn(output.path, Modifier.fillMaxWidth())
        }
    }
}

/** A single- or multi-line text field that fills the card width. */
@Composable
internal fun EditField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        supportingText = if (supporting != null) {
            { Text(supporting) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

private fun snapHalf(value: Float): Float = (value * 2).roundToInt() / 2f

private fun formatPoints(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()

private const val PHOTO_ASPECT = 413f / 531f
private const val FRAME_WIDTH = 72f
private const val SWATCH = 32
private const val SKETCH_WIDTH = 56
private const val SKETCH_HEIGHT = 79
private const val CROP_MAX_HEIGHT_FRACTION = 0.45f

/** 9 to 13 pt in half-point steps: 7 stops between the ends. */
private const val SIZE_STEPS = 7

private val ACCENTS = listOf(
    0xFF235595.toInt(),
    0xFF007573.toInt(),
    0xFF1E6D39.toInt(),
    0xFF80232E.toInt(),
    0xFF5938B5.toInt(),
    0xFFC04C3C.toInt(),
    0xFF844B0E.toInt(),
    0xFF333333.toInt(),
)

private val PreviewActions = ResumeEditorActions({}, {}, {}, {}, {}, {}, {}, {}, { "id" })

@AllPreviews
@Composable
private fun ResumeEditorPreview() {
    val resume = remember {
        var n = 0
        ResumeFactory.fromTemplate(ResumeTemplates.all.first(), "p", 0L) { "p${n++}" }
    }
    MffTheme(dynamicColor = false) {
        ResumeEditorScreen(ResumeUiState(page = ResumePage.EDITOR, resume = resume), PreviewActions)
    }
}
