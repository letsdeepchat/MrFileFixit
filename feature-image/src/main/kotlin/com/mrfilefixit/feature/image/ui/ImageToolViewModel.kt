package com.mrfilefixit.feature.image.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.mrfilefixit.core.domain.image.ImageEffects
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.image.ImageInfo
import com.mrfilefixit.core.domain.image.ImageOp
import com.mrfilefixit.core.domain.image.ImageProcessor
import com.mrfilefixit.core.domain.image.ImageRequest
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
import com.mrfilefixit.core.domain.scan.asSource
import com.mrfilefixit.core.domain.security.InputLimits
import com.mrfilefixit.feature.image.work.ImageBatchScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
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

enum class ImageTool { RESIZE, CROP, ROTATE, COMPRESS, CONVERT, EDIT, ENHANCE, BACKGROUND, STRIP_EXIF }

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
    val background: BackgroundOptions = BackgroundOptions(),
)

/** Edits on the Resize form. Width and height stay in the photo's ratio while [ImageOptions.keepAspect] is on. */
sealed interface ResizeEdit {
    data class Width(val text: String) : ResizeEdit

    data class Height(val text: String) : ResizeEdit

    data class KeepAspect(val keep: Boolean) : ResizeEdit

    data class Preset(val preset: ResizePreset) : ResizeEdit
}

data class ImageToolUiState(
    /** A result was saved or shared since the last run, so leaving does not lose anything. */
    val delivered: Boolean = false,
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
    /** Enhance and Edit tabs: the first image at preview size, before and after, while options are chosen. */
    val livePreview: LivePreview? = null,
    /** The tool whose run produced [job] / [pdfJob]; null when unknown (a background batch picked up again). */
    val ranTool: ImageTool? = null,
) {
    val isBatch: Boolean get() = files.size > 1
    val outcome: BatchOutcome? get() = (job as? JobState.Success)?.value

    /** The job the screen shows: the PDF job once one has started, otherwise the image batch. */
    val activeJob: JobState<*> get() = if (pdfJob !is JobState.Idle) pdfJob else job

    /**
     * The job as the current tab shows it. A finished run (its results, error or "cancelled") belongs to the tool
     * that ran it, so another tab shows its own options and Run button instead of the last tool's results. The
     * results are kept and come back on their own tab. A run in progress shows everywhere.
     */
    val shownJob: JobState<*>
        get() = activeJob.takeIf { it is JobState.Running || ranTool == null || ranTool == tool } ?: JobState.Idle

    /** Finished files, from either job. */
    val outputs: List<OutputFile>
        get() = outcome?.images?.map { it.file } ?: listOfNotNull((pdfJob as? JobState.Success)?.value)
    val isDone: Boolean get() = shownJob is JobState.Success
    val canRun: Boolean
        get() = files.isNotEmpty() && (tool != ImageTool.CROP || files.size == 1) &&
            (tool != ImageTool.BACKGROUND || options.background.isReady)
    val activePreset: ResizePreset? get() = presets.firstOrNull { it.id == options.presetId }
}

/**
 * [beforeUri] is the first image scaled to preview size; [afterUri] the same with the tab's effects applied, or
 * [error] when they could not be applied (for example no person found for Remove background).
 */
