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
import com.mrfilefixit.core.domain.pdf.PageMargin
import com.mrfilefixit.core.domain.pdf.PageOrientation
import com.mrfilefixit.core.domain.pdf.PageSelection
import com.mrfilefixit.core.domain.pdf.PageSize
import com.mrfilefixit.core.domain.pdf.PdfInfo
import com.mrfilefixit.core.domain.pdf.PdfOperation
import com.mrfilefixit.core.domain.pdf.Position
import com.mrfilefixit.core.domain.pdf.RunPdfOperationUseCase
import com.mrfilefixit.core.domain.pdf.SplitMode
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

/** Sections of the tool picker. */
enum class PdfToolGroup { ORGANIZE, CONVERT, OPTIMIZE, SECURITY }

enum class PdfTool(
    val group: PdfToolGroup,
    val acceptsImages: Boolean = false,
    val multipleInputs: Boolean = false,
) {
    MERGE(PdfToolGroup.ORGANIZE, multipleInputs = true),
    SPLIT(PdfToolGroup.ORGANIZE),
    EXTRACT(PdfToolGroup.ORGANIZE),
    REORDER(PdfToolGroup.ORGANIZE),
    ROTATE(PdfToolGroup.ORGANIZE),
    DELETE(PdfToolGroup.ORGANIZE),
    IMAGES_TO_PDF(PdfToolGroup.CONVERT, acceptsImages = true, multipleInputs = true),
    TO_IMAGES(PdfToolGroup.CONVERT),
    COMPRESS(PdfToolGroup.OPTIMIZE),
    WATERMARK(PdfToolGroup.OPTIMIZE),
    PAGE_NUMBERS(PdfToolGroup.OPTIMIZE),
    PROTECT(PdfToolGroup.SECURITY),
    UNLOCK(PdfToolGroup.SECURITY),
}

/** A KB window the output should land in, e.g. an exam portal's certificate limit. Shown, not enforced. */
data class KbTarget(val minKb: Int, val maxKb: Int) {
    fun contains(bytes: Long): Boolean = bytes in minKb.toLong() * KIB..maxKb.toLong() * KIB

    private companion object {
        const val KIB = 1024L
    }
}

enum class NumberStyle(val pattern: String) { PLAIN("{n}"), OF_TOTAL("{n} / {total}"), PAGE_OF("Page {n} of {total}") }

/** Options the user can set. Passwords are deliberately not here: they stay in the UI until "Run". */
data class PdfOptions(
    val pages: String = "",
    val degrees: Int = 90,
    val level: CompressionLevel = CompressionLevel.MEDIUM,
    val grayscale: Boolean = false,
    val pageSize: PageSize = PageSize.A4,
    val orientation: PageOrientation = PageOrientation.AUTO,
    val margin: PageMargin = PageMargin.SMALL,
    val splitMode: SplitMode = SplitMode.CUSTOM,
    /** "Every N pages" for [SplitMode.EVERY_N], as typed. */
    val everyN: String = "2",
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
    /** Page count per picked PDF (by URI), for the merge list. Missing while loading. */
    val pageCounts: Map<String, Int> = emptyMap(),
    val sizeTarget: KbTarget? = null,
    val options: PdfOptions = PdfOptions(),
    val inputError: AppError? = null,
    val job: JobState<List<OutputFile>> = JobState.Idle,
) {
    val outputs: List<OutputFile> get() = (job as? JobState.Success)?.value.orEmpty()
    val canRun: Boolean get() = when {
        files.isEmpty() -> false
        tool.multipleInputs -> files.size >= if (tool == PdfTool.MERGE) 2 else 1
        info == null -> false
        tool == PdfTool.SPLIT -> !info.encrypted && splitPlan is Result.Success
        else -> tool == PdfTool.UNLOCK || info.encrypted.not()
    }

    /** Files a split will produce, for the live preview. Null until the PDF is read. */
    val splitPlan: AppResult<List<IntRange>>? get() = info?.let {
        PageSelection.splitPlan(options.splitMode, options.pages, options.everyN.toIntOrNull(), it.pageCount)
    }
}

sealed interface PdfEffect {
    data class Share(val uris: List<String>, val mimeType: String) : PdfEffect

    data class ShowError(val error: AppError) : PdfEffect
}

@HiltViewModel(assistedFactory = PdfToolViewModel.Factory::class)
class PdfToolViewModel @AssistedInject constructor(
    @Assisted private val tool: PdfTool,
    @Assisted private val sizeTarget: KbTarget?,
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
        fun create(tool: PdfTool, sizeTarget: KbTarget?): PdfToolViewModel
    }

    private val mutableState = MutableStateFlow(PdfToolUiState(tool, sizeTarget = sizeTarget))
    val state: StateFlow<PdfToolUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<PdfEffect>(Channel.BUFFERED)
    val effects: Flow<PdfEffect> = effectChannel.receiveAsFlow()

    private val runner = JobRunner<List<OutputFile>>(viewModelScope)
    private var jobId: String? = null

    init {
        viewModelScope.launch {
            runner.state.collect { job ->
                mutableState.update { it.copy(job = job) }
            }
        }
    }

    fun onFilesPicked(picked: List<String>) {
        // The same file twice in a merge is almost always a mis-tap, and unique URIs keep list keys stable.
        val uris = if (tool.multipleInputs) {
            picked.distinct().filter { uri -> state.value.files.none { it.uri == uri } }
        } else {
            picked
        }
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
                    if (tool == PdfTool.MERGE) described.value.forEach { loadPageCount(it) }
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

    private suspend fun loadPageCount(file: FileRef) {
        getInfo(file).fold(
            onSuccess = { info ->
                mutableState.update { it.copy(pageCounts = it.pageCounts + (file.uri to info.pageCount)) }
            },
            onFailure = {},
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
            PdfTool.SPLIT -> PageSelection.splitPlan(o.splitMode, o.pages, o.everyN.toIntOrNull(), pageCount).map {
                PdfOperation.Split(first, it, dir, base)
            }
            PdfTool.EXTRACT, PdfTool.REORDER -> pages().map { PdfOperation.SelectPages(first, it, dir, "$base-pages") }
            PdfTool.DELETE -> pages().map { remove ->
                PdfOperation.SelectPages(first, (1..pageCount).filterNot { it in remove.toSet() }, dir, "$base-edited")
            }
            PdfTool.ROTATE -> pages().map { PdfOperation.Rotate(first, it.toSet(), o.degrees, dir, "$base-rotated") }
            PdfTool.COMPRESS -> success(PdfOperation.Compress(first, o.level, dir, "$base-compressed", o.grayscale))
            PdfTool.IMAGES_TO_PDF -> success(
                PdfOperation.ImagesToPdf(s.files, o.pageSize, o.margin.points, dir, "images", o.orientation),
            )
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
        mutableState.value = PdfToolUiState(tool, sizeTarget = sizeTarget)
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
        const val WATERMARK_SIZE_PT = 48f
    }
}
