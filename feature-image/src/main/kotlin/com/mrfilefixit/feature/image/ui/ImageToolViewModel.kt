package com.mrfilefixit.feature.image.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mrfilefixit.core.domain.batch.BatchPolicy
import com.mrfilefixit.core.domain.dispatchers.ApplicationScope
import com.mrfilefixit.core.domain.exam.GetExamProfilesUseCase
import com.mrfilefixit.core.domain.file.DescribeFileUseCase
import com.mrfilefixit.core.domain.file.DestinationUri
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.JobIdGenerator
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.file.SaveOutputUseCase
import com.mrfilefixit.core.domain.file.ShareOutputUseCase
import com.mrfilefixit.core.domain.file.TempFileStore
import com.mrfilefixit.core.domain.image.BatchImageUseCase
import com.mrfilefixit.core.domain.image.BatchOutcome
import com.mrfilefixit.core.domain.image.EncodeTarget
import com.mrfilefixit.core.domain.image.ImageBatchSpec
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.ImageInfo
import com.mrfilefixit.core.domain.image.ImageOp
import com.mrfilefixit.core.domain.image.ImageProcessor
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.domain.image.OutputSpec
import com.mrfilefixit.core.domain.image.ResizeMode
import com.mrfilefixit.core.domain.job.JobRunner
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.domain.pdf.PageSize
import com.mrfilefixit.core.domain.pdf.PdfOperation
import com.mrfilefixit.core.domain.pdf.RunPdfOperationUseCase
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.collectAll
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.flatMap
import com.mrfilefixit.core.domain.result.fold
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.security.InputLimits
import com.mrfilefixit.feature.image.work.ImageBatchScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ImageTool { RESIZE, CROP, ROTATE, COMPRESS, CONVERT, EDIT, ENHANCE, STRIP_EXIF }

data class ImageOptions(
    val width: String = "1080",
    val height: String = "1080",
    val keepAspect: Boolean = true,
    /** Set by a preset; cleared as soon as the user edits the size by hand. */
    val presetId: String? = null,
    /** Crop frame aspect (w / h); null = image's own aspect. */
    val cropAspect: Float? = null,
    val crop: NormalizedRect = NormalizedRect.FULL,
    val degrees: Int = 90,
    val quality: Int = 80,
    val targetKb: String = "",
    val format: ImageFormat = ImageFormat.JPEG,
    /** Convert: all picked images into one PDF instead of image files. */
    val toPdf: Boolean = false,
    val edit: EditOptions = EditOptions(),
    val enhance: EnhanceOptions = EnhanceOptions(),
)

/** Edits on the Resize form. Width and height stay in the photo's ratio while [ImageOptions.keepAspect] is on. */
sealed interface ResizeEdit {
    data class Width(val text: String) : ResizeEdit

    data class Height(val text: String) : ResizeEdit

    data class KeepAspect(val keep: Boolean) : ResizeEdit

    data class Preset(val preset: ResizePreset) : ResizeEdit
}

data class ImageToolUiState(
    val tool: ImageTool = ImageTool.RESIZE,
    val files: List<FileRef> = emptyList(),
    val presets: List<ResizePreset> = listOf(ResizePreset.PASSPORT),
    /** Header info of the first file, used to size the crop preview. */
    val firstInfo: ImageInfo? = null,
    val options: ImageOptions = ImageOptions(),
    val inputError: AppError? = null,
    val job: JobState<BatchOutcome> = JobState.Idle,
    /** Convert to PDF: one document from all the picked images. */
    val pdfJob: JobState<OutputFile> = JobState.Idle,
    val runsInBackground: Boolean = false,
) {
    val isBatch: Boolean get() = files.size > 1
    val outcome: BatchOutcome? get() = (job as? JobState.Success)?.value

    /** The job the screen shows: the PDF job once one has started, otherwise the image batch. */
    val activeJob: JobState<*> get() = if (pdfJob !is JobState.Idle) pdfJob else job

    /** Finished files, from either job. */
    val outputs: List<OutputFile>
        get() = outcome?.images?.map { it.file } ?: listOfNotNull((pdfJob as? JobState.Success)?.value)
    val isDone: Boolean get() = activeJob is JobState.Success
    val canRun: Boolean get() = files.isNotEmpty() && (tool != ImageTool.CROP || files.size == 1)
    val activePreset: ResizePreset? get() = presets.firstOrNull { it.id == options.presetId }
}

