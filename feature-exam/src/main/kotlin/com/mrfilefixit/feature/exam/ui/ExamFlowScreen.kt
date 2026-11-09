package com.mrfilefixit.feature.exam.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mrfilefixit.core.domain.exam.CertificateFormat
import com.mrfilefixit.core.domain.exam.CertificateSpec
import com.mrfilefixit.core.domain.exam.DocumentKind
import com.mrfilefixit.core.domain.exam.DocumentSpec
import com.mrfilefixit.core.domain.exam.ExamProfile
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.components.CropBox
import com.mrfilefixit.core.ui.components.ErrorCard
import com.mrfilefixit.core.ui.components.ExamDisclaimerCard
import com.mrfilefixit.core.ui.components.FullLabelTabRow
import com.mrfilefixit.core.ui.components.JobProgressCard
import com.mrfilefixit.core.ui.components.PrimaryActionButton
import com.mrfilefixit.core.ui.components.SectionCard
import com.mrfilefixit.core.ui.components.SpecChecklist
import com.mrfilefixit.core.ui.components.TabItem
import com.mrfilefixit.core.ui.components.boundsText
import com.mrfilefixit.core.ui.components.displayName
import com.mrfilefixit.core.ui.format.formatIsoDate
import com.mrfilefixit.core.ui.preview.AllPreviews
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.theme.MffTheme
import com.mrfilefixit.core.ui.thumbnail.Thumbnail
import com.mrfilefixit.feature.exam.R

/** Callbacks from the exam flow UI to its ViewModel (and, for the tool shortcuts, to navigation). */
internal interface ExamFlowActions {
    fun onPick(key: SlotKey, uri: String)
    fun onCrop(key: SlotKey, rect: NormalizedRect)
    fun onRotate(key: SlotKey)
    fun onFix(key: SlotKey)
    fun onCancel(key: SlotKey)
    fun onReset(key: SlotKey)
    fun onSave(key: SlotKey, destination: String)
    fun onShare(key: SlotKey)
    fun onMakeComposite()
    fun onRetryLoad()

    /** Opens Image tools with this exam's size, pixel and KB limits for [kind] already set. */
    fun onPrepareInImageTools(kind: DocumentKind)

    /** Opens PDF Compress with [certificate]'s KB range as the target. */
    fun onCompressPdf(certificate: CertificateSpec)
}

/**
 * Detail tabs, one per upload type the exam asks for. Photo and signature always come first; Other Documents
 * also holds the photo-and-signature composite; Certificates is always shown so users see its status.
 */
internal enum class ExamTab(val kinds: Set<DocumentKind>) {
    PHOTO(setOf(DocumentKind.PHOTO, DocumentKind.POSTCARD_PHOTO)),
    SIGNATURE(setOf(DocumentKind.SIGNATURE)),
    LEFT_THUMB(setOf(DocumentKind.LEFT_THUMB)),
    DECLARATION(setOf(DocumentKind.DECLARATION)),
    OTHER(setOf(DocumentKind.OTHER)),
    CERTIFICATES(emptySet()),
}

internal fun tabsFor(profile: ExamProfile): List<ExamTab> = ExamTab.entries.filter { tab ->
    when (tab) {
        ExamTab.CERTIFICATES -> true
        ExamTab.OTHER -> profile.composite != null || profile.documents.any { it.kind in tab.kinds }
        else -> profile.documents.any { it.kind in tab.kinds }
    }
}

