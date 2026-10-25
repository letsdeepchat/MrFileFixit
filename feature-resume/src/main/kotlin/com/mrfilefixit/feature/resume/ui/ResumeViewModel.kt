package com.mrfilefixit.feature.resume.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrfilefixit.core.domain.convert.DocumentFormat
import com.mrfilefixit.core.domain.dispatchers.ApplicationScope
import com.mrfilefixit.core.domain.file.DescribeFileUseCase
import com.mrfilefixit.core.domain.file.DestinationUri
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.JobIdGenerator
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.file.SaveOutputUseCase
import com.mrfilefixit.core.domain.file.ShareOutputUseCase
import com.mrfilefixit.core.domain.file.TempFileStore
import com.mrfilefixit.core.domain.image.EncodeTarget
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.ImageOp
import com.mrfilefixit.core.domain.image.ImageProcessor
import com.mrfilefixit.core.domain.image.ImageRequest
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.domain.image.OutputSpec
import com.mrfilefixit.core.domain.image.ResizeMode
import com.mrfilefixit.core.domain.job.JobRunner
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.flatMap
import com.mrfilefixit.core.domain.result.fold
import com.mrfilefixit.core.domain.result.getOrNull
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.resume.ExportResumeUseCase
import com.mrfilefixit.core.domain.resume.ImportResumeUseCase
import com.mrfilefixit.core.domain.resume.Resume
import com.mrfilefixit.core.domain.resume.ResumeEdits
import com.mrfilefixit.core.domain.resume.ResumeFactory
import com.mrfilefixit.core.domain.resume.ResumeRepository
import com.mrfilefixit.core.domain.resume.ResumeSummary
import com.mrfilefixit.core.domain.resume.ResumeTemplate
import com.mrfilefixit.core.domain.resume.ResumeTemplates
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The three pages of the Resume tab. */
enum class ResumePage { LIST, TEMPLATES, EDITOR }

/** A picked photo waiting for the user to place it in the 35 × 45 frame. */
data class PhotoCrop(val file: FileRef, val imageAspect: Float, val rect: NormalizedRect = NormalizedRect.FULL)

/** What the user does in the photo card. */
sealed interface PhotoEvent {
    data class Picked(val uri: String) : PhotoEvent

    data class CropChanged(val rect: NormalizedRect) : PhotoEvent

    data object Confirm : PhotoEvent

    data object Cancel : PhotoEvent

    data object Remove : PhotoEvent
}

/** Photo bytes for the editor preview; compared by identity so a new photo always recomposes. */
class PhotoBytes(val bytes: ByteArray)

data class ResumeUiState(
    val page: ResumePage = ResumePage.LIST,
    val drafts: List<ResumeSummary> = emptyList(),
    /** The draft open in the editor. */
    val resume: Resume? = null,
    val photo: PhotoBytes? = null,
    val photoCrop: PhotoCrop? = null,
    val photoBusy: Boolean = false,
    /** Template chooser opened from the editor, to restyle the open draft instead of creating one. */
    val restyling: Boolean = false,
    val export: JobState<OutputFile> = JobState.Idle,
    val error: AppError? = null,
) {
    val template: ResumeTemplate? get() = resume?.let { ResumeTemplates.byId(it.templateId) }
    val exported: OutputFile? get() = (export as? JobState.Success)?.value
}

sealed interface ResumeEffect {
    data class Share(val uri: String, val mimeType: String) : ResumeEffect

    data class ShowError(val error: AppError) : ResumeEffect
}

/**
 * Resume tab: saved drafts, the template gallery and the editor. Every edit is applied to the draft in
 * memory at once and written to disk shortly after (and again when the editor closes), so nothing is lost
 * if the app is closed mid-edit.
 */
