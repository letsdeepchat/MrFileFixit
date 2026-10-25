package com.mrfilefixit.feature.scan.ui

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
import com.mrfilefixit.core.domain.job.JobRunner
import com.mrfilefixit.core.domain.job.JobState
import com.mrfilefixit.core.domain.ocr.OcrResult
import com.mrfilefixit.core.domain.ocr.OcrScript
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.collectAll
import com.mrfilefixit.core.domain.result.flatMap
import com.mrfilefixit.core.domain.result.fold
import com.mrfilefixit.core.domain.scan.EnhanceScanUseCase
import com.mrfilefixit.core.domain.scan.ImportScanUseCase
import com.mrfilefixit.core.domain.scan.RecognizeTextUseCase
import com.mrfilefixit.core.domain.scan.ScanCapture
import com.mrfilefixit.core.domain.scan.ScanDocument
import com.mrfilefixit.core.domain.scan.ScanFilter
import com.mrfilefixit.core.domain.scan.asSource
import com.mrfilefixit.core.domain.security.InputLimits
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanUiState(
    /** Copying the scanner's output into our job directory. Its result is the unfiltered scan. */
    val scan: JobState<ScanDocument> = JobState.Idle,
    val filter: ScanFilter = ScanFilter.DOCUMENT_CLEAN,
    /** The scan re-rendered with [filter]. Idle for [ScanFilter.ORIGINAL]. */
    val enhanced: JobState<ScanDocument> = JobState.Idle,
    /** Files picked for OCR. When empty, OCR reads the scanned pages instead. */
    val ocrFiles: List<FileRef> = emptyList(),
    val script: OcrScript = OcrScript.LATIN,
    val ocr: JobState<OcrResult> = JobState.Idle,
    val inputError: AppError? = null,
) {
    val original: ScanDocument? get() = (scan as? JobState.Success)?.value

    /** What the user sees, shares and runs OCR on. Falls back to the original if enhancing failed. */
    val document: ScanDocument? get() = when {
        filter == ScanFilter.ORIGINAL -> original
        enhanced is JobState.Success -> enhanced.value
        enhanced is JobState.Failed || enhanced is JobState.Cancelled -> original
        else -> null
    }

    /** What OCR reads: the picked files, or else the scanned pages. Handed to the text recognition screen. */
    val ocrSourceUris: List<String>
        get() = ocrFiles.map { it.uri }.ifEmpty { document?.pages.orEmpty().map { it.asSource().uri } }

    val canRecognize: Boolean get() = ocr !is JobState.Running &&
        (ocrFiles.isNotEmpty() || document?.pages?.isNotEmpty() == true)
}

sealed interface ScanEffect {
    data class Share(val uri: String, val mimeType: String) : ScanEffect

    data class ShowError(val error: AppError) : ScanEffect
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val importScan: ImportScanUseCase,
    private val enhanceScan: EnhanceScanUseCase,
    private val recognizeText: RecognizeTextUseCase,
    private val describeFile: DescribeFileUseCase,
    private val saveOutput: SaveOutputUseCase,
    private val shareOutput: ShareOutputUseCase,
    private val tempFiles: TempFileStore,
    private val jobIds: JobIdGenerator,
    private val limits: InputLimits,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<ScanEffect>(Channel.BUFFERED)
    val effects: Flow<ScanEffect> = effectChannel.receiveAsFlow()

    private val scanRunner = JobRunner<ScanDocument>(viewModelScope)
    private val enhanceRunner = JobRunner<ScanDocument>(viewModelScope)
    private val ocrRunner = JobRunner<OcrResult>(viewModelScope)
    private var jobId: String? = null

    init {
        viewModelScope.launch {
            scanRunner.state.collect { job ->
                mutableState.update { it.copy(scan = job) }
                // Enhance as soon as the scan is in: clean pages are what most people want to share.
                if (job is JobState.Success) enhance(state.value.filter)
            }
        }
        viewModelScope.launch { enhanceRunner.state.collect { job -> mutableState.update { it.copy(enhanced = job) } } }
        viewModelScope.launch {
            ocrRunner.state.collect { job ->
                mutableState.update { it.copy(ocr = job) }
            }
        }
    }

    /** The scanner returned pages. They are copied into our job directory straight away. */
    fun onScanCaptured(capture: ScanCapture) {
        val id = jobIds.next()
        discardOutputs()
        ocrRunner.reset()
        enhanceRunner.cancel()
        enhanceRunner.reset()
        mutableState.update { it.copy(inputError = null, ocrFiles = emptyList()) }
        scanRunner.start(cleanup = {
            if (scanRunner.state.value !is JobState.Success) tempFiles.deleteJobDir(id)
        }) { progress ->
            tempFiles.createJobDir(id).flatMap { dir ->
                jobId = id
                importScan(capture, dir, SCAN_BASE_NAME, progress)
            }
        }
    }

    /** Re-renders the scan with [filter]. The unfiltered pages are kept, so filters can be switched freely. */
    fun onFilterSelected(filter: ScanFilter) {
        if (filter == state.value.filter) return
        mutableState.update { it.copy(filter = filter) }
        ocrRunner.reset()
        enhance(filter)
    }

    private fun enhance(filter: ScanFilter) {
        val original = state.value.original ?: return
        val id = jobId ?: return
        enhanceRunner.cancel()
        enhanceRunner.reset()
        if (filter == ScanFilter.ORIGINAL) return
        enhanceRunner.start { progress ->
            // The scan's own job directory: it already exists, and is deleted with the scan.
            tempFiles.createJobDir(id).flatMap { dir ->
                enhanceScan(original, filter, dir, "$SCAN_BASE_NAME-${filter.name.lowercase()}", progress)
            }
        }
    }

    fun onScannerUnavailable() =
        mutableState.update { it.copy(inputError = AppError.FeatureUnavailable(AppError.Feature.DOCUMENT_SCANNER)) }

    fun onOcrFilesPicked(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val described = limits.checkFileCount(uris.size).flatMap { uris.map { describeFile(it) }.collectAll() }
            when (described) {
                is Result.Failure -> mutableState.update { it.copy(inputError = described.error) }
                is Result.Success -> {
                    ocrRunner.reset()
                    mutableState.update { it.copy(ocrFiles = described.value, inputError = null) }
                }
            }
        }
    }

    fun onScriptChanged(script: OcrScript) = mutableState.update { it.copy(script = script) }

    fun onRecognize() {
        val s = state.value
        if (!s.canRecognize) return
        val sources = s.ocrFiles.ifEmpty { s.document?.pages.orEmpty().map { it.asSource() } }
        ocrRunner.start { progress -> recognizeText(sources, s.script, progress) }
    }

    fun onCancel() {
        scanRunner.cancel()
        enhanceRunner.cancel()
        ocrRunner.cancel()
    }

    fun onSave(output: OutputFile, destination: String) {
        viewModelScope.launch {
            saveOutput(output, DestinationUri(destination)).fold({}, { effectChannel.send(ScanEffect.ShowError(it)) })
        }
    }

    fun onShare(output: OutputFile) {
        viewModelScope.launch {
            shareOutput(output).fold(
                onSuccess = { effectChannel.send(ScanEffect.Share(it, output.mimeType)) },
                onFailure = { effectChannel.send(ScanEffect.ShowError(it)) },
            )
        }
    }

    fun onReset() {
        onCancel()
        scanRunner.reset()
        enhanceRunner.reset()
        ocrRunner.reset()
        discardOutputs()
        mutableState.update { ScanUiState(script = it.script, filter = it.filter) }
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
        const val SCAN_BASE_NAME = "scan"
    }
}
