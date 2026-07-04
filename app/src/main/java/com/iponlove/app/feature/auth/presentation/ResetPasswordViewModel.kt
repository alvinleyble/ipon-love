package com.iponlove.app.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.auth.domain.model.AuthException
import com.iponlove.app.feature.auth.domain.usecase.SignOutUseCase
import com.iponlove.app.feature.auth.domain.usecase.UpdatePasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sets a new password on the recovery session, then signs out unconditionally (never continues
 * the recovery session into the app) so the user re-authenticates with their new password
 * (ADR-0027). This session never ran [com.iponlove.app.feature.user.domain.usecase.EnsureCurrentUserRowUseCase]
 * or a sync (`MainActivity` routes `PasswordRecovery` away from that path), so a plain sign-out
 * is enough — no local data wipe is needed here.
 */
@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val updatePassword: UpdatePasswordUseCase,
    private val signOut: SignOutUseCase,
) : ViewModel() {

    private val _form = MutableStateFlow(ResetPasswordUiState())
    val form: StateFlow<ResetPasswordUiState> = _form

    fun onPasswordChange(value: String) = _form.update { it.copy(password = value, error = null) }

    fun onConfirmPasswordChange(value: String) =
        _form.update { it.copy(confirmPassword = value, error = null) }

    fun submit() {
        val state = _form.value
        if (!state.canSubmit) return
        _form.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                updatePassword(state.password, state.confirmPassword)
            } catch (e: AuthException) {
                _form.update { it.copy(isSubmitting = false, error = e.error) }
                return@launch
            }
            runCatching { signOut() }
            _form.update { it.copy(isSubmitting = false) }
        }
    }

    /** Abandon the recovery session (no password change) and return to ordinary sign-in. */
    fun cancel() {
        if (_form.value.isSubmitting) return
        _form.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            runCatching { signOut() }
            _form.update { it.copy(isSubmitting = false) }
        }
    }
}
