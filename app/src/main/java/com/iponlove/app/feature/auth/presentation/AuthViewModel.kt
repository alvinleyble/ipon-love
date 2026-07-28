package com.iponlove.app.feature.auth.presentation

import android.content.Context
import android.util.Log
import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.iponlove.app.BuildConfig
import com.iponlove.app.core.session.LocalDataWiper
import com.iponlove.app.core.sync.SyncEngine
import com.iponlove.app.core.sync.SyncWorker
import com.iponlove.app.feature.auth.data.GoogleCredentialClient
import com.iponlove.app.feature.auth.data.GoogleCredentialResult
import com.iponlove.app.feature.auth.domain.model.AuthError
import com.iponlove.app.feature.auth.domain.model.AuthException
import com.iponlove.app.feature.auth.domain.model.AuthStatus
import com.iponlove.app.feature.auth.domain.model.LoginLockoutPolicy
import com.iponlove.app.feature.auth.domain.repository.SignUpResult
import com.iponlove.app.feature.auth.domain.usecase.AuthCredentials
import com.iponlove.app.feature.auth.domain.usecase.ObserveAuthStatusUseCase
import com.iponlove.app.feature.auth.domain.usecase.SignInUseCase
import com.iponlove.app.feature.auth.domain.usecase.SignInWithGoogleUseCase
import com.iponlove.app.feature.auth.domain.usecase.SignOutUseCase
import com.iponlove.app.feature.auth.domain.usecase.SignUpUseCase
import com.iponlove.app.feature.recurring.worker.RecurringReminderWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
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
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val googleCredentialClient: GoogleCredentialClient,
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

    private var lockoutClearJob: Job? = null

    fun onNameChange(value: String) =
        _form.update { it.copy(name = AuthCredentials.filterNameInput(value), error = null) }

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
                // spinner in case we stayed (sign-up awaiting confirmation). A clean sign-in also
                // clears any accumulated failed-attempt count.
                lockoutClearJob?.cancel()
                _form.update {
                    it.copy(isSubmitting = false, failedSignInAttempts = 0, signInLockoutSeconds = 0)
                }
            } catch (e: AuthException) {
                _form.update { it.copy(isSubmitting = false, error = e.error) }
                // Only a wrong-credentials sign-in counts toward the lockout — a network/rate-limit
                // failure isn't a guess, and sign-up isn't rate-gated here (Item 17).
                if (state.mode == AuthMode.SIGN_IN && e.error == AuthError.INVALID_CREDENTIALS) {
                    registerFailedSignIn()
                }
            }
        }
    }

    /**
     * Google Sign-In (ADR-0050). Fetches a Google ID token on-device via Credential Manager, then
     * signs in through the same repository spine as email/password. Same tap serves sign-in,
     * sign-up, and implicit account linking — the SDK session flip drives the gate either way.
     * Runs on its own [AuthUiState.isGoogleSubmitting] flag so the email button is untouched.
     */
    fun signInWithGoogle(activity: Activity) {
        if (_form.value.isGoogleSubmitting) return
        _form.update { it.copy(isGoogleSubmitting = true, error = null, confirmationSent = false) }
        viewModelScope.launch {
            when (val result = googleCredentialClient.getIdToken(activity, BuildConfig.GOOGLE_WEB_CLIENT_ID)) {
                is GoogleCredentialResult.Success -> {
                    try {
                        signInWithGoogleUseCase(result.idToken, result.nonce)
                        // On success the status stream flips and the gate swaps screens; clearing
                        // the spinner covers the rare case we somehow stay on this screen.
                        _form.update { it.copy(isGoogleSubmitting = false) }
                    } catch (e: AuthException) {
                        _form.update { it.copy(isGoogleSubmitting = false, error = e.error) }
                    }
                }
                // User dismissed the picker — stay silent, just drop the spinner.
                GoogleCredentialResult.Cancelled ->
                    _form.update { it.copy(isGoogleSubmitting = false) }
                is GoogleCredentialResult.Failure ->
                    _form.update { it.copy(isGoogleSubmitting = false, error = result.error) }
            }
        }
    }

    private fun registerFailedSignIn() {
        val result = LoginLockoutPolicy.onFailure(_form.value.failedSignInAttempts)
        _form.update {
            it.copy(
                failedSignInAttempts = result.failedAttempts,
                signInLockoutSeconds = result.lockoutSeconds,
            )
        }
        if (result.lockoutSeconds > 0) {
            // Auto-re-enable the button once the cooldown elapses, so the user doesn't have to guess
            // when they can retry (mirrors AppLockViewModel's lockout clear).
            lockoutClearJob?.cancel()
            lockoutClearJob = viewModelScope.launch {
                delay(result.lockoutSeconds * 1000)
                _form.update { it.copy(signInLockoutSeconds = 0) }
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
            // A periodic request survives the session in WorkManager's own database — without this
            // a signed-out phone keeps waking every six hours forever (ADR-0056 decision 8). The
            // preference itself is device-global, so the next sign-in re-arms it from the stored value.
            RecurringReminderWorker.cancelPeriodic(context)
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
