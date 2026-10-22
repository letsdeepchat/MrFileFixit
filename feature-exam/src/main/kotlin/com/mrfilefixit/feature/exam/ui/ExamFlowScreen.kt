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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mrfilefixit.core.domain.exam.DocumentKind
import com.mrfilefixit.core.domain.exam.DocumentSpec
import com.mrfilefixit.core.domain.exam.ExamProfile
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.ads.LocalAdSlots
import com.mrfilefixit.core.ui.components.CropBox
import com.mrfilefixit.core.ui.components.ErrorCard
import com.mrfilefixit.core.ui.components.ExamDisclaimerCard
import com.mrfilefixit.core.ui.components.JobProgressCard
import com.mrfilefixit.core.ui.components.SpecChecklist
import com.mrfilefixit.core.ui.components.boundsText
import com.mrfilefixit.core.ui.components.displayName
import com.mrfilefixit.core.ui.format.formatNumber
import com.mrfilefixit.core.ui.preview.AllPreviews
import com.mrfilefixit.core.ui.share.findActivity
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.theme.MffTheme
import com.mrfilefixit.core.ui.thumbnail.Thumbnail
import com.mrfilefixit.feature.exam.R

/** Callbacks from the exam flow UI to its ViewModel. */
internal interface ExamFlowActions {
    fun onPick(key: SlotKey, uri: String)
    fun onCrop(key: SlotKey, rect: NormalizedRect)
    fun onRotate(key: SlotKey)
    fun onFix(key: SlotKey)
    fun onCancel(key: SlotKey)
    fun onReset(key: SlotKey)
    fun onRevealed(key: SlotKey)
    fun onSave(key: SlotKey, destination: String)
    fun onShare(key: SlotKey)
    fun onMakeComposite()
    fun onRetryLoad()
}

@Composable
internal fun ExamFlowScreen(state: ExamFlowUiState, actions: ExamFlowActions, modifier: Modifier = Modifier) {
    val profile = state.profile
    when {
        state.loading -> Box(modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        state.loadError != null -> ErrorCard(state.loadError, actions::onRetryLoad, modifier.padding(Dimens.SpaceM))
        profile != null -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(Dimens.SpaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { ProfileHeader(profile) }
            item { ExamDisclaimerCard(Modifier.widthIn(max = Dimens.CardMaxWidth)) }
            val documentSlots = state.slots.filterKeys { it is SlotKey.Document }.toList()
            items(documentSlots, key = { it.first.toString() }) { (key, slot) ->
                SlotCard(key, slot, actions, Modifier.widthIn(max = Dimens.CardMaxWidth))
            }
            state.slots[SlotKey.Composite]?.let { composite ->
                item(key = "composite") {
                    val ready = state.slots[SlotKey.Document(DocumentKind.PHOTO)]?.source != null &&
                        state.slots[SlotKey.Document(DocumentKind.SIGNATURE)]?.source != null
                    CompositeCard(composite, ready, actions, Modifier.widthIn(max = Dimens.CardMaxWidth))
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: ExamProfile) {
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = Dimens.CardMaxWidth),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        Text(profile.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(profile.conductingBody, style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            VerificationChip(profile)
            // Opens the official site in the user's browser. The app itself never fetches anything.
            TextButton(onClick = {
                uriHandler.openUri(profile.sourceUrl)
            }, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
                Icon(Icons.Filled.Public, contentDescription = null)
                Text(
                    stringResource(CoreR.string.action_open_source),
                    modifier = Modifier.padding(start = Dimens.SpaceXs),
                )
            }
        }
        profile.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SlotCard(key: SlotKey, slot: SlotUiState, actions: ExamFlowActions, modifier: Modifier = Modifier) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { actions.onPick(key, it.toString()) }
    }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.SpaceM), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            Text(
                documentLabel(slot.spec.kind),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics {
                    heading()
                },
            )
            Text(requirementsText(slot.spec), style = MaterialTheme.typography.bodyMedium)
            slot.spec.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            slot.inputError?.let { ErrorCard(it, onRetry = null) }
            val source = slot.source
            val info = slot.sourceInfo
            if (source == null || info == null) {
                Button(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                ) { Text(stringResource(CoreR.string.action_choose_image)) }
                return@Column
            }

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
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.SpaceM), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            Text(stringResource(R.string.exam_composite_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.exam_composite_body), style = MaterialTheme.typography.bodyMedium)
            Text(requirementsText(slot.spec), style = MaterialTheme.typography.bodySmall)
            JobSection(SlotKey.Composite, slot, actions) {
                Button(
                    onClick = actions::onMakeComposite,
                    enabled = ready,
                    modifier = Modifier.heightIn(min = Dimens.MinTouchTarget),
                ) { Text(stringResource(R.string.exam_composite_button)) }
            }
        }
    }
}

/**
 * Shows [idleContent] until a job runs, then progress, then (after the interstitial gate) the result.
 */
@Composable
private fun JobSection(key: SlotKey, slot: SlotUiState, actions: ExamFlowActions, idleContent: @Composable () -> Unit) {
    val context = LocalContext.current
    val adSlots = LocalAdSlots.current
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
        is JobState.Success -> {
            if (!slot.resultRevealed) {
                // Interstitial (if any) between "done" and the result. Never blocks: the gate has its own timeout.
                LaunchedEffect(job) {
                    context.findActivity()?.let { adSlots.beforeResult(it) }
                    actions.onRevealed(key)
                }
                JobProgressCard(1f, onCancel = {})
            } else {
                ResultSection(key, slot, actions)
            }
        }
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
        DocumentKind.SIGNATURE -> R.string.exam_doc_SIGNATURE
        DocumentKind.LEFT_THUMB -> R.string.exam_doc_LEFT_THUMB
        DocumentKind.DECLARATION -> R.string.exam_doc_DECLARATION
        DocumentKind.OTHER -> R.string.exam_doc_OTHER
    },
)

@Composable
private fun requirementsText(spec: DocumentSpec): String = stringResource(
    R.string.exam_requirements,
    spec.formats.sorted().joinToString(" / ") { it.displayName() },
    stringResource(CoreR.string.range_kb, formatNumber(spec.minKb), formatNumber(spec.maxKb)),
    boundsText(spec.width),
    boundsText(spec.height),
)

private object NoOpActions : ExamFlowActions {
    override fun onPick(key: SlotKey, uri: String) = Unit
    override fun onCrop(key: SlotKey, rect: NormalizedRect) = Unit
    override fun onRotate(key: SlotKey) = Unit
    override fun onFix(key: SlotKey) = Unit
    override fun onCancel(key: SlotKey) = Unit
    override fun onReset(key: SlotKey) = Unit
    override fun onRevealed(key: SlotKey) = Unit
    override fun onSave(key: SlotKey, destination: String) = Unit
    override fun onShare(key: SlotKey) = Unit
    override fun onMakeComposite() = Unit
    override fun onRetryLoad() = Unit
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
