package com.mrfilefixit.core.domain.file

import com.mrfilefixit.core.domain.result.AppResult
import javax.inject.Inject

class SaveOutputUseCase @Inject constructor(private val repository: FileRepository) {
    suspend operator fun invoke(output: OutputFile, destination: DestinationUri): AppResult<Unit> =
        repository.saveTo(output, destination)
}

class ShareOutputUseCase @Inject constructor(private val repository: FileRepository) {
    suspend operator fun invoke(output: OutputFile): AppResult<String> = repository.shareUri(output)
}