@Composable
internal fun ExamFlowScreen(state: ExamFlowUiState, actions: ExamFlowActions, modifier: Modifier = Modifier) {
    val profile = state.profile
    when {
        state.loading -> Box(modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        state.loadError != null -> ErrorCard(state.loadError, actions::onRetryLoad, modifier.padding(Dimens.SpaceM))
        profile != null -> ExamDetail(profile, state, actions, modifier)
    }
}

@Composable
private fun ExamDetail(profile: ExamProfile, state: ExamFlowUiState, actions: ExamFlowActions, modifier: Modifier) {
    val tabs = tabsFor(profile)
    var tabIndex by rememberSaveable(profile.id) { mutableIntStateOf(0) }
    val tab = tabs[tabIndex.coerceIn(tabs.indices)]
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("exam_detail"),
        contentPadding = PaddingValues(bottom = Dimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "header") {
            ProfileHeader(
                profile,
                onPrepare = {
                    val kind = profile.documents.firstOrNull { it.kind in tab.kinds }?.kind ?: DocumentKind.PHOTO
                    actions.onPrepareInImageTools(kind)
                },
                modifier = Modifier.sectionWidth(),
            )
        }
        item(key = "disclaimer") { ExamDisclaimerCard(Modifier.sectionWidth()) }
        item(key = "tabs") {
            // Full names on one line ("Left Thumb Impression", "Handwritten Declaration"); the row scrolls.
            FullLabelTabRow(
                tabs = tabs.map { TabItem(tabLabel(it), "exam_tab_${it.name.lowercase()}") },
                selectedIndex = tabs.indexOf(tab),
                onSelect = { tabIndex = it },
                modifier = Modifier.widthIn(max = Dimens.CardMaxWidth),
            )
        }
        if (tab == ExamTab.CERTIFICATES) {
            certificateItems(profile, actions)
        } else {
            val documentSlots = state.slots.filterKeys { it is SlotKey.Document && it.kind in tab.kinds }.toList()
            items(documentSlots, key = { it.first.toString() }) { (key, slot) ->
                SlotCard(key, slot, actions, Modifier.sectionWidth())
            }
            val composite = state.slots[SlotKey.Composite]
            if (tab == ExamTab.OTHER && composite != null) {
                item(key = "composite") {
                    val ready = state.slots[SlotKey.Document(DocumentKind.PHOTO)]?.source != null &&
                        state.slots[SlotKey.Document(DocumentKind.SIGNATURE)]?.source != null
                    CompositeCard(composite, ready, actions, Modifier.sectionWidth())
                }
            }
        }
    }
}

private fun Modifier.sectionWidth(): Modifier = this
    .padding(horizontal = Dimens.ScreenPadding)
    .widthIn(max = Dimens.CardMaxWidth)
    .fillMaxWidth()

private fun LazyListScope.certificateItems(profile: ExamProfile, actions: ExamFlowActions) {
    if (profile.certificates.isEmpty()) {
        item(key = "no-certificates") {
            Text(
                stringResource(R.string.exam_no_certificates),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.sectionWidth(),
            )
        }
    }
    items(profile.certificates, key = { "cert-${it.name}" }) { certificate ->
        CertificateCard(certificate, onCompress = { actions.onCompressPdf(certificate) }, Modifier.sectionWidth())
    }
}