sealed interface ImageEffect {
    data class Share(val uris: List<String>, val mimeType: String) : ImageEffect

    data class ShowError(val error: AppError) : ImageEffect
}

/**
 * One screen for all image tools. Everything is free and unlimited: small batches run inline, and batches
 * above [BatchPolicy.INLINE_LIMIT] go to WorkManager so they survive the app being closed.
 */
@HiltViewModel
class ImageToolViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getExamProfiles: GetExamProfilesUseCase,
    private val describeFile: DescribeFileUseCase,
    private val batchImages: BatchImageUseCase,
    private val imageProcessor: ImageProcessor,
    private val scheduler: ImageBatchScheduler,
    private val saveOutput: SaveOutputUseCase,
    private val shareOutput: ShareOutputUseCase,
    private val tempFiles: TempFileStore,
    private val jobIds: JobIdGenerator,
    private val limits: InputLimits,
    private val runPdfOperation: RunPdfOperationUseCase,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ImageToolUiState())
    val state: StateFlow<ImageToolUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<ImageEffect>(Channel.BUFFERED)
    val effects: Flow<ImageEffect> = effectChannel.receiveAsFlow()

    private val runner = JobRunner<BatchOutcome>(viewModelScope)
    private val pdfRunner = JobRunner<OutputFile>(viewModelScope)
    private var jobId: String? = null
    private var workId: UUID? = null
    private var workObserver: Job? = null

    init {
        viewModelScope.launch {
            runner.state.collect { job -> if (!state.value.runsInBackground) applyJob(job) }
        }
        viewModelScope.launch { pdfRunner.state.collect { job -> mutableState.update { it.copy(pdfJob = job) } } }
        val route = savedStateHandle.toRoute<ImageRoute>()
        viewModelScope.launch { loadPresets(route) }
    }

    /** Exam presets come from the bundled rules. Opened from an exam, that exam's upload is pre-selected. */
    private suspend fun loadPresets(route: ImageRoute) {
        val profiles = (getExamProfiles() as? Result.Success)?.value.orEmpty()
        val presets = ResizePresets.build(profiles, route.examId)
        mutableState.update { it.copy(presets = presets) }
        val requested = presets.firstOrNull { it.spec != null && it.id == "${route.examId}:${route.document}" }
        if (requested != null) {
            mutableState.update { it.copy(tool = ImageTool.RESIZE) }
            onResizeEdit(ResizeEdit.Preset(requested))
        }
    }

    fun onResizeEdit(edit: ResizeEdit) = mutableState.update { s ->
        val o = s.options
        val info = s.firstInfo
        val synced = when (edit) {
            is ResizeEdit.Width -> {
                val text = edit.text.digitsOnly()
                val value = text.toIntOrNull()
                val height = if (o.keepAspect && info != null && value != null) {
                    AspectRatioSync.heightFor(value, info.width, info.height).toString()
                } else {
                    o.height
                }
                o.copy(width = text, height = height, presetId = null)
            }
            is ResizeEdit.Height -> {
                val text = edit.text.digitsOnly()
                val value = text.toIntOrNull()
                val width = if (o.keepAspect && info != null && value != null) {
                    AspectRatioSync.widthFor(value, info.width, info.height).toString()
                } else {
                    o.width
                }
                o.copy(height = text, width = width, presetId = null)
            }
            is ResizeEdit.KeepAspect -> o.copy(keepAspect = edit.keep, presetId = null).withSyncedHeight(info)
            // An exam preset is an exact box: crop to fill rather than keep the photo's ratio.
            is ResizeEdit.Preset -> o.copy(
                width = edit.preset.width.toString(),
                height = edit.preset.height.toString(),
                keepAspect = false,
                presetId = edit.preset.id,
                format = if (edit.preset.spec != null) ImageFormat.JPEG else o.format,
            )
        }
        s.copy(options = synced)
    }

    private fun applyJob(job: JobState<BatchOutcome>) =
        mutableState.update { it.copy(job = job) }

    fun canEncode(format: ImageFormat): Boolean = imageProcessor.canEncode(format)

    fun onToolSelected(tool: ImageTool) = mutableState.update { it.copy(tool = tool) }

    fun onFilesPicked(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val described: AppResult<List<FileRef>> = limits.checkFileCount(uris.size)
                .flatMap { uris.map { describeFile(it) }.collectAll() }
            when (described) {
                is Result.Success -> {
                    mutableState.update { it.copy(files = described.value, firstInfo = null, inputError = null) }
                    imageProcessor.readInfo(described.value.first()).fold(
                        onSuccess = { info ->
                            mutableState.update {
                                it.copy(firstInfo = info, options = it.options.withSyncedHeight(info))
                            }
                        },
                        onFailure = { error -> mutableState.update { it.copy(inputError = error) } },
                    )
                }
                is Result.Failure -> mutableState.update { it.copy(inputError = described.error) }
            }
        }
    }

    fun onOptionsChanged(transform: (ImageOptions) -> ImageOptions) =
        mutableState.update { it.copy(options = transform(it.options)) }

    fun onRun() {
        val s = state.value
        if (!s.canRun) return
        viewModelScope.launch {
            val id = jobIds.next()
            discardOutputs()
            runner.reset()
            pdfRunner.reset()
            mutableState.update { it.copy(inputError = null) }
            if (s.tool == ImageTool.CONVERT && s.options.toPdf) {
                runPdf(id, s.files)
                return@launch
            }
            val spec = buildSpec(s)
            if (s.files.size > BatchPolicy.INLINE_LIMIT) {
                runInBackground(id, s.files, spec)
            } else {
                runInline(id, s.files, spec)
            }
        }
    }

    private fun runInline(id: String, files: List<FileRef>, spec: ImageBatchSpec) {
        mutableState.update { it.copy(runsInBackground = false) }
        runner.start(cleanup = {
            if (runner.state.value !is JobState.Success) tempFiles.deleteJobDir(id)
        }) { progress ->
            tempFiles.createJobDir(id).flatMap { dir ->
                jobId = id
                batchImages(files, spec, dir, progress)
            }
        }
    }

    /** One PDF, a page per image at the image's own size, so nothing is cropped or scaled. */
    private fun runPdf(id: String, files: List<FileRef>) {
        mutableState.update { it.copy(runsInBackground = false) }
        val baseName = files.first().displayName.substringBeforeLast('.').ifBlank { PDF_BASE_NAME }
        pdfRunner.start(cleanup = {
            if (pdfRunner.state.value !is JobState.Success) tempFiles.deleteJobDir(id)
        }) { progress ->
            tempFiles.createJobDir(id).flatMap { dir ->
                jobId = id
                runPdfOperation(PdfOperation.ImagesToPdf(files, PageSize.FIT_IMAGE, 0f, dir, baseName), progress)
                    .flatMap { outputs ->
                        outputs.firstOrNull()?.let { success(it) } ?: failure(AppError.Unexpected("NoPdfOutput"))
                    }
            }
        }
    }

    private suspend fun runInBackground(id: String, files: List<FileRef>, spec: ImageBatchSpec) {
        when (val dir = tempFiles.createJobDir(id)) {
            is Result.Failure -> applyJob(JobState.Failed(dir.error))
            is Result.Success -> {
                jobId = id
                mutableState.update { it.copy(runsInBackground = true) }
                val work = scheduler.enqueue(dir.value, files, spec)
                workId = work
                workObserver?.cancel()
                workObserver = viewModelScope.launch {
                    scheduler.observe(work, dir.value)
                        .collect { applyJob(it) }
                }
            }
        }
    }

    private fun buildSpec(s: ImageToolUiState): ImageBatchSpec {
        val o = s.options
        val quality = EncodeTarget.Quality(o.quality.coerceIn(1, MAX_QUALITY))
        val examSpec = s.activePreset?.spec
        return when (s.tool) {
            ImageTool.RESIZE -> ImageBatchSpec(
                listOf(
                    ImageOp.Resize(
                        o.width.toIntOrNull()?.coerceIn(1, limits.maxDimension) ?: DEFAULT_SIDE,
                        o.height.toIntOrNull()?.coerceIn(1, limits.maxDimension) ?: DEFAULT_SIDE,
                        when {
                            examSpec != null -> ResizeMode.FILL
                            o.keepAspect -> ResizeMode.FIT
                            else -> ResizeMode.STRETCH
                        },
                    ),
                ),
                if (examSpec != null) {
                    // Same KB targeting as the exam flow: aim inside the window under either KB reading.
                    val range = examSpec.targetBytes
                    OutputSpec(ImageFormat.JPEG, EncodeTarget.SizeRange(range.first, range.last, allowPadding = true))
                } else {
                    OutputSpec(o.format, quality)
                },
                "-resized",
            )
            ImageTool.CROP -> ImageBatchSpec(listOf(ImageOp.Crop(o.crop)), OutputSpec(o.format, quality), "-cropped")
            ImageTool.ROTATE -> ImageBatchSpec(
                listOf(ImageOp.Rotate(o.degrees)),
                OutputSpec(o.format, quality),
                "-rotated",
            )
            ImageTool.COMPRESS -> {
                val kb = o.targetKb.toLongOrNull()?.takeIf { it > 0 }
                val target = if (kb != null) {
                    EncodeTarget.SizeRange(0, kb * KIB, allowDownscale = true)
                } else {
                    quality
                }
                val format = if (o.format == ImageFormat.PNG) ImageFormat.JPEG else o.format
                ImageBatchSpec(emptyList(), OutputSpec(format, target), "-compressed")
            }
            ImageTool.CONVERT -> ImageBatchSpec(emptyList(), OutputSpec(o.format, quality), "")
            ImageTool.EDIT -> ImageBatchSpec(
                operations = emptyList(),
                output = if (o.edit.usesStampSize) {
                    OutputSpec(ImageFormat.JPEG, EditOptions.STAMP_TARGET)
                } else {
                    OutputSpec(o.format, quality)
                },
                suffix = "-edited",
                effects = o.edit.toEffects(),
            )
            ImageTool.ENHANCE -> ImageBatchSpec(
                operations = emptyList(),
                output = OutputSpec(o.format, quality),
                suffix = "-enhanced",
                effects = o.enhance.toEffects(),
            )
            ImageTool.STRIP_EXIF -> ImageBatchSpec(
                emptyList(),
                OutputSpec(ImageFormat.JPEG, EncodeTarget.Original),
                "-clean",
            )
        }
    }

    fun onCancel() {
        val work = workId
        if (state.value.runsInBackground && work != null) scheduler.cancel(work) else runner.cancel()
        pdfRunner.cancel()
    }

    fun onSave(output: OutputFile, destination: String) {
        viewModelScope.launch {
            saveOutput(output, DestinationUri(destination)).fold({}, { effectChannel.send(ImageEffect.ShowError(it)) })
        }
    }

    fun onShareAll() {
        val outputs = state.value.outputs
        if (outputs.isEmpty()) return
        viewModelScope.launch {
            outputs.map { shareOutput(it) }.collectAll().fold(
                onSuccess = { uris ->
                    val mime = outputs.map { it.mimeType }.distinct().singleOrNull() ?: "image/*"
                    effectChannel.send(ImageEffect.Share(uris, mime))
                },
                onFailure = { effectChannel.send(ImageEffect.ShowError(it)) },
            )
        }
    }

    fun onReset() {
        onCancel()
        runner.reset()
        pdfRunner.reset()
        workObserver?.cancel()
        discardOutputs()
        mutableState.update { ImageToolUiState(tool = it.tool, presets = it.presets, options = it.options) }
    }

    private fun discardOutputs() {
        val id = jobId ?: return
        jobId = null
        appScope.launch { tempFiles.deleteJobDir(id) }
    }

    override fun onCleared() {
        // Background batches keep their files until the user comes back; stale-file cleanup removes them later.
        if (!state.value.runsInBackground) discardOutputs()
        super.onCleared()
    }

    private fun String.digitsOnly(): String = filter(Char::isDigit).take(MAX_DIGITS)

    private fun ImageOptions.withSyncedHeight(info: ImageInfo?): ImageOptions {
        val width = width.toIntOrNull()
        return if (keepAspect && info != null && width != null && presetId == null) {
            copy(height = AspectRatioSync.heightFor(width, info.width, info.height).toString())
        } else {
            this
        }
    }

    private companion object {
        const val MAX_DIGITS = 5
        const val MAX_QUALITY = 100
        const val DEFAULT_SIDE = 1080
        const val KIB = 1024L
        const val PDF_BASE_NAME = "Images"
    }
}
