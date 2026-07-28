package com.iponlove.app.feature.settings.presentation

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.couple.domain.usecase.ObserveCoupleMembersUseCase
import com.iponlove.app.feature.recurring.worker.RecurringReminderWorker
import com.iponlove.app.feature.settings.domain.usecase.ObserveBudgetAlertsEnabledUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveBudgetOverAlertsEnabledUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveBudgetOverThresholdUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveBudgetWarnThresholdUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveOffAppRecurringRemindersEnabledUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObservePartnerDebtAlertsEnabledUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveRecurringRemindersEnabledUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveRecurringSweepArmedUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetBudgetAlertsEnabledUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetBudgetOverAlertsEnabledUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetBudgetOverThresholdUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetBudgetWarnThresholdUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetOffAppRecurringRemindersEnabledUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetPartnerDebtAlertsEnabledUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetRecurringRemindersEnabledUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val setBudgetAlertsEnabledUseCase: SetBudgetAlertsEnabledUseCase,
    observeBudgetAlertsEnabled: ObserveBudgetAlertsEnabledUseCase,
    private val setBudgetWarnThresholdUseCase: SetBudgetWarnThresholdUseCase,
    observeBudgetWarnThreshold: ObserveBudgetWarnThresholdUseCase,
    private val setBudgetOverAlertsEnabledUseCase: SetBudgetOverAlertsEnabledUseCase,
    observeBudgetOverAlertsEnabled: ObserveBudgetOverAlertsEnabledUseCase,
    private val setBudgetOverThresholdUseCase: SetBudgetOverThresholdUseCase,
    observeBudgetOverThreshold: ObserveBudgetOverThresholdUseCase,
    private val setRecurringRemindersEnabledUseCase: SetRecurringRemindersEnabledUseCase,
    observeRecurringRemindersEnabled: ObserveRecurringRemindersEnabledUseCase,
    private val setOffAppRecurringRemindersEnabledUseCase: SetOffAppRecurringRemindersEnabledUseCase,
    observeOffAppRecurringRemindersEnabled: ObserveOffAppRecurringRemindersEnabledUseCase,
    private val observeRecurringSweepArmed: ObserveRecurringSweepArmedUseCase,
    private val setPartnerDebtAlertsEnabledUseCase: SetPartnerDebtAlertsEnabledUseCase,
    observePartnerDebtAlertsEnabled: ObservePartnerDebtAlertsEnabledUseCase,
    observeCoupleMembers: ObserveCoupleMembersUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState

    init {
        observeBudgetAlertsEnabled()
            .onEach { enabled -> _uiState.update { it.copy(budgetAlertsEnabled = enabled) } }
            .launchIn(viewModelScope)
        observeBudgetWarnThreshold()
            .onEach { percent -> _uiState.update { it.copy(budgetWarnThresholdPercent = percent) } }
            .launchIn(viewModelScope)
        observeBudgetOverAlertsEnabled()
            .onEach { enabled -> _uiState.update { it.copy(budgetOverAlertsEnabled = enabled) } }
            .launchIn(viewModelScope)
        observeBudgetOverThreshold()
            .onEach { percent -> _uiState.update { it.copy(budgetOverThresholdPercent = percent) } }
            .launchIn(viewModelScope)
        observeRecurringRemindersEnabled()
            .onEach { enabled -> _uiState.update { it.copy(recurringRemindersEnabled = enabled) } }
            .launchIn(viewModelScope)
        observeOffAppRecurringRemindersEnabled()
            .onEach { enabled -> _uiState.update { it.copy(offAppRecurringRemindersEnabled = enabled) } }
            .launchIn(viewModelScope)
        observePartnerDebtAlertsEnabled()
            .onEach { enabled -> _uiState.update { it.copy(partnerDebtAlertsEnabled = enabled) } }
            .launchIn(viewModelScope)
        observeCoupleMembers()
            .map { it != null }
            .onEach { paired -> _uiState.update { it.copy(isPaired = paired) } }
            .launchIn(viewModelScope)
    }

    fun setBudgetAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { setBudgetAlertsEnabledUseCase(enabled) }
    }

    fun setBudgetWarnThreshold(percent: Int) {
        viewModelScope.launch { setBudgetWarnThresholdUseCase(percent) }
    }

    fun setBudgetOverAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { setBudgetOverAlertsEnabledUseCase(enabled) }
    }

    fun setBudgetOverThreshold(percent: Int) {
        viewModelScope.launch { setBudgetOverThresholdUseCase(percent) }
    }

    fun setRecurringRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            setRecurringRemindersEnabledUseCase(enabled)
            // The master gates the sweep too — turning reminders off must tear the schedule down,
            // not leave it waking the phone to discover there's nothing to do.
            syncSweepSchedule()
        }
    }

    fun setOffAppRecurringRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            setOffAppRecurringRemindersEnabledUseCase(enabled)
            syncSweepSchedule()
        }
    }

    /**
     * Persist first, then reconcile the schedule (ADR-0056 decision 8) — the toggle-on/toggle-off
     * half of the sweep's lifecycle. Preference-first so a failed WorkManager call leaves the login
     * self-heal in `MainActivity` a correct value to reconcile against; the reverse order would
     * strand a schedule the stored preference disowns. Reads the combined value back rather than
     * trusting the argument, so both toggles share one rule.
     */
    private suspend fun syncSweepSchedule() {
        RecurringReminderWorker.setPeriodicEnabled(context, observeRecurringSweepArmed().first())
    }

    /**
     * Re-read the OS-level notification permission. Called on every resume, not just at init: the
     * banner's whole job is to send the user to system settings, so it has to notice when they
     * come back having granted (or revoked) it.
     */
    fun refreshNotificationPermission() {
        val blocked = !NotificationManagerCompat.from(context).areNotificationsEnabled()
        _uiState.update { it.copy(notificationsBlocked = blocked) }
    }

    fun setPartnerDebtAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { setPartnerDebtAlertsEnabledUseCase(enabled) }
    }
}
