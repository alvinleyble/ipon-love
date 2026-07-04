package com.iponlove.app.feature.applock.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.applock.domain.model.PinVerifyResult
import com.iponlove.app.feature.applock.domain.usecase.ResetPinAfterReAuthUseCase
import com.iponlove.app.feature.applock.domain.usecase.VerifyPinUseCase
import com.iponlove.app.feature.auth.domain.model.AuthException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    private var lockoutClearJob: Job? = null

    init {
        // This ViewModel is activity-scoped, so it survives every lock/unlock cycle. Without
        // this, a verified PIN's filled dots leak into the next lock (onDigit early-returns at
        // length 4, so the pad looks stuck). Clear the entry whenever the lock re-engages.
        viewModelScope.launch {
            appLockManager.isLocked.collect { locked ->
                // Keep the lockout message visible across a relock — otherwise the pad reads as
                // frozen with no explanation until lockoutClearJob eventually fires.
                if (locked) _uiState.update {
                    it.copy(pin = "", error = if (it.lockoutRemainingSeconds > 0) it.error else null)
                }
            }
        }
    }

    fun onDigit(digit: Char) {
        val current = _uiState.value
        if (current.lockoutRemainingSeconds > 0) return
        if (current.pin.length >= 4) return
        val next = current.pin + digit
        _uiState.update { it.copy(pin = next, error = null) }
        if (next.length == 4) verify(next)
    }

    fun onDelete() {
        if (_uiState.value.lockoutRemainingSeconds > 0) return
        _uiState.update { it.copy(pin = it.pin.dropLast(1), error = null) }
    }

    private fun verify(pin: String) {
        viewModelScope.launch {
            when (val result = verifyPin(pin)) {
                is PinVerifyResult.Success -> {
                    lockoutClearJob?.cancel()
                    _uiState.update { it.copy(pin = "", error = null, lockoutRemainingSeconds = 0) }
                    appLockManager.unlock()
                }
                is PinVerifyResult.Failed -> {
                    _uiState.update { it.copy(pin = "", error = "Incorrect PIN", lockoutRemainingSeconds = 0) }
                }
                is PinVerifyResult.LockedOut -> {
                    _uiState.update {
                        it.copy(
                            pin = "",
                            error = "Too many attempts. Try again in ${result.remainingSeconds}s.",
                            lockoutRemainingSeconds = result.remainingSeconds,
                        )
                    }
                    // Auto re-enable the pad once the cooldown elapses so the user doesn't have to
                    // guess when they can retry.
                    lockoutClearJob?.cancel()
                    lockoutClearJob = viewModelScope.launch {
                        delay(result.remainingSeconds * 1000)
                        _uiState.update { it.copy(error = null, lockoutRemainingSeconds = 0) }
                    }
                }
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
