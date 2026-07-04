package com.iponlove.app.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.auth.domain.model.AuthException
import com.iponlove.app.feature.auth.domain.usecase.SendPasswordResetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val sendPasswordReset: SendPasswordResetUseCase,
) : ViewModel() {

    private val _form = MutableStateFlow(ForgotPasswordUiState())
    val form: StateFlow<ForgotPasswordUiState> = _form

    fun onEmailChange(value: String) = _form.update { it.copy(email = value, error = null) }

    fun submit() {
        val state = _form.value
        if (!state.canSubmit) return
        _form.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                sendPasswordReset(state.email)
                _form.update { it.copy(isSubmitting = false, emailSent = true) }
            } catch (e: AuthException) {
                _form.update { it.copy(isSubmitting = false, error = e.error) }
            }
        }
    }
}
