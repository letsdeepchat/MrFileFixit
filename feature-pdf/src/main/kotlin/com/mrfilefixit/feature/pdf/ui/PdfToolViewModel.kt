package com.mrfilefixit.feature.pdf.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrfilefixit.core.domain.dispatchers.ApplicationScope
import com.mrfilefixit.core.domain.file.DescribeFileUseCase
import com.mrfilefixit.core.domain.file.DestinationUri
import com.mrfilefixit.core.domain.file.FileRef
import com.mrfilefixit.core.domain.file.JobIdGenerator
import com.mrfilefixit.core.domain.file.OutputFile
import com.mrfilefixit.core.domain.file.SaveOutputUseCase
import com.mrfilefixit.core.domain.file.ShareOutputUseCase
import com.mrfilefixit.core.domain.file.TempFileStore
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.domain.job.JobRunner
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.domain.pdf.CompressionLevel
import com.mrfilefixit.core.domain.pdf.GetPdfInfoUseCase
import com.mrfilefixit.core.domain.pdf.PageSelection
import com.mrfilefixit.core.domain.pdf.PageSize
import com.mrfilefixit.core.domain.pdf.PdfInfo
import com.mrfilefixit.core.domain.pdf.PdfOperation
import com.mrfilefixit.core.domain.pdf.Position
import com.mrfilefixit.core.domain.pdf.RunPdfOperationUseCase
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.collectAll
import com.mrfilefixit.core.domain.result.flatMap
import com.mrfilefixit.core.domain.result.fold
import com.mrfilefixit.core.domain.result.map
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.security.InputLimits
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

enum class PdfTool(val acceptsImages: Boolean = false, val multipleInputs: Boolean = false) {
    MERGE(multipleInputs = true),
    SPLIT,
    EXTRACT,
    REORDER,
    ROTATE,
    DELETE,
    COMPRESS,
    IMAGES_TO_PDF(acceptsImages = true, multipleInputs = true),
    TO_IMAGES,
    WATERMARK,
    PAGE_NUMBERS,
    PROTECT,
    UNLOCK,
}

enum class NumberStyle(val pattern: String) { PLAIN("{n}"), OF_TOTAL("{n} / {total}"), PAGE_OF("Page {n} of {total}") }

/** Options the user can set. Passwords are deliberately not here: they stay in the UI until "Run". */
data class PdfOptions(
    val pages: String = "",
    val degrees: Int = 90,
    val level: CompressionLevel = CompressionLevel.MEDIUM,
    val pageSize: PageSize = PageSize.A4,
    val dpi: Int = 150,
    val watermarkText: String = "",
    val opacity: Float = 0.3f,
    val diagonal: Boolean = true,
    val position: Position = Position.BOTTOM_CENTER,
    val numberStyle: NumberStyle = NumberStyle.OF_TOTAL,
)

data class PdfToolUiState(
    val tool: PdfTool,
    val files: List<FileRef> = emptyList(),
    val info: PdfInfo? = null,
    val options: PdfOptions = PdfOptions(),
    val inputError: AppError? = null,
    val job: JobState<List<OutputFile>> = JobState.Idle,
    val resultRevealed: Boolean = false,
) {
    val outputs: List<OutputFile> get() = (job as? JobState.Success)?.value.orEmpty().takeIf {
        resultRevealed
    }.orEmpty()
    val canRun: Boolean get() = when {
        files.isEmpty() -> false
        tool.multipleInputs -> files.size >= if (tool == PdfTool.MERGE) 2 else 1
        else -> info != null && (tool == PdfTool.UNLOCK || info.encrypted.not())
    }
}

sealed interface PdfEffect {
    data class Share(val uris: List<String>, val mimeType: String) : PdfEffect

    data class ShowError(val error: AppError) : PdfEffect
}

