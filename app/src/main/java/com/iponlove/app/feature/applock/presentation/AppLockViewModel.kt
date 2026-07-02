package com.iponlove.app.feature.applock.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.applock.domain.usecase.ResetPinAfterReAuthUseCase
import com.iponlove.app.feature.applock.domain.usecase.VerifyPinUseCase
import com.iponlove.app.feature.auth.domain.model.AuthException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val verifyPin: VerifyPinUseCase,
    private val resetPinAfterReAuth: ResetPinAfterReAuthUseCase,
    val appLockManager: AppLockManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState

    init {
        // This ViewModel is activity-scoped, so it survives every lock/unlock cycle. Without
        // this, a verified PIN's filled dots leak into the next lock (onDigit early-returns at
        // length 4, so the pad looks stuck). Clear the entry whenever the lock re-engages.
        viewModelScope.launch {
            appLockManager.isLocked.collect { locked ->
                if (locked) _uiState.update { it.copy(pin = "", error = null) }
            }
        }
    }

    fun onDigit(digit: Char) {
        val current = _uiState.value
        if (current.pin.length >= 4) return
        val next = current.pin + digit
        _uiState.update { it.copy(pin = next, error = null) }
        if (next.length == 4) verify(next)
    }

    fun onDelete() = _uiState.update { it.copy(pin = it.pin.dropLast(1), error = null) }

    private fun verify(pin: String) {
        viewModelScope.launch {
            if (verifyPin(pin)) {
                _uiState.update { it.copy(pin = "", error = null) }
                appLockManager.unlock()
            } else {
                _uiState.update { it.copy(pin = "", error = "Incorrect PIN") }
            }
        }
    }

    fun onBiometricSuccess() = appLockManager.unlock()

    fun onForgotPinEmailChange(value: String) =
        _uiState.update { it.copy(forgotPinEmail = value, forgotPinError = null) }

    fun onForgotPinPasswordChange(value: String) =
        _uiState.update { it.copy(forgotPinPassword = value, forgotPinError = null) }

    fun showForgotPin() = _uiState.update { it.copy(showForgotPinDialog = true, forgotPinError = null) }

    fun dismissForgotPin() = _uiState.update {
        it.copy(showForgotPinDialog = false, forgotPinEmail = "", forgotPinPassword = "", forgotPinError = null)
    }

    fun submitForgotPin() {
        val state = _uiState.value
        if (state.forgotPinEmail.isBlank() || state.forgotPinPassword.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isForgotPinLoading = true, forgotPinError = null) }
            try {
                resetPinAfterReAuth(state.forgotPinEmail, state.forgotPinPassword)
                appLockManager.unlock()
                _uiState.update { it.copy(isForgotPinLoading = false, showForgotPinDialog = false) }
            } catch (e: AuthException) {
                _uiState.update { it.copy(isForgotPinLoading = false, forgotPinError = e.error.name) }
            }
        }
    }
}
