package com.mrfilefixit.feature.resume.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mrfilefixit.core.domain.biodata.Biodata
import com.mrfilefixit.core.domain.biodata.BiodataAge
import com.mrfilefixit.core.domain.biodata.BiodataEdits
import com.mrfilefixit.core.domain.biodata.BiodataField
import com.mrfilefixit.core.domain.biodata.BiodataKind
import com.mrfilefixit.core.domain.biodata.BiodataSection
import com.mrfilefixit.core.domain.biodata.BiodataTemplate
import com.mrfilefixit.core.domain.biodata.BiodataTemplates
import com.mrfilefixit.core.domain.biodata.FieldInput
import com.mrfilefixit.core.domain.biodata.FieldStyle
import com.mrfilefixit.core.domain.biodata.PhotoLayout
import com.mrfilefixit.core.domain.convert.DocumentFormat
import com.mrfilefixit.core.domain.document.BorderStyle
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.components.CropBox
import com.mrfilefixit.core.ui.components.ErrorCard
import com.mrfilefixit.core.ui.components.JobProgressCard
import com.mrfilefixit.core.ui.components.PdfPagesColumn
import com.mrfilefixit.core.ui.components.SectionCard
import com.mrfilefixit.core.ui.components.SectionHeader
import com.mrfilefixit.core.ui.components.SegmentedChoice
import com.mrfilefixit.core.ui.components.StickyActionBar
import com.mrfilefixit.core.ui.components.SwitchRow
import com.mrfilefixit.core.ui.components.ToolScaffold
import com.mrfilefixit.core.ui.components.windowHeight
import com.mrfilefixit.core.ui.format.formatBytes
import com.mrfilefixit.core.ui.preview.AllPreviews
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.theme.MffTheme
import com.mrfilefixit.feature.resume.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Callbacks from the biodata editor to [BiodataViewModel]. */
internal class BiodataEditorActions(
    val onBack: () -> Unit,
    val onEdit: ((Biodata) -> Biodata) -> Unit,
    val onChangeDesign: () -> Unit,
    val onPreview: (Boolean) -> Unit,
    val onPhoto: (BiodataPhotoEvent) -> Unit,
    val onExport: (DocumentFormat) -> Unit,
    val onCancelExport: () -> Unit,
    val onSave: (String) -> Unit,
    val onShare: () -> Unit,
)

/**
 * The five biodata designs, grouped by type. Restyling an open draft offers only designs of its own type,
 * so no field is lost.
 */
@Composable
internal fun BiodataTemplatesScreen(
    current: String?,
    onlyKind: BiodataKind?,
    onChoose: (BiodataTemplate) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScaffold(
        title = stringResource(R.string.biodata_pick_template),
        modifier = modifier.testTag("biodata_templates"),
        onBack = onBack,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = TEMPLATE_MIN_WIDTH.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("biodata_templates_grid"),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.biodata_pick_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BiodataKind.entries.filter { onlyKind == null || it == onlyKind }.forEach { kind ->
                item(key = "kind-$kind", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(stringResource(BiodataText.kind(kind)))
                }
                items(BiodataTemplates.all.filter { it.kind == kind }, key = { it.id }) { template ->
                    BiodataTemplateCard(template, template.id == current, onClick = { onChoose(template) })
                }
            }
        }
    }
}

