package com.iponlove.app.feature.auth.presentation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.iponlove.app.core.session.LocalDataWiper
import com.iponlove.app.core.sync.SyncEngine
import com.iponlove.app.core.sync.SyncWorker
import com.iponlove.app.feature.auth.domain.model.AuthException
import com.iponlove.app.feature.auth.domain.model.AuthStatus
import com.iponlove.app.feature.auth.domain.repository.SignUpResult
import com.iponlove.app.feature.auth.domain.usecase.ObserveAuthStatusUseCase
import com.iponlove.app.feature.auth.domain.usecase.SignInUseCase
import com.iponlove.app.feature.auth.domain.usecase.SignOutUseCase
import com.iponlove.app.feature.auth.domain.usecase.SignUpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Drives the auth gate ([status]) and the login/sign-up form ([form]). One instance is
 * hoisted at the app root so the same status stream both decides the gate and reacts to
 * sign-in/out.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    observeAuthStatus: ObserveAuthStatusUseCase,
    private val signIn: SignInUseCase,
    private val signUp: SignUpUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val syncEngine: SyncEngine,
    private val localDataWiper: LocalDataWiper,
) : ViewModel() {

    val status: StateFlow<AuthStatus> = observeAuthStatus().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AuthStatus.Loading,
    )

    private val _form = MutableStateFlow(AuthUiState())
    val form: StateFlow<AuthUiState> = _form

    fun onNameChange(value: String) = _form.update { it.copy(name = value, error = null) }

    fun onEmailChange(value: String) = _form.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _form.update { it.copy(password = value, error = null) }

    fun onConfirmPasswordChange(value: String) =
        _form.update { it.copy(confirmPassword = value, error = null) }

    fun toggleMode() = _form.update {
        val next = if (it.mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN
        it.copy(mode = next, error = null, confirmationSent = false, confirmPassword = "")
    }

    fun submit() {
        val state = _form.value
        if (!state.canSubmit) return
        _form.update { it.copy(isSubmitting = true, error = null, confirmationSent = false) }
        viewModelScope.launch {
            try {
                when (state.mode) {
                    AuthMode.SIGN_IN -> signIn(state.email, state.password)
                    AuthMode.SIGN_UP -> onSignedUp(
                        signUp(state.name, state.email, state.password, state.confirmPassword),
                    )
                }
                // On success the status stream flips and the gate swaps screens; clear the
                // spinner in case we stayed (sign-up awaiting confirmation).
                _form.update { it.copy(isSubmitting = false) }
            } catch (e: AuthException) {
                _form.update { it.copy(isSubmitting = false, error = e.error) }
            }
        }
    }

    private fun onSignedUp(result: SignUpResult) {
        if (result == SignUpResult.CONFIRMATION_REQUIRED) {
            // Account made but not yet usable — drop back to sign-in with a "check email" note.
            _form.update {
                it.copy(
                    mode = AuthMode.SIGN_IN,
                    password = "",
                    confirmPassword = "",
                    confirmationSent = true,
                )
            }
        }
        // SIGNED_IN: confirmation is off server-side; the status stream authenticates us.
    }

    /**
     * Sign out and wipe this account's local data (ADR-0021). A best-effort push runs first so
     * a synced device loses nothing. If that push *throws*, there were pending rows we couldn't
     * send (offline) — wiping would lose them, so we surface a confirm instead of proceeding.
     * (A no-op push — nothing dirty — returns normally and wipes safely.)
     */
    fun signOut() {
        viewModelScope.launch {
            WorkManager.getInstance(context).cancelUniqueWork(SyncWorker.WORK_NAME)
            val flushed = runCatching { syncEngine.pushOnly() }.isSuccess
            if (flushed) {
                performSignOut()
            } else {
                _form.update { it.copy(signOutPendingConfirm = true) }
            }
        }
    }

    /** User chose to sign out despite unsynced changes — discard them and wipe. */
    fun confirmSignOutDiscardingChanges() {
        viewModelScope.launch {
            _form.update { it.copy(signOutPendingConfirm = false) }
            performSignOut()
        }
    }

    fun cancelSignOut() = _form.update { it.copy(signOutPendingConfirm = false) }

    private suspend fun performSignOut() {
        // Only wipe once the session is actually gone: if sign-out fails the same user stays
        // logged in (no leak), and we must not destroy the data they're still looking at.
        val signedOut = try {
            signOutUseCase()
            true
        } catch (_: AuthException) {
            false
        }
        if (signedOut) wipeWithRetry()
        _form.value = AuthUiState()
    }

    /**
     * A partial wipe wedges pairing on re-login (see [LocalDataWiper]'s ordering invariant), so
     * the wipe runs [NonCancellable] (scope death mid-wipe must not stop it partway), is retried
     * once on failure — every step is idempotent — and is logged, never swallowed.
     */
    private suspend fun wipeWithRetry() = withContext(NonCancellable) {
        try {
            localDataWiper.wipe()
        } catch (first: Exception) {
            Log.e(TAG, "sign-out wipe failed, retrying once", first)
            try {
                localDataWiper.wipe()
            } catch (second: Exception) {
                Log.e(TAG, "sign-out wipe failed twice; local state may be partial", second)
            }
        }
    }

    fun dismissError() = _form.update { it.copy(error = null) }

    private companion object {
        const val TAG = "AuthViewModel"
    }
}
