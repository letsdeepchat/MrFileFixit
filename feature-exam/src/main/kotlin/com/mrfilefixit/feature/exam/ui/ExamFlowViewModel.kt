package com.mrfilefixit.feature.exam.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrfilefixit.core.domain.dispatchers.ApplicationScope
import com.mrfilefixit.core.domain.exam.CheckDocumentUseCase
import com.mrfilefixit.core.domain.exam.DocumentKind
import com.mrfilefixit.core.domain.exam.DocumentSpec
import com.mrfilefixit.core.domain.exam.ExamProfile
import com.mrfilefixit.core.domain.exam.GetExamProfileUseCase
import com.mrfilefixit.core.domain.exam.MergePhotoSignatureUseCase
import com.mrfilefixit.core.domain.exam.PrepareExamDocumentUseCase
import com.mrfilefixit.core.domain.exam.PreparedDocument
import com.mrfilefixit.core.domain.exam.SpecReport
import com.mrfilefixit.core.domain.file.DescribeFileUseCase
import com.mrfilefixit.core.domain.file.DestinationUri
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.JobIdGenerator
import com.mrfilefixit.core.domain.file.SaveOutputUseCase
import com.mrfilefixit.core.domain.file.ShareOutputUseCase
import com.mrfilefixit.core.domain.file.TempFileStore
import com.mrfilefixit.core.domain.image.ImageInfo
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.domain.job.JobRunner
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.flatMap
import com.mrfilefixit.core.domain.result.fold
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Identifies an upload slot: one document kind, or the combined photo + signature image. */
sealed interface SlotKey {
    data class Document(val kind: DocumentKind) : SlotKey

    data object Composite : SlotKey
}

data class SlotUiState(
    val spec: DocumentSpec,
    val source: FileRef? = null,
    val sourceInfo: ImageInfo? = null,
    val sourceReport: SpecReport? = null,
    val crop: NormalizedRect? = null,
    val rotation: Int = 0,
    val job: JobState<PreparedDocument> = JobState.Idle,
    val inputError: AppError? = null,
    val saved: Boolean = false,
) {
    val result: PreparedDocument? get() = (job as? JobState.Success)?.value
}

data class ExamFlowUiState(
    val loading: Boolean = true,
    val profile: ExamProfile? = null,
    val loadError: AppError? = null,
    val slots: Map<SlotKey, SlotUiState> = emptyMap(),
)

sealed interface ExamEffect {
    data class Share(val uri: String, val mimeType: String) : ExamEffect

    data class ShowError(val error: AppError) : ExamEffect
}

/**
 * Drives the photo/signature flow for one exam. One [JobRunner] per slot, so photo and signature can be
 * prepared independently, and each slot's output lives in its own temp job directory.
 */
