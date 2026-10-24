package com.mrfilefixit.feature.exam.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrfilefixit.core.domain.exam.ExamCatalog
import com.mrfilefixit.core.domain.exam.ExamCategory
import com.mrfilefixit.core.domain.exam.ExamProfile
import com.mrfilefixit.core.domain.exam.GetExamProfilesUseCase
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.fold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ExamPickerUiState {
    data object Loading : ExamPickerUiState

    /** [category] null means "All". [visible] is what the list shows after search and filter. */
    data class Loaded(
        val profiles: List<ExamProfile>,
        val query: String = "",
        val category: ExamCategory? = null,
    ) : ExamPickerUiState {
        val categories: List<ExamCategory> get() = ExamCatalog.categories(profiles)
        val visible: List<ExamProfile> get() = ExamCatalog.filter(profiles, query, category)
    }

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

    fun onQueryChanged(query: String) = updateLoaded { it.copy(query = query.take(MAX_QUERY)) }

    fun onCategorySelected(category: ExamCategory?) = updateLoaded { it.copy(category = category) }

    fun onClearFilters() = updateLoaded { it.copy(query = "", category = null) }

    private inline fun updateLoaded(crossinline transform: (ExamPickerUiState.Loaded) -> ExamPickerUiState.Loaded) =
        mutableState.update { state -> if (state is ExamPickerUiState.Loaded) transform(state) else state }

    private companion object {
        const val MAX_QUERY = 60
    }
}