data class LivePreview(
    val beforeUri: String,
    val afterUri: String?,
    val width: Int,
    val height: Int,
    val error: AppError? = null,
)

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
    private var previewJob: Job? = null
    private var previewDirId: String? = null

    /** The preview-size copy of the first image, by source URI, made once per picked file. */
    private var previewBase: Pair<String, OutputFile>? = null
    private var previewCount = 0

    init {
        viewModelScope.launch {
            runner.state.collect { job -> if (!state.value.runsInBackground) applyJob(job) }
        }
        viewModelScope.launch { pdfRunner.state.collect { job -> mutableState.update { it.copy(pdfJob = job) } } }
        // Read by name rather than with toRoute(): in the Images tab the handle belongs to the main tabs entry.
        val route = ImageRoute(savedStateHandle.get<String>("examId"), savedStateHandle.get<String>("document"))
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

    fun onToolSelected(tool: ImageTool) {
        // The old "after" belongs to the previous tab's effects; drop it so it never shows under the new tab.
        mutableState.update { it.copy(tool = tool, livePreview = it.livePreview?.copy(afterUri = null)) }
        schedulePreview(immediately = true)
    }

    fun onFilesPicked(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val described: AppResult<List<FileRef>> = limits.checkFileCount(uris.size)
                .flatMap { uris.map { describeFile(it) }.collectAll() }
            when (described) {
                is Result.Success -> {
                    // New pictures start clean: the last run's results, and a crop drawn on the old picture, belong
                    // to the old picture. A background batch keeps running and is not cancelled here.
                    if (!state.value.runsInBackground) {
                        runner.cancel()
                        pdfRunner.cancel()
                        discardOutputs()
                        runner.reset()
                        pdfRunner.reset()
                    }
                    mutableState.update {
                        it.copy(
                            files = described.value,
                            firstInfo = null,
                            inputError = null,
                            livePreview = null,
                            ranTool = null,
                            delivered = false,
                            options = it.options.copy(crop = NormalizedRect.FULL),
                        )
                    }
                    imageProcessor.readInfo(described.value.first()).fold(
                        onSuccess = { info ->
                            mutableState.update {
                                it.copy(firstInfo = info, options = it.options.withSyncedHeight(info))
                            }
                            schedulePreview(immediately = true)
                        },
                        onFailure = { error -> mutableState.update { it.copy(inputError = error) } },
                    )
                }
                is Result.Failure -> mutableState.update { it.copy(inputError = described.error) }
            }
        }
    }

    fun onOptionsChanged(transform: (ImageOptions) -> ImageOptions) {
        val before = state.value.previewEffects()
        mutableState.update { it.copy(options = transform(it.options)) }
        if (state.value.previewEffects() != before) schedulePreview()
    }

    /** What the live preview shows for the current tab: Enhance at original size, or the Edit look and text. */
    private fun ImageToolUiState.previewEffects(): ImageEffects? = when (tool) {
        ImageTool.ENHANCE -> options.enhance.copy(scale = 1).toEffects()
        ImageTool.EDIT -> options.edit.toEffects()
        ImageTool.BACKGROUND -> options.background.toEffects().takeIf { options.background.isReady }
        else -> null
    }

    /**
     * Enhance and Edit tabs: renders the first image at [PREVIEW_SIDE] with the tab's effects, a moment after a
     * slider stops or typing pauses, so the before / after split shows the real effect before the full-size run.
     */
    private fun schedulePreview(immediately: Boolean = false) {
        previewJob?.cancel()
        val s = state.value
        val source = s.files.firstOrNull()
        val info = s.firstInfo
        if (source == null || info == null || s.isDone) return
        val effects = s.previewEffects() ?: return
        previewJob = viewModelScope.launch {
            if (!immediately) delay(PREVIEW_DEBOUNCE_MS)
            val dir = previewDir() ?: return@launch
            val base = previewBaseFor(source, info, dir) ?: return@launch
            mutableState.update {
                val keep = it.livePreview?.takeIf { p -> p.beforeUri == base.asSource().uri }?.copy(error = null)
                it.copy(
                    livePreview = keep ?: LivePreview(
                        base.asSource().uri,
                        null,
                        base.width ?: info.width,
                        base.height ?: info.height,
                    ),
                )
            }
            val request = ImageRequest(
                source = base.asSource(),
                outputDir = dir,
                outputBaseName = "after-${++previewCount}",
                operations = emptyList(),
                // A see-through background needs a format with alpha even in the preview.
                output = OutputSpec(
                    if (effects.background?.needsAlpha == true) ImageFormat.PNG else ImageFormat.JPEG,
                    EncodeTarget.Quality(PREVIEW_QUALITY),
                ),
                effects = effects,
            )
            imageProcessor.process(request).fold(
                onSuccess = { after ->
                    mutableState.update { st ->
                        st.copy(livePreview = st.livePreview?.copy(afterUri = after.file.asSource().uri))
                    }
                },
                onFailure = { error ->
                    mutableState.update { st ->
                        st.copy(livePreview = st.livePreview?.copy(afterUri = null, error = error))
                    }
                },
            )
        }
    }

    private suspend fun previewDir(): String? {
        val id = previewDirId ?: jobIds.next().also { previewDirId = it }
        return (tempFiles.createJobDir(id) as? Result.Success)?.value
    }

    private suspend fun previewBaseFor(source: FileRef, info: ImageInfo, dir: String): OutputFile? {
        previewBase?.takeIf { it.first == source.uri }?.let { return it.second }
        val ops = if (maxOf(info.width, info.height) > PREVIEW_SIDE) {
            listOf(ImageOp.Resize(PREVIEW_SIDE, PREVIEW_SIDE, ResizeMode.FIT))
        } else {
            emptyList()
        }
        val request = ImageRequest(
            source = source,
            outputDir = dir,
            outputBaseName = "before-${++previewCount}",
            operations = ops,
            output = OutputSpec(ImageFormat.JPEG, EncodeTarget.Quality(PREVIEW_QUALITY)),
        )
        val base = (imageProcessor.process(request) as? Result.Success)?.value?.file ?: return null
        previewBase = source.uri to base
        return base
    }

    private fun discardPreview() {
        previewJob?.cancel()
        previewBase = null
        val id = previewDirId ?: return
        previewDirId = null
        appScope.launch { tempFiles.deleteJobDir(id) }
    }

    fun onRun() {
        val s = state.value
        if (!s.canRun) return
        mutableState.update { it.copy(delivered = false, ranTool = s.tool) }
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
            ImageTool.BACKGROUND -> {
                val background = o.background
                // See-through needs alpha: PNG unless WebP was chosen.
                val format = if (background.choice == BackgroundChoice.TRANSPARENT && o.format != ImageFormat.WEBP) {
                    ImageFormat.PNG
                } else {
                    o.format
                }
                ImageBatchSpec(
                    operations = emptyList(),
                    output = OutputSpec(format, quality),
                    suffix = "-background",
                    effects = background.toEffects(),
                )
            }
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
            saveOutput(output, DestinationUri(destination)).fold(
                onSuccess = { mutableState.update { it.copy(delivered = true) } },
                onFailure = { effectChannel.send(ImageEffect.ShowError(it)) },
            )
        }
    }

    fun onShareAll() {
        val outputs = state.value.outputs
        if (outputs.isEmpty()) return
        viewModelScope.launch {
            outputs.map { shareOutput(it) }.collectAll().fold(
                onSuccess = { uris ->
                    val mime = outputs.map { it.mimeType }.distinct().singleOrNull() ?: "image/*"
                    mutableState.update { it.copy(delivered = true) }
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
        discardPreview()
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
        discardPreview()
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

        /** Long side of the live Enhance preview: sharp on a phone screen, fast to redo while sliding. */
        const val PREVIEW_SIDE = 1024
        const val PREVIEW_QUALITY = 92
        const val PREVIEW_DEBOUNCE_MS = 250L
    }
}