@HiltViewModel
class ResumeViewModel @Inject constructor(
    private val repository: ResumeRepository,
    private val exportResume: ExportResumeUseCase,
    private val importResume: ImportResumeUseCase,
    private val describeFile: DescribeFileUseCase,
    private val imageProcessor: ImageProcessor,
    private val saveOutput: SaveOutputUseCase,
    private val shareOutput: ShareOutputUseCase,
    private val tempFiles: TempFileStore,
    private val ids: JobIdGenerator,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ResumeUiState())
    val state: StateFlow<ResumeUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<ResumeEffect>(Channel.BUFFERED)
    val effects: Flow<ResumeEffect> = effectChannel.receiveAsFlow()

    private val runner = JobRunner<OutputFile>(viewModelScope)
    private var exportJobId: String? = null
    private var pendingSave: Job? = null

    init {
        viewModelScope.launch { runner.state.collect { job -> mutableState.update { it.copy(export = job) } } }
        refreshDrafts()
    }

    private fun refreshDrafts() {
        viewModelScope.launch {
            repository.list().fold(
                onSuccess = { list -> mutableState.update { it.copy(drafts = list) } },
                onFailure = { error -> mutableState.update { it.copy(error = error) } },
            )
        }
    }

    // region navigation

    /** Opens the template gallery: to start a new draft, or with [restyle] to restyle the open one. */
    fun onOpenTemplates(restyle: Boolean) =
        mutableState.update { it.copy(page = ResumePage.TEMPLATES, restyling = restyle, error = null) }

    fun onTemplateChosen(template: ResumeTemplate) {
        val current = state.value
        if (current.restyling && current.resume != null) {
            edit { ResumeEdits.applyTemplate(it, template) }
            mutableState.update { it.copy(page = ResumePage.EDITOR, restyling = false) }
            return
        }
        val resume = ResumeFactory.fromTemplate(template, ids.next(), now(), ids::next)
        openEditor(resume, photo = null)
        scheduleSave(immediate = true)
    }

    fun onOpenDraft(id: String) {
        viewModelScope.launch {
            repository.load(id).fold(
                onSuccess = { resume ->
                    val photo = if (resume.hasPhoto) repository.photo(id).getOrNull() else null
                    openEditor(resume, photo?.let(::PhotoBytes))
                },
                onFailure = { error -> effectChannel.send(ResumeEffect.ShowError(error)) },
            )
        }
    }

    fun onImport(uri: String) {
        viewModelScope.launch {
            val result = describeFile(uri).flatMap { importResume(it, ids.next(), now()) }
            when (result) {
                is Result.Success -> {
                    openEditor(result.value, photo = null)
                    refreshDrafts()
                }
                is Result.Failure -> mutableState.update { it.copy(error = result.error) }
            }
        }
    }

    fun onDeleteDraft(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            refreshDrafts()
        }
    }

    /** Back inside the tab. Returns false when the tab is already on its first page. */
    fun onBack(): Boolean {
        val s = state.value
        when {
            s.photoCrop != null -> mutableState.update { it.copy(photoCrop = null) }
            s.page == ResumePage.TEMPLATES && s.restyling -> mutableState.update {
                it.copy(page = ResumePage.EDITOR, restyling = false)
            }
            s.page == ResumePage.TEMPLATES -> mutableState.update { it.copy(page = ResumePage.LIST) }
            s.page == ResumePage.EDITOR -> closeEditor()
            else -> return false
        }
        return true
    }

    private fun openEditor(resume: Resume, photo: PhotoBytes?) {
        resetExport()
        mutableState.update {
            it.copy(
                page = ResumePage.EDITOR,
                resume = resume,
                photo = photo,
                photoCrop = null,
                restyling = false,
                error = null,
            )
        }
    }

    private fun closeEditor() {
        scheduleSave(immediate = true)
        resetExport()
        mutableState.update { it.copy(page = ResumePage.LIST, resume = null, photo = null, photoCrop = null) }
        refreshDrafts()
    }

    fun onDismissError() = mutableState.update { it.copy(error = null) }

    // endregion

    // region editing

    /** Applies [transform] to the open draft and saves it shortly after. Any change invalidates an old export. */
    fun edit(transform: (Resume) -> Resume) {
        val before = state.value.resume ?: return
        val after = transform(before)
        if (after == before) return
        mutableState.update { it.copy(resume = after) }
        if (state.value.export !is JobState.Running) resetExport()
        scheduleSave(immediate = false)
    }

    private fun scheduleSave(immediate: Boolean) {
        val resume = state.value.resume ?: return
        pendingSave?.cancel()
        val write: suspend () -> Unit = {
            repository.save(
                resume.copy(updatedAtMillis = now()),
            ).fold({}, { effectChannel.send(ResumeEffect.ShowError(it)) })
        }
        // An immediate save (leaving the editor) runs in the app scope so it survives the screen closing.
        pendingSave = if (immediate) {
            appScope.launch { write() }
        } else {
            viewModelScope.launch {
                delay(SAVE_DELAY_MS)
                write()
            }
        }
    }

    // endregion

    // region photo

    fun onPhoto(event: PhotoEvent) = when (event) {
        is PhotoEvent.Picked -> photoPicked(event.uri)
        is PhotoEvent.CropChanged -> mutableState.update { s ->
            s.copy(
                photoCrop = s.photoCrop?.copy(rect = event.rect),
            )
        }
        PhotoEvent.Confirm -> photoConfirm()
        PhotoEvent.Cancel -> mutableState.update { it.copy(photoCrop = null) }
        PhotoEvent.Remove -> photoRemove()
    }

    private fun photoPicked(uri: String) {
        viewModelScope.launch {
            mutableState.update { it.copy(photoBusy = true) }
            val picked = describeFile(uri).flatMap { file ->
                imageProcessor.readInfo(file).flatMap { info -> success(file to info) }
            }
            when (picked) {
                is Result.Success -> {
                    val (file, info) = picked.value
                    val aspect = info.width.toFloat() / info.height
                    mutableState.update {
                        it.copy(
                            photoBusy = false,
                            photoCrop = PhotoCrop(
                                file,
                                aspect,
                                NormalizedRect.centeredWithAspect(PHOTO_ASPECT, aspect),
                            ),
                        )
                    }
                }
                is Result.Failure -> {
                    mutableState.update { it.copy(photoBusy = false) }
                    effectChannel.send(ResumeEffect.ShowError(picked.error))
                }
            }
        }
    }

    /** Crops to the 35 × 45 frame, resizes to 413 × 531 px and stores the JPEG with the draft. */
    private fun photoConfirm() {
        val s = state.value
        val crop = s.photoCrop ?: return
        val resume = s.resume ?: return
        val jobId = ids.next()
        mutableState.update { it.copy(photoBusy = true) }
        viewModelScope.launch {
            val result = tempFiles.createJobDir(jobId).flatMap { dir ->
                imageProcessor.process(
                    ImageRequest(
                        source = crop.file,
                        outputDir = dir,
                        outputBaseName = "photo",
                        operations = listOf(
                            ImageOp.Crop(crop.rect),
                            ImageOp.Resize(PHOTO_WIDTH_PX, PHOTO_HEIGHT_PX, ResizeMode.FILL),
                        ),
                        output = OutputSpec(ImageFormat.JPEG, EncodeTarget.Quality(PHOTO_QUALITY)),
                    ),
                )
            }.flatMap { processed -> repository.savePhoto(resume.id, processed.file.path) }
                .flatMap { repository.photo(resume.id) }
            tempFiles.deleteJobDir(jobId)
            when (result) {
                is Result.Success -> {
                    mutableState.update {
                        it.copy(photoBusy = false, photoCrop = null, photo = result.value?.let(::PhotoBytes))
                    }
                    edit { it.copy(hasPhoto = true) }
                }
                is Result.Failure -> {
                    mutableState.update { it.copy(photoBusy = false) }
                    effectChannel.send(ResumeEffect.ShowError(result.error))
                }
            }
        }
    }

    private fun photoRemove() {
        val resume = state.value.resume ?: return
        viewModelScope.launch {
            repository.deletePhoto(resume.id)
            mutableState.update { it.copy(photo = null) }
            edit { it.copy(hasPhoto = false) }
        }
    }

    // endregion

    // region export

    fun onExport(format: DocumentFormat) {
        val resume = state.value.resume ?: return
        val jobId = ids.next()
        discardExport()
        runner.start(cleanup = {
            if (runner.state.value !is JobState.Success) tempFiles.deleteJobDir(jobId)
        }) { progress ->
            tempFiles.createJobDir(jobId).flatMap { dir ->
                exportJobId = jobId
                exportResume(resume, format, dir, progress)
            }
        }
    }

    fun onCancelExport() = runner.cancel()

    fun onSave(destination: String) {
        val output = state.value.exported ?: return
        viewModelScope.launch {
            saveOutput(output, DestinationUri(destination)).fold({}, { effectChannel.send(ResumeEffect.ShowError(it)) })
        }
    }

    fun onShare() {
        val output = state.value.exported ?: return
        viewModelScope.launch {
            shareOutput(output).fold(
                onSuccess = { effectChannel.send(ResumeEffect.Share(it, output.mimeType)) },
                onFailure = { effectChannel.send(ResumeEffect.ShowError(it)) },
            )
        }
    }

    private fun resetExport() {
        runner.cancel()
        runner.reset()
        discardExport()
    }

    private fun discardExport() {
        val id = exportJobId ?: return
        exportJobId = null
        appScope.launch { tempFiles.deleteJobDir(id) }
    }

    // endregion

    override fun onCleared() {
        if (state.value.resume != null) scheduleSave(immediate = true)
        discardExport()
        super.onCleared()
    }

    /** Ids for sections and entries the editor adds. */
    val newId: () -> String = { ids.next() }

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val SAVE_DELAY_MS = 600L
        const val PHOTO_WIDTH_PX = 413
        const val PHOTO_HEIGHT_PX = 531
        const val PHOTO_ASPECT = 413f / 531f
        const val PHOTO_QUALITY = 90
    }
}