@Composable
private fun BiodataTemplateCard(template: BiodataTemplate, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(Dimens.CardCorner),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("biodata_template_${template.id}"),
    ) {
        Column(Modifier.padding(Dimens.SpaceM), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            BiodataSketch(template, Modifier.fillMaxWidth())
            Text(stringResource(BiodataText.templateName(template)), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(BiodataText.templateDescription(template)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** A page-shaped sketch of a design: its frame, photo placement and heading colour. Decorative. */
@Composable
internal fun BiodataSketch(template: BiodataTemplate, modifier: Modifier = Modifier) {
    val accent = Color(template.accentArgb)
    val secondary = Color(template.secondaryArgb)
    val ink = Color(SKETCH_INK)
    Canvas(modifier.aspectRatio(PAGE_ASPECT)) {
        drawRoundRect(Color.White, cornerRadius = CornerRadius(4.dp.toPx()))
        val inset = size.width * FRAME_INSET
        val frameSize = Size(size.width - 2 * inset, size.height - 2 * inset)
        when (template.border) {
            BorderStyle.SINGLE -> drawRect(accent, Offset(inset, inset), frameSize, style = Stroke(1f))
            BorderStyle.DOUBLE, BorderStyle.ORNATE -> {
                drawRect(accent, Offset(inset, inset), frameSize, style = Stroke(2f))
                val gap = inset * INNER_FRAME
                drawRect(
                    secondary,
                    Offset(inset + gap, inset + gap),
                    Size(frameSize.width - 2 * gap, frameSize.height - 2 * gap),
                    style = Stroke(1f),
                )
            }
            BorderStyle.CORNERS -> {
                val l = size.width * CORNER
                listOf(
                    Offset(inset, inset) to Offset(1f, 1f),
                    Offset(size.width - inset, inset) to Offset(-1f, 1f),
                    Offset(inset, size.height - inset) to Offset(1f, -1f),
                    Offset(size.width - inset, size.height - inset) to Offset(-1f, -1f),
                ).forEach { (corner, dir) ->
                    drawLine(accent, corner, corner + Offset(l * dir.x, 0f), strokeWidth = 2f)
                    drawLine(accent, corner, corner + Offset(0f, l * dir.y), strokeWidth = 2f)
                }
            }
            null -> Unit
        }
        val pad = size.width * PAD
        val contentWidth = size.width - 2 * pad
        // Title.
        drawRoundRect(
            accent,
            Offset(pad + contentWidth * TITLE_SIDE, pad),
            Size(contentWidth * (1 - 2 * TITLE_SIDE), LINE * TITLE_THICK),
            CornerRadius(LINE),
        )
        var y = pad + size.height * TITLE_GAP
        if (template.photoLayout == PhotoLayout.BESIDE_DETAILS) {
            val photoW = contentWidth * PHOTO_SHARE
            val photoH = photoW / PORTRAIT
            drawRect(accent.copy(alpha = PHOTO_ALPHA), Offset(size.width - pad - photoW, y), Size(photoW, photoH))
            repeat(DETAIL_LINES) { i ->
                val lineY = y + i * size.height * LINE_STEP
                drawRect(ink.copy(alpha = TEXT_ALPHA), Offset(pad, lineY), Size(contentWidth * DETAIL_WIDTH, LINE))
            }
            y += photoH + size.height * LINE_STEP
        } else {
            val photoW = contentWidth * PROFILE_SHARE
            drawRect(accent.copy(alpha = PHOTO_ALPHA), Offset(pad, y), Size(photoW, photoW))
            y += photoW + size.height * LINE_STEP
            val sampleH = contentWidth * if (template.photoLayout == PhotoLayout.SHOWCASE) SHOWCASE else GRID_ROW
            drawRect(secondary.copy(alpha = PHOTO_ALPHA), Offset(pad, y), Size(contentWidth, sampleH))
            y += sampleH + size.height * LINE_STEP
        }
        while (y < size.height - pad - LINE * BOTTOM_LINES) {
            drawRect(accent, Offset(pad, y), Size(contentWidth * HEADING_WIDTH, LINE))
            y += size.height * LINE_STEP
            drawRect(ink.copy(alpha = TEXT_ALPHA), Offset(pad, y), Size(contentWidth, LINE))
            y += size.height * LINE_STEP * 2
        }
    }
}

/**
 * The biodata editor: name, photos (1 to 5, framed on add), then each section of the design's type. The
 * cultural and astrological section can be switched off as a whole or field by field. Preview shows the real
 * PDF, made again as you type; PDF and Word export are pinned at the bottom.
 */
@Composable
internal fun BiodataEditorScreen(
    state: BiodataUiState,
    actions: BiodataEditorActions,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
) {
    val biodata = state.biodata ?: return
    val template = BiodataTemplates.byId(biodata.templateId)
    ToolScaffold(
        title = biodata.name.ifBlank { stringResource(BiodataText.kind(template.kind)) },
        modifier = modifier.testTag("biodata_editor"),
        onBack = actions.onBack,
        actions = {
            TextButton(onClick = actions.onChangeDesign, modifier = Modifier.testTag("biodata_change_design")) {
                Text(stringResource(R.string.biodata_change_design))
            }
        },
        bottomBar = { BiodataExportBar(state.export, actions) },
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
            item(key = "mode") {
                SegmentedChoice(
                    options = listOf(false, true),
                    selected = state.previewing,
                    label = { stringResource(if (it) R.string.biodata_mode_preview else R.string.biodata_mode_edit) },
                    onSelect = actions.onPreview,
                    modifier = Modifier
                        .contentWidth()
                        .testTag("biodata_mode"),
                )
            }
            state.exported?.let { output ->
                item(key = "export") { BiodataExportResult(output, actions, Modifier.contentWidth()) }
            }
            when (val job = state.export) {
                is JobState.Failed -> item(key = "export-error") { ErrorCard(job.error, null, Modifier.contentWidth()) }
                else -> Unit
            }
            if (state.previewing) {
                item(key = "preview") { LivePreview(state, Modifier.contentWidth()) }
            } else {
                item(key = "identity") { IdentityCard(biodata, template.kind, actions, Modifier.contentWidth()) }
                item(key = "photos") { PhotosCard(state, template.kind, actions, Modifier.contentWidth()) }
                BiodataSection.forKind(template.kind).forEach { section ->
                    item(key = "section-$section") {
                        SectionEditor(biodata, template.kind, section, actions.onEdit, Modifier.contentWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun IdentityCard(biodata: Biodata, kind: BiodataKind, actions: BiodataEditorActions, modifier: Modifier) {
    SectionCard(modifier) {
        BiodataTextField(
            value = biodata.name,
            onValue = { v -> actions.onEdit { BiodataEdits.setName(it, v) } },
            label = stringResource(R.string.biodata_name),
            modifier = Modifier.testTag("biodata_name"),
        )
        if (kind == BiodataKind.PORTFOLIO) {
            BiodataTextField(
                value = biodata.tagline,
                onValue = { v -> actions.onEdit { BiodataEdits.setTagline(it, v) } },
                label = stringResource(R.string.biodata_tagline),
            )
        } else {
            BiodataTextField(
                value = biodata.invocation,
                onValue = { v -> actions.onEdit { BiodataEdits.setInvocation(it, v) } },
                label = stringResource(R.string.biodata_invocation),
            )
        }
    }
}

@Composable
private fun SectionEditor(
    biodata: Biodata,
    kind: BiodataKind,
    section: BiodataSection,
    onEdit: ((Biodata) -> Biodata) -> Unit,
    modifier: Modifier,
) {
    SectionCard(modifier.testTag("biodata_section_${section.name.lowercase()}")) {
        SectionHeader(stringResource(BiodataText.section(section)))
        val cultural = section == BiodataSection.CULTURAL
        if (cultural) {
            val shown = BiodataEdits.culturalShown(biodata)
            SwitchRow(
                label = stringResource(R.string.biodata_cultural_switch),
                checked = shown,
                onChecked = { on -> onEdit { BiodataEdits.setCulturalShown(it, on) } },
                supporting = stringResource(R.string.biodata_cultural_hint),
                modifier = Modifier.testTag("biodata_cultural_switch"),
            )
            if (!shown) return@SectionCard
        }
        section.fields(kind).forEach { field ->
            FieldEditor(biodata, field, onEdit)
        }
    }
}

@Composable
private fun FieldEditor(biodata: Biodata, field: BiodataField, onEdit: ((Biodata) -> Biodata) -> Unit) {
    val label = stringResource(BiodataText.field(field))
    val value = biodata.value(field)
    val set: (String) -> Unit = { v -> onEdit { BiodataEdits.setValue(it, field, v) } }
    var picking by rememberSaveable { mutableStateOf(false) }
    val supporting = when {
        field.style == FieldStyle.LIST -> stringResource(R.string.biodata_list_hint)
        field == BiodataField.AGE -> {
            val years = BiodataAge.years(biodata.value(BiodataField.DATE_OF_BIRTH), LocalDate.now())
            years?.let { stringResource(R.string.biodata_age_auto, it.toString()) }
        }
        field.input == FieldInput.DATE -> stringResource(R.string.biodata_date_hint)
        else -> null
    }
    BiodataTextField(
        value = value,
        onValue = set,
        label = label,
        singleLine = field.style == FieldStyle.LINE,
        keyboardType = keyboardFor(field.input),
        supporting = supporting,
        modifier = Modifier.testTag("biodata_field_${field.name.lowercase()}"),
        trailing = when {
            field.input == FieldInput.DATE -> {
                {
                    IconButton(onClick = { picking = true }) {
                        Icon(Icons.Filled.CalendarMonth, stringResource(R.string.biodata_pick_date))
                    }
                }
            }
            field.toggleable -> {
                {
                    val shown = field !in biodata.hidden
                    IconToggleButton(
                        checked = shown,
                        onCheckedChange = { on -> onEdit { BiodataEdits.setShown(it, field, on) } },
                        modifier = Modifier.testTag("biodata_show_${field.name.lowercase()}"),
                    ) {
                        Icon(
                            if (shown) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            stringResource(R.string.biodata_field_show, label),
                        )
                    }
                }
            }
            else -> null
        },
    )
    if (picking) {
        DateDialog(
            initial = BiodataAge.parse(value),
            onPicked = { date ->
                picking = false
                set(BiodataAge.format(date))
            },
            onDismiss = { picking = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate?, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val startMillis = (initial ?: LocalDate.now().minusYears(DEFAULT_AGE_YEARS))
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = startMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    } ?: onDismiss()
                },
            ) { Text(stringResource(R.string.biodata_date_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.resume_cancel)) } },
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun BiodataTextField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
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
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

private fun keyboardFor(input: FieldInput): KeyboardType = when (input) {
    FieldInput.NUMBER -> KeyboardType.Number
    FieldInput.PHONE -> KeyboardType.Phone
    FieldInput.EMAIL -> KeyboardType.Email
    FieldInput.URL -> KeyboardType.Uri
    FieldInput.TEXT, FieldInput.DATE, FieldInput.TIME -> KeyboardType.Text
}

@Composable
private fun PhotosCard(state: BiodataUiState, kind: BiodataKind, actions: BiodataEditorActions, modifier: Modifier) {
    val biodata = state.biodata ?: return
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) actions.onPhoto(BiodataPhotoEvent.Picked(uri.toString()))
    }
    SectionCard(modifier.testTag("biodata_photos")) {
        SectionHeader(stringResource(R.string.biodata_photos_title, biodata.photos.size, Biodata.MAX_PHOTOS))
        val crop = state.photoCrop
        if (crop != null) {
            PhotoCropper(crop, state.photoBusy, actions)
            return@SectionCard
        }
        Text(
            stringResource(
                if (kind == BiodataKind.MARRIAGE) {
                    R.string.biodata_photos_hint_marriage
                } else {
                    R.string.biodata_photos_hint_portfolio
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        biodata.photos.forEachIndexed { index, photo ->
            PhotoRow(index, photo.key, state.photos[photo.key], biodata.photos.size, actions)
        }
        if (state.photoBusy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        if (biodata.canAddPhoto) {
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !state.photoBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.MinTouchTarget)
                    .testTag("biodata_photo_add"),
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                Text(stringResource(R.string.biodata_photo_add), Modifier.padding(start = Dimens.SpaceS))
            }
        }
    }
}

/** One stored photo: thumbnail, "Main" on the first, and buttons to promote, move or remove it. */
@Composable
private fun PhotoRow(index: Int, key: String, bytes: PhotoBytes?, count: Int, actions: BiodataEditorActions) {
    val bitmap by produceState<ImageBitmap?>(null, bytes) {
        value = bytes?.let { withContext(Dispatchers.Default) { decodeThumbnail(it.bytes) } }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("biodata_photo_$index"),
    ) {
        Box(
            Modifier
                .size(Dimens.ThumbnailSize)
                .clip(RoundedCornerShape(Dimens.SpaceS))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            bitmap?.let {
                Image(
                    it,
                    stringResource(R.string.biodata_photo_cd, index + 1),
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (index == 0) {
                Text(
                    stringResource(R.string.biodata_photo_main),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = Dimens.SpaceXs),
                )
            }
        }
        FlowRow(Modifier.weight(1f)) {
            if (index > 0) {
                IconButton(onClick = { actions.onPhoto(BiodataPhotoEvent.MakePrimary(key)) }) {
                    Icon(Icons.Filled.Star, stringResource(R.string.biodata_photo_make_main))
                }
                IconButton(onClick = { actions.onPhoto(BiodataPhotoEvent.Move(index, index - 1)) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.biodata_photo_earlier))
                }
            }
            if (index < count - 1) {
                IconButton(onClick = { actions.onPhoto(BiodataPhotoEvent.Move(index, index + 1)) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.biodata_photo_later))
                }
            }
            IconButton(
                onClick = { actions.onPhoto(BiodataPhotoEvent.Remove(key)) },
                modifier = Modifier.testTag("biodata_photo_remove_$index"),
            ) {
                Icon(Icons.Filled.Delete, stringResource(R.string.biodata_photo_remove))
            }
        }
    }
}

@Composable
private fun PhotoCropper(crop: BiodataPhotoCrop, busy: Boolean, actions: BiodataEditorActions) {
    Text(stringResource(R.string.biodata_crop_title), style = MaterialTheme.typography.titleSmall)
    CropBox(
        uri = crop.file.uri,
        imageAspect = crop.imageAspect,
        frameAspect = crop.aspect.ratio ?: crop.imageAspect,
        onCropChanged = { actions.onPhoto(BiodataPhotoEvent.CropChanged(it)) },
        maxHeight = windowHeight() * CROP_MAX_HEIGHT_FRACTION,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        PhotoAspect.entries.forEach { aspect ->
            FilterChip(
                selected = crop.aspect == aspect,
                onClick = { actions.onPhoto(BiodataPhotoEvent.Aspect(aspect)) },
                label = { Text(aspectLabel(aspect)) },
                modifier = Modifier
                    .heightIn(min = Dimens.MinTouchTarget)
                    .testTag("biodata_aspect_${aspect.name.lowercase()}"),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Button(
            onClick = { actions.onPhoto(BiodataPhotoEvent.Confirm) },
            enabled = !busy,
            modifier = Modifier
                .heightIn(min = Dimens.MinTouchTarget)
                .testTag("biodata_crop_use"),
        ) { Text(stringResource(R.string.biodata_crop_use)) }
        TextButton(
            onClick = { actions.onPhoto(BiodataPhotoEvent.Cancel) },
            modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
        ) { Text(stringResource(R.string.resume_cancel)) }
    }
}

@Composable
private fun aspectLabel(aspect: PhotoAspect): String = stringResource(
    when (aspect) {
        PhotoAspect.PORTRAIT -> R.string.biodata_aspect_portrait
        PhotoAspect.SQUARE -> R.string.biodata_aspect_square
        PhotoAspect.LANDSCAPE -> R.string.biodata_aspect_landscape
        PhotoAspect.ORIGINAL -> R.string.biodata_aspect_original
    },
)

/** The biodata as the real PDF, made again a moment after each edit. */
@Composable
private fun LivePreview(state: BiodataUiState, modifier: Modifier) {
    Column(modifier.testTag("biodata_preview"), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        if (state.biodata?.photos.isNullOrEmpty()) {
            Text(stringResource(R.string.biodata_preview_need_photo), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        Text(
            stringResource(R.string.biodata_preview_live),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (val job = state.preview) {
            is JobState.Failed -> ErrorCard(job.error, onRetry = null)
            is JobState.Running, JobState.Idle, is JobState.Cancelled -> if (state.previewFile == null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            is JobState.Success -> Unit
        }
        state.previewFile?.let { file ->
            PdfPagesColumn(file.path, Modifier.fillMaxWidth(), maxPages = PREVIEW_PAGES)
        }
    }
}

@Composable
private fun BiodataExportBar(job: JobState<OutputFile>, actions: BiodataEditorActions) {
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
                        .testTag("biodata_export_pdf"),
                ) { Text(stringResource(R.string.resume_export_pdf)) }
                OutlinedButton(
                    onClick = { actions.onExport(DocumentFormat.DOCX) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Dimens.PrimaryButtonHeight)
                        .testTag("biodata_export_docx"),
                ) { Text(stringResource(R.string.resume_export_docx)) }
            }
        }
    }
}

@Composable
private fun BiodataExportResult(output: OutputFile, actions: BiodataEditorActions, modifier: Modifier) {
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(output.mimeType)) { uri ->
        if (uri != null) actions.onSave(uri.toString())
    }
    val isPdf = output.mimeType == DocumentFormat.PDF.mimeType
    SectionCard(modifier.testTag("biodata_export_result")) {
        SectionHeader(
            stringResource(
                R.string.resume_export_ready,
                stringResource(if (isPdf) R.string.resume_export_pdf_name else R.string.resume_export_docx_name),
            ),
        )
        Text(
            formatBytes(output.sizeBytes),
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
    }
}

/** Decodes a stored photo at thumbnail size. */
private fun decodeThumbnail(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_DECODE_PX) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

private const val TEMPLATE_MIN_WIDTH = 160
private const val PAGE_ASPECT = 210f / 297f
private const val SKETCH_INK = 0xFF2B2B2B
private const val FRAME_INSET = 0.04f
private const val INNER_FRAME = 0.6f
private const val CORNER = 0.14f
private const val PAD = 0.11f
private const val LINE = 3f
private const val TITLE_SIDE = 0.2f
private const val TITLE_GAP = 0.07f
private const val PHOTO_SHARE = 0.32f
private const val PROFILE_SHARE = 0.22f
private const val SHOWCASE = 0.55f
private const val GRID_ROW = 0.3f
private const val PORTRAIT = 3f / 4f
private const val DETAIL_WIDTH = 0.55f
private const val BOTTOM_LINES = 4
private const val TITLE_THICK = 1.6f
private const val DETAIL_LINES = 6
private const val LINE_STEP = 0.035f
private const val HEADING_WIDTH = 0.35f
private const val TEXT_ALPHA = 0.45f
private const val PHOTO_ALPHA = 0.35f
private const val CROP_MAX_HEIGHT_FRACTION = 0.45f
private const val PREVIEW_PAGES = 4
private const val THUMB_DECODE_PX = 256
private const val DEFAULT_AGE_YEARS = 25L

private val PreviewEditorActions = BiodataEditorActions({}, {}, {}, {}, {}, {}, {}, {}, {})

@AllPreviews
@Composable
private fun BiodataTemplatesPreview() {
    MffTheme(dynamicColor = false) {
        BiodataTemplatesScreen(current = null, onlyKind = null, onChoose = {}, onBack = {})
    }
}

@AllPreviews
@Composable
private fun BiodataEditorPreview() {
    val biodata = remember { Biodata(id = "p", templateId = BiodataTemplates.TRADITIONAL.id, name = "Asha Rao") }
    MffTheme(dynamicColor = false) {
        BiodataEditorScreen(BiodataUiState(page = BiodataPage.EDITOR, biodata = biodata), PreviewEditorActions)
    }
}
