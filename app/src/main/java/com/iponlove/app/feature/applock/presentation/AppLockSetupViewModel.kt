package com.iponlove.app.feature.applock.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.applock.domain.model.AppLockPreferences
import com.iponlove.app.feature.applock.domain.usecase.EnableBiometricUseCase
import com.iponlove.app.feature.applock.domain.usecase.MarkBiometricNudgeShownUseCase
import com.iponlove.app.feature.applock.domain.usecase.ObserveAppLockUseCase
import com.iponlove.app.feature.applock.domain.usecase.SetPinUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppLockSetupViewModel @Inject constructor(
    private val setPin: SetPinUseCase,
    private val enableBiometric: EnableBiometricUseCase,
    private val markNudgeShown: MarkBiometricNudgeShownUseCase,
    observeAppLock: ObserveAppLockUseCase,
) : ViewModel() {

    val preferences: StateFlow<AppLockPreferences> = observeAppLock()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLockPreferences())

    private val _uiState = MutableStateFlow(AppLockSetupUiState())
    val uiState: StateFlow<AppLockSetupUiState> = _uiState

    fun onDigit(digit: Char) {
        val state = _uiState.value
        when (state.step) {
            SetupStep.ENTER_NEW -> {
                if (state.newPin.length >= 4) return
                val next = state.newPin + digit
                _uiState.update { it.copy(newPin = next, error = null) }
                if (next.length == 4) _uiState.update { it.copy(step = SetupStep.CONFIRM) }
            }
            SetupStep.CONFIRM -> {
                if (state.confirmPin.length >= 4) return
                val next = state.confirmPin + digit
                _uiState.update { it.copy(confirmPin = next, error = null) }
                if (next.length == 4) confirmSet(state.newPin, next)
            }
        }
    }

    fun onDelete() {
        val state = _uiState.value
        when (state.step) {
            SetupStep.ENTER_NEW -> _uiState.update { it.copy(newPin = it.newPin.dropLast(1)) }
            SetupStep.CONFIRM -> {
                if (state.confirmPin.isEmpty()) {
                    _uiState.update { it.copy(step = SetupStep.ENTER_NEW, newPin = it.newPin.dropLast(1)) }
                } else {
                    _uiState.update { it.copy(confirmPin = it.confirmPin.dropLast(1)) }
                }
            }
        }
    }

    private fun confirmSet(newPin: String, confirmPin: String) {
        if (newPin != confirmPin) {
            _uiState.update {
                it.copy(step = SetupStep.ENTER_NEW, newPin = "", confirmPin = "", error = "PINs don't match. Try again.")
            }
            return
        }
        viewModelScope.launch {
            setPin(newPin)
            _uiState.update { it.copy(pinSetSuccess = true) }
        }
    }

    fun onBiometricToggle(enabled: Boolean) {
        viewModelScope.launch { enableBiometric(enabled) }
    }

    /** Records that the post-setup "enable biometric?" nudge (item 13) has been shown, so it
     *  never nags again on a later PIN change. */
    fun markBiometricNudgeShown() {
        viewModelScope.launch { markNudgeShown() }
    }

    fun resetPinSetSuccess() = _uiState.update {
        it.copy(pinSetSuccess = false, step = SetupStep.ENTER_NEW, newPin = "", confirmPin = "")
    }
}
