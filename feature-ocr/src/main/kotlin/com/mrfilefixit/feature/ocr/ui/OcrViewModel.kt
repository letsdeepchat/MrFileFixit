package com.mrfilefixit.feature.ocr.ui

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
import com.mrfilefixit.core.domain.ocr.ExportOcrUseCase
import com.mrfilefixit.core.domain.ocr.OcrExportFormat
import com.mrfilefixit.core.domain.ocr.OcrResult
import com.mrfilefixit.core.domain.ocr.OcrScript
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.AppResult
import com.mrfilefixit.core.domain.result.Result
import com.mrfilefixit.core.domain.result.collectAll
import com.mrfilefixit.core.domain.result.failure
import com.mrfilefixit.core.domain.result.flatMap
import com.mrfilefixit.core.domain.result.fold
import com.mrfilefixit.core.domain.result.success
import com.mrfilefixit.core.domain.scan.RecognizeTextUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OcrUiState(
    val files: List<FileRef> = emptyList(),
    val script: OcrScript = OcrScript.AUTO,
    val ocr: JobState<OcrResult> = JobState.Idle,
    val export: JobState<OutputFile> = JobState.Idle,
    /** The format of the running or finished export. */
    val exportFormat: OcrExportFormat? = null,
    val inputError: AppError? = null,
) {
    val result: OcrResult? get() = (ocr as? JobState.Success)?.value
    val exported: OutputFile? get() = (export as? JobState.Success)?.value
    val canRecognize: Boolean get() = files.isNotEmpty() && ocr !is JobState.Running
}

sealed interface OcrEffect {
    data class Share(val uri: String, val mimeType: String) : OcrEffect

    data class ShowError(val error: AppError) : OcrEffect
}

/**
 * Text recognition on picked images and PDFs (or pages handed over by Scan as [initialUris]): recognize in the
 * chosen script or detect it, then copy the text or export it as a searchable PDF, TXT, Word or a text PDF.
 */
@HiltViewModel(assistedFactory = OcrViewModel.Factory::class)
class OcrViewModel @AssistedInject constructor(
    @Assisted initialUris: List<String>,
    private val describeFile: DescribeFileUseCase,
    private val recognizeText: RecognizeTextUseCase,
    private val exportOcr: ExportOcrUseCase,
    private val saveOutput: SaveOutputUseCase,
    private val shareOutput: ShareOutputUseCase,
    private val tempFiles: TempFileStore,
    private val ids: JobIdGenerator,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(initialUris: List<String>): OcrViewModel
    }

    private val mutableState = MutableStateFlow(OcrUiState())
    val state: StateFlow<OcrUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<OcrEffect>(Channel.BUFFERED)
    val effects: Flow<OcrEffect> = effectChannel.receiveAsFlow()

    private val ocrRunner = JobRunner<OcrResult>(viewModelScope)
    private val exportRunner = JobRunner<OutputFile>(viewModelScope)
    private var exportJobId: String? = null

    init {
        viewModelScope.launch { ocrRunner.state.collect { job -> mutableState.update { it.copy(ocr = job) } } }
        viewModelScope.launch { exportRunner.state.collect { job -> mutableState.update { it.copy(export = job) } } }
        onFilesPicked(initialUris)
    }

    fun onFilesPicked(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            when (val described = uris.map { describe(it) }.collectAll()) {
                is Result.Failure -> mutableState.update { it.copy(inputError = described.error) }
                is Result.Success -> {
                    clearResults()
                    mutableState.update { it.copy(files = described.value, inputError = null) }
                }
            }
        }
    }

    /** Scan hands over its own pages as file:// URIs under the job cache; everything else came from the picker. */
    private suspend fun describe(uri: String): AppResult<FileRef> {
        if (!uri.startsWith(FILE_SCHEME)) return describeFile(uri)
        val file = File(uri.removePrefix(FILE_SCHEME))
        if (!file.path.contains(JOB_DIR_MARKER) || !file.isFile) return failure(AppError.FileNotFound)
        val mime = if (file.extension.equals(PDF_EXTENSION, ignoreCase = true)) PDF_MIME else JPEG_MIME
        return success(FileRef(uri, file.name, mime, file.length()))
    }

    fun onScriptChanged(script: OcrScript) {
        if (script == state.value.script) return
        clearResults()
        mutableState.update { it.copy(script = script) }
    }

    fun onRecognize() {
        val s = state.value
        if (!s.canRecognize) return
        clearResults()
        ocrRunner.start { progress -> recognizeText(s.files, s.script, progress) }
    }

    fun onCancel() {
        ocrRunner.cancel()
        exportRunner.cancel()
    }

    fun onExport(format: OcrExportFormat) {
        val s = state.value
        val result = s.result ?: return
        if (s.export is JobState.Running) return
        discardExport()
        val jobId = ids.next()
        exportJobId = jobId
        mutableState.update { it.copy(exportFormat = format) }
        exportRunner.start(cleanup = {
            if (exportRunner.state.value !is JobState.Success) tempFiles.deleteJobDir(jobId)
        }) { progress ->
            tempFiles.createJobDir(jobId).flatMap { dir -> exportOcr(s.files, result, format, dir, progress) }
        }
    }

    fun onSave(destination: String) {
        val output = state.value.exported ?: return
        viewModelScope.launch {
            saveOutput(output, DestinationUri(destination)).fold({}, { effectChannel.send(OcrEffect.ShowError(it)) })
        }
    }

    fun onShare() {
        val output = state.value.exported ?: return
        viewModelScope.launch {
            shareOutput(output).fold(
                onSuccess = { effectChannel.send(OcrEffect.Share(it, output.mimeType)) },
                onFailure = { effectChannel.send(OcrEffect.ShowError(it)) },
            )
        }
    }

    fun onReset() {
        clearResults()
        mutableState.update { OcrUiState(script = it.script) }
    }

    private fun clearResults() {
        onCancel()
        ocrRunner.reset()
        exportRunner.reset()
        discardExport()
        mutableState.update { it.copy(exportFormat = null) }
    }

    private fun discardExport() {
        val id = exportJobId ?: return
        exportJobId = null
        appScope.launch { tempFiles.deleteJobDir(id) }
    }

    override fun onCleared() {
        discardExport()
        super.onCleared()
    }

    private companion object {
        const val FILE_SCHEME = "file://"
        const val JOB_DIR_MARKER = "/cache/jobs/"
        const val PDF_EXTENSION = "pdf"
        const val PDF_MIME = "application/pdf"
        const val JPEG_MIME = "image/jpeg"
    }
}
