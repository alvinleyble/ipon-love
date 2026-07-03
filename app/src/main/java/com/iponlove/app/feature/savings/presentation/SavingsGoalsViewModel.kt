package com.iponlove.app.feature.savings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.savings.domain.usecase.ObserveSavingsGoalsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavingsGoalsViewModel @Inject constructor(
    observeGoals: ObserveSavingsGoalsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavingsGoalsUiState())
    val uiState: StateFlow<SavingsGoalsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeGoals().collect { goals ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        active = goals.filterNot { g -> g.goal.isArchived },
                        archived = goals.filter { g -> g.goal.isArchived },
                    )
                }
            }
        }
    }

    fun toggleShowArchived() = _uiState.update { it.copy(showArchived = !it.showArchived) }
}