@HiltViewModel(assistedFactory = ExamFlowViewModel.Factory::class)
class ExamFlowViewModel @AssistedInject constructor(
    @Assisted private val profileId: String,
    private val getProfile: GetExamProfileUseCase,
    private val describeFile: DescribeFileUseCase,
    private val checkDocument: CheckDocumentUseCase,
    private val prepareDocument: PrepareExamDocumentUseCase,
    private val mergePhotoSignature: MergePhotoSignatureUseCase,
    private val saveOutput: SaveOutputUseCase,
    private val shareOutput: ShareOutputUseCase,
    private val tempFiles: TempFileStore,
    private val jobIds: JobIdGenerator,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(profileId: String): ExamFlowViewModel
    }

    private val mutableState = MutableStateFlow(ExamFlowUiState())
    val state: StateFlow<ExamFlowUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<ExamEffect>(Channel.BUFFERED)
    val effects: Flow<ExamEffect> = effectChannel.receiveAsFlow()

    private val runners = mutableMapOf<SlotKey, JobRunner<PreparedDocument>>()
    private val jobDirs = mutableMapOf<SlotKey, String>()

    init {
        load()
    }

    fun load() {
        mutableState.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            getProfile(profileId).fold(
                onSuccess = { profile -> onProfileLoaded(profile) },
                onFailure = { error -> mutableState.update { it.copy(loading = false, loadError = error) } },
            )
        }
    }

    private fun onProfileLoaded(profile: ExamProfile) {
        val slots = buildMap<SlotKey, SlotUiState> {
            profile.documents.forEach { put(SlotKey.Document(it.kind), SlotUiState(it)) }
            profile.composite?.let { put(SlotKey.Composite, SlotUiState(it.document)) }
        }
        slots.keys.forEach { key -> runnerFor(key) }
        mutableState.update { it.copy(loading = false, profile = profile, slots = slots) }
    }

    private fun runnerFor(key: SlotKey): JobRunner<PreparedDocument> = runners.getOrPut(key) {
        JobRunner<PreparedDocument>(viewModelScope).also { runner ->
            viewModelScope.launch {
                runner.state.collect { job ->
                    updateSlot(key) { slot -> slot.copy(job = job) }
                }
            }
        }
    }

    fun onFilePicked(key: SlotKey, uri: String) {
        val slot = mutableState.value.slots[key] ?: return
        runners[key]?.cancel()
        runners[key]?.reset()
        discardJobDir(key)
        updateSlot(key) { SlotUiState(spec = it.spec) }
        viewModelScope.launch {
            val checked = describeFile(uri).flatMap { ref ->
                checkDocument(ref, slot.spec).fold(
                    onSuccess = { (info, report) -> Result.Success(Triple(ref, info, report)) },
                    onFailure = { Result.Failure(it) },
                )
            }
            when (checked) {
                is Result.Success -> {
                    val (ref, info, report) = checked.value
                    updateSlot(key) { it.copy(source = ref, sourceInfo = info, sourceReport = report) }
                }
                is Result.Failure -> updateSlot(key) { it.copy(inputError = checked.error) }
            }
        }
    }

    fun onCropChanged(key: SlotKey, rect: NormalizedRect) = updateSlot(key) { it.copy(crop = rect) }

    fun onRotate(key: SlotKey) = updateSlot(key) {
        it.copy(rotation = (it.rotation + QUARTER_TURN) % FULL_TURN, crop = null)
    }

    fun onFix(key: SlotKey) {
        val slot = mutableState.value.slots[key] ?: return
        val source = slot.source ?: return
        val jobId = jobIds.next()
        discardJobDir(key)
        updateSlot(key) { it.copy(saved = false) }
        runnerFor(key).start(cleanup = { cleanupIfNotSuccessful(key, jobId) }) { progress ->
            tempFiles.createJobDir(jobId).flatMap { dir ->
                jobDirs[key] = jobId
                prepareDocument(
                    source = source,
                    spec = slot.spec,
                    crop = slot.crop,
                    rotationDegrees = slot.rotation,
                    outputDir = dir,
                    baseName = slot.spec.kind.name.lowercase(),
                    progress = progress,
                )
            }
        }
    }

    /** Builds the combined image from the picked photo and signature (each centre-cropped to its band). */
    fun onMakeComposite() {
        val current = mutableState.value
        val composite = current.profile?.composite ?: return
        val photo = current.slots[SlotKey.Document(DocumentKind.PHOTO)]?.source ?: return
        val signature = current.slots[SlotKey.Document(DocumentKind.SIGNATURE)]?.source ?: return
        val jobId = jobIds.next()
        discardJobDir(SlotKey.Composite)
        updateSlot(SlotKey.Composite) { it.copy(saved = false) }
        runnerFor(SlotKey.Composite).start(cleanup = { cleanupIfNotSuccessful(SlotKey.Composite, jobId) }) { progress ->
            tempFiles.createJobDir(jobId).flatMap { dir ->
                jobDirs[SlotKey.Composite] = jobId
                mergePhotoSignature(photo, signature, composite, dir, "photo_signature", progress)
            }
        }
    }

    fun onCancel(key: SlotKey) {
        runners[key]?.cancel()
    }

    fun onReset(key: SlotKey) {
        runners[key]?.cancel()
        runners[key]?.reset()
        discardJobDir(key)
        updateSlot(key) { SlotUiState(spec = it.spec) }
    }

    fun onSave(key: SlotKey, destination: String) {
        val output = mutableState.value.slots[key]?.result?.image?.file ?: return
        viewModelScope.launch {
            saveOutput(output, DestinationUri(destination)).fold(
                onSuccess = { updateSlot(key) { it.copy(saved = true) } },
                onFailure = { effectChannel.send(ExamEffect.ShowError(it)) },
            )
        }
    }

    fun onShare(key: SlotKey) {
        val output = mutableState.value.slots[key]?.result?.image?.file ?: return
        viewModelScope.launch {
            shareOutput(output).fold(
                onSuccess = { uri -> effectChannel.send(ExamEffect.Share(uri, output.mimeType)) },
                onFailure = { effectChannel.send(ExamEffect.ShowError(it)) },
            )
        }
    }

    private suspend fun cleanupIfNotSuccessful(key: SlotKey, jobId: String) {
        if (runners[key]?.state?.value !is JobState.Success) {
            tempFiles.deleteJobDir(jobId)
            if (jobDirs[key] == jobId) jobDirs.remove(key)
        }
    }

    private fun discardJobDir(key: SlotKey) {
        val jobId = jobDirs.remove(key) ?: return
        appScope.launch { tempFiles.deleteJobDir(jobId) }
    }

    private inline fun updateSlot(key: SlotKey, crossinline transform: (SlotUiState) -> SlotUiState) {
        mutableState.update { state ->
            val slot = state.slots[key] ?: return@update state
            state.copy(slots = state.slots + (key to transform(slot)))
        }
    }

    override fun onCleared() {
        // Outputs live only as long as the screen; anything not saved or shared is deleted.
        val dirs = jobDirs.values.toList()
        jobDirs.clear()
        appScope.launch { dirs.forEach { tempFiles.deleteJobDir(it) } }
        super.onCleared()
    }

    private companion object {
        const val QUARTER_TURN = 90
        const val FULL_TURN = 360
    }
}