@Composable
private fun CertificateCard(certificate: CertificateSpec, onCompress: () -> Unit, modifier: Modifier = Modifier) {
    SectionCard(modifier) {
        Text(
            certificate.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        RequirementRow(stringResource(R.string.exam_req_format), certificateFormats(listOf(certificate)))
        RequirementRow(stringResource(R.string.exam_req_size), kbRange(certificate))
        certificate.notes?.let { RequirementRow(stringResource(R.string.exam_req_notes), it) }
        if (CertificateFormat.PDF in certificate.formats) {
            OutlinedButton(
                onClick = onCompress,
                shape = RoundedCornerShape(Dimens.ButtonCorner),
                modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
            ) {
                Icon(Icons.Filled.Compress, contentDescription = null)
                Text(stringResource(R.string.exam_compress_pdf), Modifier.padding(start = Dimens.SpaceS))
            }
        }
    }
}

/** Label on the start side, value on the end; read by TalkBack as one "label: value" node. */
@Composable
private fun RequirementRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(REQUIREMENT_LABEL_WEIGHT),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RequirementTable(spec: DocumentSpec) {
    RequirementRow(
        stringResource(R.string.exam_req_dimensions),
        stringResource(R.string.exam_dimensions_value, boundsText(spec.width), boundsText(spec.height)),
    )
    RequirementRow(stringResource(R.string.exam_req_size), kbRange(spec))
    RequirementRow(
        stringResource(R.string.exam_req_format),
        spec.formats.sorted().joinToString(" / ") { it.displayName() },
    )
    spec.background?.let { RequirementRow(stringResource(R.string.exam_req_background), it) }
    spec.ink?.let { RequirementRow(stringResource(R.string.exam_req_ink), it) }
    spec.notes?.let { RequirementRow(stringResource(R.string.exam_req_notes), it) }
}

@Composable
private fun tabLabel(tab: ExamTab): String = stringResource(
    when (tab) {
        ExamTab.PHOTO -> R.string.exam_tab_photo
        ExamTab.SIGNATURE -> R.string.exam_tab_signature
        ExamTab.LEFT_THUMB -> R.string.exam_tab_left_thumb
        ExamTab.DECLARATION -> R.string.exam_tab_declaration
        ExamTab.OTHER -> R.string.exam_tab_other
        ExamTab.CERTIFICATES -> R.string.exam_tab_certificates
    },
)

@Composable
private fun ProfileHeader(profile: ExamProfile, onPrepare: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = Dimens.SpaceS), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Text(profile.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            Tag(
                profile.conductingBody,
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.onSurface,
            )
            VerificationTag(profile)
        }
        Text(
            profile.lastVerified?.let { stringResource(CoreR.string.last_verified, formatIsoDate(it)) }
                ?: stringResource(CoreR.string.last_verified_never),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        profile.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        PrimaryActionButton(stringResource(R.string.exam_prepare_files), onClick = onPrepare)
        Text(
            stringResource(R.string.exam_prepare_files_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val REQUIREMENT_LABEL_WEIGHT = 0.4f

@Composable
private fun SlotCard(key: SlotKey, slot: SlotUiState, actions: ExamFlowActions, modifier: Modifier = Modifier) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { actions.onPick(key, it.toString()) }
    }
    SectionCard(modifier) {
        Text(
            documentLabel(slot.spec.kind),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        RequirementTable(slot.spec)

        slot.inputError?.let { ErrorCard(it, onRetry = null) }
        val source = slot.source
        val info = slot.sourceInfo
        if (source == null || info == null) {
            PrimaryActionButton(
                stringResource(R.string.exam_choose_for, documentLabel(slot.spec.kind)),
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.padding(top = Dimens.SpaceS),
            )
        } else {
            slot.sourceReport?.let {
                Text(stringResource(R.string.exam_your_file), style = MaterialTheme.typography.titleSmall)
                SpecChecklist(it)
            }
            JobSection(key, slot, actions) {
                val rotated = slot.rotation % 180 != 0
                val aspect = if (rotated) info.height.toFloat() / info.width else info.width.toFloat() / info.height
                CropBox(
                    uri = source.uri,
                    imageAspect = aspect,
                    frameAspect = slot.spec.targetWidth.toFloat() / slot.spec.targetHeight,
                    onCropChanged = { actions.onCrop(key, it) },
                )
                Text(stringResource(R.string.exam_crop_hint), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                    OutlinedButton(onClick = {
                        actions.onRotate(key)
                    }, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
                        Icon(Icons.Filled.Rotate90DegreesCw, contentDescription = null)
                        Text(
                            stringResource(CoreR.string.action_rotate),
                            modifier = Modifier.padding(start = Dimens.SpaceXs),
                        )
                    }
                    Button(onClick = {
                        actions.onFix(key)
                    }, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
                        Text(stringResource(R.string.exam_fix_button))
                    }
                    TextButton(onClick = {
                        actions.onReset(key)
                    }, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
                        Text(stringResource(CoreR.string.action_reset))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompositeCard(slot: SlotUiState, ready: Boolean, actions: ExamFlowActions, modifier: Modifier = Modifier) {
    SectionCard(modifier) {
        Text(
            stringResource(R.string.exam_composite_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.exam_composite_body), style = MaterialTheme.typography.bodyMedium)
        RequirementTable(slot.spec)
        JobSection(SlotKey.Composite, slot, actions) {
            PrimaryActionButton(
                stringResource(R.string.exam_composite_button),
                onClick = actions::onMakeComposite,
                enabled = ready,
            )
        }
    }
}

/**
 * Shows [idleContent] until a job runs, then progress, then the result.
 */
@Composable
private fun JobSection(key: SlotKey, slot: SlotUiState, actions: ExamFlowActions, idleContent: @Composable () -> Unit) {
    when (val job = slot.job) {
        JobState.Idle -> idleContent()
        JobState.Cancelled -> {
            Text(stringResource(CoreR.string.job_cancelled), style = MaterialTheme.typography.bodyMedium)
            idleContent()
        }
        is JobState.Running -> JobProgressCard(job.progress, onCancel = { actions.onCancel(key) })
        is JobState.Failed -> {
            ErrorCard(job.error, onRetry = { actions.onFix(key) })
            idleContent()
        }
        is JobState.Success -> ResultSection(key, slot, actions)
    }
}

@Composable
private fun ResultSection(key: SlotKey, slot: SlotUiState, actions: ExamFlowActions) {
    val result = slot.result ?: return
    val file = result.image.file
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(file.mimeType)) { uri ->
        uri?.let { actions.onSave(key, it.toString()) }
    }
    val passed = result.report.passed
    Text(
        stringResource(if (passed) R.string.exam_result_heading else R.string.exam_result_failed_heading),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.semantics { heading() },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM), verticalAlignment = Alignment.Top) {
        Thumbnail(
            uri = android.net.Uri.fromFile(java.io.File(file.path)).toString(),
            contentDescription = stringResource(CoreR.string.cd_thumbnail),
            size = Dimens.ThumbnailSize,
        )
        SpecChecklist(result.report, Modifier.weight(1f))
    }
    if (result.image.padded) Text(stringResource(R.string.exam_padded_note), style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Button(onClick = {
            saver.launch(file.displayName)
        }, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
            Text(stringResource(if (slot.saved) R.string.exam_saved else CoreR.string.action_save))
        }
        FilledTonalButton(onClick = {
            actions.onShare(key)
        }, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
            Text(stringResource(CoreR.string.action_share))
        }
        TextButton(onClick = { actions.onReset(key) }, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
            Text(stringResource(CoreR.string.action_reset))
        }
    }
}

@Composable
internal fun documentLabel(kind: DocumentKind): String = stringResource(
    when (kind) {
        DocumentKind.PHOTO -> R.string.exam_doc_PHOTO
        DocumentKind.POSTCARD_PHOTO -> R.string.exam_doc_POSTCARD_PHOTO
        DocumentKind.SIGNATURE -> R.string.exam_doc_SIGNATURE
        DocumentKind.LEFT_THUMB -> R.string.exam_doc_LEFT_THUMB
        DocumentKind.DECLARATION -> R.string.exam_doc_DECLARATION
        DocumentKind.OTHER -> R.string.exam_doc_OTHER
    },
)

private object NoOpActions : ExamFlowActions {
    override fun onPick(key: SlotKey, uri: String) = Unit
    override fun onCrop(key: SlotKey, rect: NormalizedRect) = Unit
    override fun onRotate(key: SlotKey) = Unit
    override fun onFix(key: SlotKey) = Unit
    override fun onCancel(key: SlotKey) = Unit
    override fun onReset(key: SlotKey) = Unit
    override fun onSave(key: SlotKey, destination: String) = Unit
    override fun onShare(key: SlotKey) = Unit
    override fun onMakeComposite() = Unit
    override fun onRetryLoad() = Unit
    override fun onPrepareInImageTools(kind: DocumentKind) = Unit
    override fun onCompressPdf(certificate: CertificateSpec) = Unit
}

@AllPreviews
@Composable
private fun ExamFlowPreview() {
    val profile = PreviewProfiles.first()
    MffTheme(dynamicColor = false) {
        ExamFlowScreen(
            state = ExamFlowUiState(
                loading = false,
                profile = profile,
                slots = profile.documents.associate { SlotKey.Document(it.kind) to SlotUiState(it) },
            ),
            actions = NoOpActions,
        )
    }
}
