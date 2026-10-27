package com.mrfilefixit.feature.resume.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrfilefixit.core.domain.biodata.Biodata
import com.mrfilefixit.core.domain.biodata.BiodataEdits
import com.mrfilefixit.core.domain.biodata.BiodataKind
import com.mrfilefixit.core.domain.biodata.BiodataLabels
import com.mrfilefixit.core.domain.biodata.BiodataPhoto
import com.mrfilefixit.core.domain.biodata.BiodataRepository
import com.mrfilefixit.core.domain.biodata.BiodataSummary
import com.mrfilefixit.core.domain.biodata.BiodataTemplate
import com.mrfilefixit.core.domain.biodata.BiodataTemplates
import com.mrfilefixit.core.domain.biodata.ExportBiodataUseCase
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
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
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

/** Where the biodata part of the tab is. [NONE] shows the hub. */
enum class BiodataPage { NONE, TEMPLATES, EDITOR }

/** Frame shapes for cropping a biodata photo. [ratio] is width / height; null keeps the photo's own shape. */
enum class PhotoAspect(val ratio: Float?) {
    PORTRAIT(3f / 4f),
    SQUARE(1f),
    LANDSCAPE(4f / 3f),
    ORIGINAL(null),
}

/** A picked photo being framed before it is added to the draft. */
data class BiodataPhotoCrop(
    val file: FileRef,
    val imageAspect: Float,
    val aspect: PhotoAspect,
    val rect: NormalizedRect,
)

/** What the user does with biodata photos. */
sealed interface BiodataPhotoEvent {
    data class Picked(val uri: String) : BiodataPhotoEvent

    data class Aspect(val aspect: PhotoAspect) : BiodataPhotoEvent

    data class CropChanged(val rect: NormalizedRect) : BiodataPhotoEvent

    data object Confirm : BiodataPhotoEvent

    data object Cancel : BiodataPhotoEvent

    data class Remove(val key: String) : BiodataPhotoEvent

    data class MakePrimary(val key: String) : BiodataPhotoEvent

    data class Move(val from: Int, val to: Int) : BiodataPhotoEvent
}

data class BiodataUiState(
    val page: BiodataPage = BiodataPage.NONE,
    val drafts: List<BiodataSummary> = emptyList(),
    val biodata: Biodata? = null,
    /** Stored JPEG bytes by photo key, for the editor's thumbnails. */
    val photos: Map<String, PhotoBytes> = emptyMap(),
    val photoCrop: BiodataPhotoCrop? = null,
    val photoBusy: Boolean = false,
    /** Design chooser opened from the editor: switches the open draft's design instead of starting one. */
    val restyling: Boolean = false,
    /** The editor shows the live PDF preview instead of the form. */
    val previewing: Boolean = false,
    val preview: JobState<OutputFile> = JobState.Idle,
    val export: JobState<OutputFile> = JobState.Idle,
    val error: AppError? = null,
) {
    val template: BiodataTemplate? get() = biodata?.let { BiodataTemplates.byId(it.templateId) }
    val exported: OutputFile? get() = (export as? JobState.Success)?.value
    val previewFile: OutputFile? get() = (preview as? JobState.Success)?.value
}

sealed interface BiodataEffect {
    data class Share(val uri: String, val mimeType: String) : BiodataEffect

    data class ShowError(val error: AppError) : BiodataEffect
}

/**
 * The biodata half of the Resume & Biodata tab: saved drafts, the five designs and the editor. Like resumes,
 * every edit is applied at once and saved shortly after. While the preview is shown, the PDF is made again a
 * moment after each change, so it always matches the form.
 */