@HiltViewModel(assistedFactory = PdfToolViewModel.Factory::class)
class PdfToolViewModel @AssistedInject constructor(
    @Assisted private val tool: PdfTool,
    private val describeFile: DescribeFileUseCase,
    private val getInfo: GetPdfInfoUseCase,
    private val runOperation: RunPdfOperationUseCase,
    private val saveOutput: SaveOutputUseCase,
    private val shareOutput: ShareOutputUseCase,
    private val tempFiles: TempFileStore,
    private val jobIds: JobIdGenerator,
    private val limits: InputLimits,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(tool: PdfTool): PdfToolViewModel
    }

    private val mutableState = MutableStateFlow(PdfToolUiState(tool))
    val state: StateFlow<PdfToolUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<PdfEffect>(Channel.BUFFERED)
    val effects: Flow<PdfEffect> = effectChannel.receiveAsFlow()

    private val runner = JobRunner<List<OutputFile>>(viewModelScope)
    private var jobId: String? = null

    init {
        viewModelScope.launch {
            runner.state.collect { job ->
                mutableState.update {
                    it.copy(job = job, resultRevealed = it.resultRevealed && job is JobState.Success)
                }
            }
        }
    }

    fun onFilesPicked(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val combined = if (tool.multipleInputs) state.value.files.size + uris.size else 1
            val described: AppResult<List<FileRef>> = limits.checkFileCount(combined)
                .flatMap { uris.map { describeFile(it) }.collectAll() }
            when (described) {
                is Result.Failure -> mutableState.update { it.copy(inputError = described.error) }
                is Result.Success -> {
                    val files = if (tool.multipleInputs) {
                        state.value.files + described.value
                    } else {
                        described.value.take(
                            1,
                        )
                    }
                    mutableState.update { it.copy(files = files, inputError = null, info = null) }
                    if (!tool.multipleInputs) loadInfo(files.first())
                }
            }
        }
    }

    private suspend fun loadInfo(file: FileRef) {
        getInfo(file).fold(
            onSuccess = { info -> mutableState.update { it.copy(info = info) } },
            onFailure = { error -> mutableState.update { it.copy(inputError = error) } },
        )
    }

    fun onRemoveFile(index: Int) = mutableState.update { s ->
        s.copy(files = s.files.filterIndexed { i, _ -> i != index }, info = if (s.tool.multipleInputs) s.info else null)
    }

    fun onMoveFile(from: Int, to: Int) = mutableState.update { s ->
        if (to !in s.files.indices) return@update s
        s.copy(files = s.files.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun onOptionsChanged(transform: (PdfOptions) -> PdfOptions) = mutableState.update {
        it.copy(options = transform(it.options))
    }

    /** [password] and [ownerPassword] are only used for PROTECT/UNLOCK and are wiped after the job. */
    fun onRun(password: CharArray? = null) {
        val s = state.value
        if (!s.canRun) return
        val id = jobIds.next()
        discardOutputs()
        mutableState.update { it.copy(resultRevealed = false) }
        runner.start(cleanup = {
            if (runner.state.value !is JobState.Success) tempFiles.deleteJobDir(id)
        }) { progress ->
            tempFiles.createJobDir(id).flatMap { dir ->
                jobId = id
                buildOperation(s, dir, password).flatMap { runOperation(it, progress) }
            }.also { password?.fill('\u0000') }
        }
    }

    private fun buildOperation(s: PdfToolUiState, dir: String, password: CharArray?): AppResult<PdfOperation> {
        val o = s.options
        val first = s.files.first()
        val pageCount = s.info?.pageCount ?: 0
        val base = first.displayName.substringBeforeLast('.').ifBlank { "document" }
        fun pages() = PageSelection.parse(o.pages, pageCount)
        return when (s.tool) {
            PdfTool.MERGE -> success(PdfOperation.Merge(s.files, dir, "merged"))
            PdfTool.SPLIT -> PageSelection.parseRanges(o.pages, pageCount).map {
                PdfOperation.Split(first, it, dir, base)
            }
            PdfTool.EXTRACT, PdfTool.REORDER -> pages().map { PdfOperation.SelectPages(first, it, dir, "$base-pages") }
            PdfTool.DELETE -> pages().map { remove ->
                PdfOperation.SelectPages(first, (1..pageCount).filterNot { it in remove.toSet() }, dir, "$base-edited")
            }
            PdfTool.ROTATE -> pages().map { PdfOperation.Rotate(first, it.toSet(), o.degrees, dir, "$base-rotated") }
            PdfTool.COMPRESS -> success(PdfOperation.Compress(first, o.level, dir, "$base-compressed"))
            PdfTool.IMAGES_TO_PDF -> success(PdfOperation.ImagesToPdf(s.files, o.pageSize, MARGIN_PT, dir, "images"))
            PdfTool.TO_IMAGES -> pages().map { PdfOperation.ToImages(first, it, o.dpi, ImageFormat.JPEG, dir, base) }
            PdfTool.WATERMARK -> success(
                PdfOperation.Watermark(
                    first,
                    o.watermarkText,
                    o.opacity,
                    WATERMARK_SIZE_PT,
                    o.diagonal,
                    dir,
                    "$base-watermarked",
                ),
            )
            PdfTool.PAGE_NUMBERS -> success(
                PdfOperation.PageNumbers(first, o.position, 1, o.numberStyle.pattern, dir, "$base-numbered"),
            )
            PdfTool.PROTECT -> {
                val pw = password ?: CharArray(0)
                success(PdfOperation.Protect(first, pw.copyOf(), pw.copyOf(), dir, "$base-protected"))
            }
            PdfTool.UNLOCK -> success(
                PdfOperation.Unlock(first, (password ?: CharArray(0)).copyOf(), dir, "$base-unlocked"),
            )
        }
    }

    fun onCancel() = runner.cancel()

    fun onResultRevealed() = mutableState.update { it.copy(resultRevealed = true) }

    fun onSave(output: OutputFile, destination: String) {
        viewModelScope.launch {
            saveOutput(output, DestinationUri(destination)).fold(
                onSuccess = {},
                onFailure = { effectChannel.send(PdfEffect.ShowError(it)) },
            )
        }
    }

    fun onShareAll() {
        val outputs = state.value.outputs
        if (outputs.isEmpty()) return
        viewModelScope.launch {
            outputs.map { shareOutput(it) }.collectAll().fold(
                onSuccess = { effectChannel.send(PdfEffect.Share(it, outputs.first().mimeType)) },
                onFailure = { effectChannel.send(PdfEffect.ShowError(it)) },
            )
        }
    }

    fun onReset() {
        runner.cancel()
        runner.reset()
        discardOutputs()
        mutableState.value = PdfToolUiState(tool)
    }

    private fun discardOutputs() {
        val id = jobId ?: return
        jobId = null
        appScope.launch { tempFiles.deleteJobDir(id) }
    }

    override fun onCleared() {
        discardOutputs()
        super.onCleared()
    }

    private companion object {
        const val MARGIN_PT = 24f
        const val WATERMARK_SIZE_PT = 48f
    }
}
