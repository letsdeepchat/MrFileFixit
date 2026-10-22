package com.mrfilefixit.feature.exam.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrfilefixit.core.domain.exam.ExamProfile
import com.mrfilefixit.core.domain.exam.GetExamProfilesUseCase
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.fold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ExamPickerUiState {
    data object Loading : ExamPickerUiState

    data class Loaded(val profiles: List<ExamProfile>) : ExamPickerUiState

    data class Error(val error: AppError) : ExamPickerUiState
}

@HiltViewModel
class ExamPickerViewModel @Inject constructor(
    private val getProfiles: GetExamProfilesUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ExamPickerUiState>(ExamPickerUiState.Loading)
    val state: StateFlow<ExamPickerUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutableState.value = ExamPickerUiState.Loading
        viewModelScope.launch {
            mutableState.value = getProfiles().fold(
                onSuccess = { ExamPickerUiState.Loaded(it) },
                onFailure = { ExamPickerUiState.Error(it) },
            )
        }
    }
}
