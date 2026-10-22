package com.mrfilefixit.feature.image.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrfilefixit.core.domain.batch.BatchPolicy
import com.mrfilefixit.core.domain.batch.BatchQuotaRepository
import com.mrfilefixit.core.domain.dispatchers.ApplicationScope
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
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.collectAll
import com.mrfilefixit.core.domain.result.flatMap
import com.mrfilefixit.core.domain.result.fold
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ImageTool { RESIZE, CROP, ROTATE, COMPRESS, CONVERT, STRIP_EXIF }

data class ImageOptions(
    val width: String = "1080",
    val height: String = "1080",
    val keepAspect: Boolean = true,
    /** Crop frame aspect (w / h); null = image's own aspect. */
    val cropAspect: Float? = null,
    val crop: NormalizedRect = NormalizedRect.FULL,
    val degrees: Int = 90,
    val quality: Int = 80,
    val targetKb: String = "",
    val format: ImageFormat = ImageFormat.JPEG,
)

data class ImageToolUiState(
    val tool: ImageTool = ImageTool.RESIZE,
    val files: List<FileRef> = emptyList(),
    /** Header info of the first file, used to size the crop preview. */
    val firstInfo: ImageInfo? = null,
    val options: ImageOptions = ImageOptions(),
    val inputError: AppError? = null,
    val job: JobState<BatchOutcome> = JobState.Idle,
    val resultRevealed: Boolean = false,
    val runsInBackground: Boolean = false,
) {
    val isBatch: Boolean get() = files.size > 1
    val outcome: BatchOutcome? get() = (job as? JobState.Success)?.value?.takeIf { resultRevealed }
    val canRun: Boolean get() = files.isNotEmpty() && (tool != ImageTool.CROP || files.size == 1)
}

sealed interface ImageEffect {
    data class Share(val uris: List<String>, val mimeType: String) : ImageEffect

    data class ShowError(val error: AppError) : ImageEffect
}

/**
 * One screen for all image tools. Single images run inline; batches use one free daily batch job
 * (or one earned with an opt-in rewarded ad), and batches above [BatchPolicy.INLINE_LIMIT] go to WorkManager.
 */
@HiltViewModel
class ImageToolViewModel @Inject constructor(
    private val describeFile: DescribeFileUseCase,
    private val batchImages: BatchImageUseCase,
    private val imageProcessor: ImageProcessor,
    private val scheduler: ImageBatchScheduler,
    private val quota: BatchQuotaRepository,
    private val saveOutput: SaveOutputUseCase,
    private val shareOutput: ShareOutputUseCase,
    private val tempFiles: TempFileStore,
    private val jobIds: JobIdGenerator,
    private val limits: InputLimits,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ImageToolUiState())
    val state: StateFlow<ImageToolUiState> = mutableState.asStateFlow()

    val remainingBatches: StateFlow<Int> = quota.remaining
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BatchPolicy.FREE_BATCHES_PER_DAY)

    private val effectChannel = Channel<ImageEffect>(Channel.BUFFERED)
    val effects: Flow<ImageEffect> = effectChannel.receiveAsFlow()

    private val runner = JobRunner<BatchOutcome>(viewModelScope)
    private var jobId: String? = null
    private var workId: UUID? = null
    private var workObserver: Job? = null

    init {
        viewModelScope.launch {
            runner.state.collect { job -> if (!state.value.runsInBackground) applyJob(job) }
        }
    }

    private fun applyJob(job: JobState<BatchOutcome>) =
        mutableState.update { it.copy(job = job, resultRevealed = it.resultRevealed && job is JobState.Success) }

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
                        onSuccess = { info -> mutableState.update { it.copy(firstInfo = info) } },
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
            if (s.isBatch) {
                val allowed = quota.consume()
                if (allowed is Result.Failure) {
                    mutableState.update { it.copy(inputError = allowed.error) }
                    return@launch
                }
            }
            val spec = buildSpec(s)
            val id = jobIds.next()
            discardOutputs()
            mutableState.update { it.copy(resultRevealed = false, inputError = null) }
            if (s.files.size >
                BatchPolicy.INLINE_LIMIT
            ) {
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
        return when (s.tool) {
            ImageTool.RESIZE -> ImageBatchSpec(
                listOf(
                    ImageOp.Resize(
                        o.width.toIntOrNull()?.coerceIn(1, limits.maxDimension) ?: DEFAULT_SIDE,
                        o.height.toIntOrNull()?.coerceIn(1, limits.maxDimension) ?: DEFAULT_SIDE,
                        if (o.keepAspect) ResizeMode.FIT else ResizeMode.STRETCH,
                    ),
                ),
                OutputSpec(o.format, quality),
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
    }

    fun onResultRevealed() = mutableState.update { it.copy(resultRevealed = true) }

    /** Called only after the rewarded ad reports an earned reward, never on click. */
    fun onRewardEarned() {
        viewModelScope.launch {
            quota.grant(BatchPolicy.REWARD_BATCHES)
            mutableState.update { it.copy(inputError = null) }
        }
    }

    fun onSave(output: OutputFile, destination: String) {
        viewModelScope.launch {
            saveOutput(output, DestinationUri(destination)).fold({}, { effectChannel.send(ImageEffect.ShowError(it)) })
        }
    }

    fun onShareAll() {
        val outputs = state.value.outcome?.images?.map { it.file }.orEmpty()
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
        workObserver?.cancel()
        discardOutputs()
        mutableState.update { ImageToolUiState(tool = it.tool, options = it.options) }
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

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_QUALITY = 100
        const val DEFAULT_SIDE = 1080
        const val KIB = 1024L
    }
}