@HiltViewModel
class BiodataViewModel @Inject constructor(
    private val repository: BiodataRepository,
    private val exportBiodata: ExportBiodataUseCase,
    private val describeFile: DescribeFileUseCase,
    private val imageProcessor: ImageProcessor,
    private val saveOutput: SaveOutputUseCase,
    private val shareOutput: ShareOutputUseCase,
    private val tempFiles: TempFileStore,
    private val ids: JobIdGenerator,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BiodataUiState())
    val state: StateFlow<BiodataUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<BiodataEffect>(Channel.BUFFERED)
    val effects: Flow<BiodataEffect> = effectChannel.receiveAsFlow()

    private val exportRunner = JobRunner<OutputFile>(viewModelScope)
    private val previewRunner = JobRunner<OutputFile>(viewModelScope)
    private var exportJobId: String? = null
    private var previewJobId: String? = null
    private var pendingSave: Job? = null
    private var pendingPreview: Job? = null

    /** The draft changed since the preview PDF was made. */
    private var previewStale = true

    /** Printed words; the screen passes its translated strings so the PDF is in the app's language. */
    var labels: BiodataLabels = BiodataLabels.English

    init {
        viewModelScope.launch { exportRunner.state.collect { job -> mutableState.update { it.copy(export = job) } } }
        viewModelScope.launch { previewRunner.state.collect { job -> mutableState.update { it.copy(preview = job) } } }
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

    fun onOpenTemplates(restyle: Boolean) =
        mutableState.update { it.copy(page = BiodataPage.TEMPLATES, restyling = restyle, error = null) }

    fun onTemplateChosen(template: BiodataTemplate) {
        val current = state.value
        if (current.restyling && current.biodata != null) {
            edit { BiodataEdits.applyTemplate(it, template) }
            mutableState.update { it.copy(page = BiodataPage.EDITOR, restyling = false) }
            return
        }
        openEditor(Biodata(id = newKey(), templateId = template.id, updatedAtMillis = now()), emptyMap())
        scheduleSave(immediate = true)
    }

    fun onOpenDraft(id: String) {
        viewModelScope.launch {
            repository.load(id).fold(
                onSuccess = { biodata ->
                    val photos = biodata.photos.mapNotNull { photo ->
                        repository.photo(id, photo.key).getOrNull()?.let { photo.key to PhotoBytes(it) }
                    }.toMap()
                    openEditor(biodata, photos)
                },
                onFailure = { error -> effectChannel.send(BiodataEffect.ShowError(error)) },
            )
        }
    }

    fun onDeleteDraft(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            refreshDrafts()
        }
    }

    /** Back inside the biodata pages. Returns false when there is nothing to go back from. */
    fun onBack(): Boolean {
        val s = state.value
        when {
            s.photoCrop != null -> mutableState.update { it.copy(photoCrop = null) }
            s.page == BiodataPage.TEMPLATES && s.restyling -> mutableState.update {
                it.copy(page = BiodataPage.EDITOR, restyling = false)
            }
            s.page == BiodataPage.TEMPLATES -> mutableState.update { it.copy(page = BiodataPage.NONE) }
            s.page == BiodataPage.EDITOR && s.previewing -> mutableState.update { it.copy(previewing = false) }
            s.page == BiodataPage.EDITOR -> closeEditor()
            else -> return false
        }
        return true
    }

    private fun openEditor(biodata: Biodata, photos: Map<String, PhotoBytes>) {
        resetExport()
        resetPreview()
        mutableState.update {
            it.copy(
                page = BiodataPage.EDITOR,
                biodata = biodata,
                photos = photos,
                photoCrop = null,
                restyling = false,
                previewing = false,
                error = null,
            )
        }
    }

    private fun closeEditor() {
        scheduleSave(immediate = true)
        resetExport()
        resetPreview()
        mutableState.update {
            it.copy(page = BiodataPage.NONE, biodata = null, photos = emptyMap(), photoCrop = null, previewing = false)
        }
        refreshDrafts()
    }

    fun onDismissError() = mutableState.update { it.copy(error = null) }

    // endregion

    // region editing

    fun edit(transform: (Biodata) -> Biodata) {
        val before = state.value.biodata ?: return
        val after = transform(before)
        if (after == before) return
        mutableState.update { it.copy(biodata = after) }
        if (state.value.export !is JobState.Running) resetExport()
        scheduleSave(immediate = false)
        if (state.value.previewing) schedulePreview(immediate = false)
    }

    private fun scheduleSave(immediate: Boolean) {
        val biodata = state.value.biodata ?: return
        pendingSave?.cancel()
        val write: suspend () -> Unit = {
            repository.save(biodata.copy(updatedAtMillis = now()))
                .fold({}, { effectChannel.send(BiodataEffect.ShowError(it)) })
        }
        // Leaving the editor saves in the app scope, so the write finishes even if the screen goes away.
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

    // region preview

    /** Switches between the form and the PDF preview. */
    fun onPreview(show: Boolean) {
        mutableState.update { it.copy(previewing = show) }
        if (show && (previewStale || state.value.preview !is JobState.Success)) schedulePreview(immediate = true)
    }

    private fun schedulePreview(immediate: Boolean) {
        previewStale = true
        pendingPreview?.cancel()
        pendingPreview = viewModelScope.launch {
            if (!immediate) delay(PREVIEW_DELAY_MS)
            renderPreview()
        }
    }

    private fun renderPreview() {
        val biodata = state.value.biodata ?: return
        if (biodata.photos.isEmpty()) {
            previewRunner.cancel()
            previewRunner.reset()
            return
        }
        val jobId = ids.next()
        val old = previewJobId
        previewStale = false
        // A newer draft replaces a preview that is still being made.
        previewRunner.cancel()
        previewRunner.start(cleanup = {
            if (previewRunner.state.value !is JobState.Success) tempFiles.deleteJobDir(jobId)
        }) { progress ->
            tempFiles.createJobDir(jobId).flatMap { dir ->
                previewJobId = jobId
                if (old != null) appScope.launch { tempFiles.deleteJobDir(old) }
                exportBiodata(biodata, labels, DocumentFormat.PDF, dir, LocalDate.now(), progress)
            }
        }
    }

    private fun resetPreview() {
        pendingPreview?.cancel()
        previewRunner.cancel()
        previewRunner.reset()
        previewStale = true
        val id = previewJobId ?: return
        previewJobId = null
        appScope.launch { tempFiles.deleteJobDir(id) }
    }

    // endregion

    // region photos

    fun onPhoto(event: BiodataPhotoEvent) {
        when (event) {
            is BiodataPhotoEvent.Picked -> photoPicked(event.uri)
            is BiodataPhotoEvent.Aspect -> mutableState.update { s ->
                val crop = s.photoCrop ?: return@update s
                s.copy(photoCrop = crop.copy(aspect = event.aspect, rect = frameFor(event.aspect, crop.imageAspect)))
            }
            is BiodataPhotoEvent.CropChanged -> mutableState.update { s ->
                s.copy(photoCrop = s.photoCrop?.copy(rect = event.rect))
            }
            BiodataPhotoEvent.Confirm -> photoConfirm()
            BiodataPhotoEvent.Cancel -> mutableState.update { it.copy(photoCrop = null) }
            is BiodataPhotoEvent.Remove -> {
                edit { BiodataEdits.removePhoto(it, event.key) }
                mutableState.update { it.copy(photos = it.photos - event.key) }
            }
            is BiodataPhotoEvent.MakePrimary -> edit { BiodataEdits.makePrimary(it, event.key) }
            is BiodataPhotoEvent.Move -> edit { BiodataEdits.movePhoto(it, event.from, event.to) }
        }
    }

    private fun photoPicked(uri: String) {
        val biodata = state.value.biodata ?: return
        if (!biodata.canAddPhoto) return
        viewModelScope.launch {
            mutableState.update { it.copy(photoBusy = true) }
            val picked = describeFile(uri).flatMap { file ->
                imageProcessor.readInfo(file).flatMap { info -> success(file to info) }
            }
            when (picked) {
                is Result.Success -> {
                    val (file, info) = picked.value
                    val imageAspect = info.width.toFloat() / info.height.coerceAtLeast(1)
                    val aspect = defaultAspect(biodata)
                    mutableState.update {
                        it.copy(
                            photoBusy = false,
                            photoCrop = BiodataPhotoCrop(file, imageAspect, aspect, frameFor(aspect, imageAspect)),
                        )
                    }
                }
                is Result.Failure -> {
                    mutableState.update { it.copy(photoBusy = false) }
                    effectChannel.send(BiodataEffect.ShowError(picked.error))
                }
            }
        }
    }

    /** Crops to the chosen frame, fits the long side to [PHOTO_MAX_SIDE] px and stores the JPEG with the draft. */
    private fun photoConfirm() {
        val s = state.value
        val crop = s.photoCrop ?: return
        val biodata = s.biodata ?: return
        val jobId = ids.next()
        val key = newKey()
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
                            ImageOp.Resize(PHOTO_MAX_SIDE, PHOTO_MAX_SIDE, ResizeMode.FIT),
                        ),
                        output = OutputSpec(ImageFormat.JPEG, EncodeTarget.Quality(PHOTO_QUALITY)),
                    ),
                )
            }.flatMap { processed ->
                repository.savePhoto(biodata.id, key, processed.file.path).flatMap {
                    repository.photo(biodata.id, key).flatMap { bytes -> success(processed.file to bytes) }
                }
            }
            tempFiles.deleteJobDir(jobId)
            when (result) {
                is Result.Success -> {
                    val (file, bytes) = result.value
                    val photo = BiodataPhoto(key, file.width ?: PHOTO_MAX_SIDE, file.height ?: PHOTO_MAX_SIDE)
                    mutableState.update {
                        it.copy(
                            photoBusy = false,
                            photoCrop = null,
                            photos = if (bytes != null) it.photos + (key to PhotoBytes(bytes)) else it.photos,
                        )
                    }
                    edit { BiodataEdits.addPhoto(it, photo) }
                }
                is Result.Failure -> {
                    mutableState.update { it.copy(photoBusy = false) }
                    effectChannel.send(BiodataEffect.ShowError(result.error))
                }
            }
        }
    }

    // endregion

    // region export

    fun onExport(format: DocumentFormat) {
        val biodata = state.value.biodata ?: return
        val jobId = ids.next()
        resetExport()
        exportRunner.start(cleanup = {
            if (exportRunner.state.value !is JobState.Success) tempFiles.deleteJobDir(jobId)
        }) { progress ->
            tempFiles.createJobDir(jobId).flatMap { dir ->
                exportJobId = jobId
                exportBiodata(biodata, labels, format, dir, LocalDate.now(), progress)
            }
        }
    }

    fun onCancelExport() = exportRunner.cancel()

    fun onSave(destination: String) {
        val output = state.value.exported ?: return
        viewModelScope.launch {
            saveOutput(
                output,
                DestinationUri(destination),
            ).fold({}, { effectChannel.send(BiodataEffect.ShowError(it)) })
        }
    }

    fun onShare() {
        val output = state.value.exported ?: return
        viewModelScope.launch {
            shareOutput(output).fold(
                onSuccess = { effectChannel.send(BiodataEffect.Share(it, output.mimeType)) },
                onFailure = { effectChannel.send(BiodataEffect.ShowError(it)) },
            )
        }
    }

    /** Stops any export and deletes the last exported file. */
    private fun resetExport() {
        exportRunner.cancel()
        exportRunner.reset()
        val id = exportJobId ?: return
        exportJobId = null
        appScope.launch { tempFiles.deleteJobDir(id) }
    }

    // endregion

    override fun onCleared() {
        if (state.value.biodata != null) scheduleSave(immediate = true)
        resetExport()
        resetPreview()
        super.onCleared()
    }

    /** Draft ids and photo keys: letters and digits only, as the store names files after them. */
    private fun newKey(): String = ids.next().filter(Char::isLetterOrDigit).take(KEY_LENGTH).ifEmpty { "b${now()}" }

    private fun now(): Long = System.currentTimeMillis()

    internal companion object {
        const val SAVE_DELAY_MS = 600L
        const val PREVIEW_DELAY_MS = 900L
        const val PHOTO_MAX_SIDE = 1600
        const val PHOTO_QUALITY = 90
        const val KEY_LENGTH = 32

        /** Marriage: a portrait first; portfolio: a square profile photo first; work samples keep their shape. */
        fun defaultAspect(biodata: Biodata): PhotoAspect = when {
            biodata.photos.isNotEmpty() -> PhotoAspect.ORIGINAL
            BiodataTemplates.byId(biodata.templateId).kind == BiodataKind.MARRIAGE -> PhotoAspect.PORTRAIT
            else -> PhotoAspect.SQUARE
        }

        fun frameFor(aspect: PhotoAspect, imageAspect: Float): NormalizedRect =
            aspect.ratio?.let { NormalizedRect.centeredWithAspect(it, imageAspect) } ?: NormalizedRect.FULL
    }
}
