package com.iponlove.app.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.settings.domain.usecase.ObserveBudgetAlertsEnabledUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveRecurringRemindersEnabledUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetBudgetAlertsEnabledUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetRecurringRemindersEnabledUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val setBudgetAlertsEnabledUseCase: SetBudgetAlertsEnabledUseCase,
    observeBudgetAlertsEnabled: ObserveBudgetAlertsEnabledUseCase,
    private val setRecurringRemindersEnabledUseCase: SetRecurringRemindersEnabledUseCase,
    observeRecurringRemindersEnabled: ObserveRecurringRemindersEnabledUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState

    init {
        observeBudgetAlertsEnabled()
            .onEach { enabled -> _uiState.update { it.copy(budgetAlertsEnabled = enabled) } }
            .launchIn(viewModelScope)
        observeRecurringRemindersEnabled()
            .onEach { enabled -> _uiState.update { it.copy(recurringRemindersEnabled = enabled) } }
            .launchIn(viewModelScope)
    }

    fun setBudgetAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { setBudgetAlertsEnabledUseCase(enabled) }
    }

    fun setRecurringRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch { setRecurringRemindersEnabledUseCase(enabled) }
    }
}
