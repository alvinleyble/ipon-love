package com.iponlove.app.feature.settings.presentation

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.iponlove.app.BuildConfig
import com.iponlove.app.core.network.ConnectivityObserver
import com.iponlove.app.core.sync.SyncWorker
import com.iponlove.app.feature.auth.data.GoogleCredentialClient
import com.iponlove.app.feature.auth.data.GoogleCredentialResult
import com.iponlove.app.feature.auth.domain.model.AuthException
import com.iponlove.app.feature.auth.domain.usecase.AuthCredentials
import com.iponlove.app.feature.auth.domain.usecase.ChangeEmailUseCase
import com.iponlove.app.feature.auth.domain.usecase.ChangePasswordUseCase
import com.iponlove.app.feature.auth.domain.usecase.GetLinkedGoogleIdentityUseCase
import com.iponlove.app.feature.auth.domain.usecase.LinkGoogleIdentityUseCase
import com.iponlove.app.feature.auth.domain.usecase.RefreshSessionUseCase
import com.iponlove.app.feature.auth.presentation.message
import com.iponlove.app.feature.settings.domain.usecase.DeleteUserAccountUseCase
import com.iponlove.app.feature.settings.domain.usecase.PreviewResetFinancesUseCase
import com.iponlove.app.feature.settings.domain.usecase.ResetFinancesUseCase
import com.iponlove.app.feature.user.domain.usecase.GetAccountEmailUseCase
import com.iponlove.app.feature.user.domain.usecase.ObserveCurrentUserUseCase
import com.iponlove.app.feature.user.domain.usecase.UpdateAccentColorUseCase
import com.iponlove.app.feature.user.domain.usecase.UpdateAvatarMotifUseCase
import com.iponlove.app.feature.user.domain.usecase.UpdateDisplayNameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    observeCurrentUser: ObserveCurrentUserUseCase,
    private val getAccountEmail: GetAccountEmailUseCase,
    connectivity: ConnectivityObserver,
    private val updateDisplayName: UpdateDisplayNameUseCase,
    private val updateAccentColor: UpdateAccentColorUseCase,
    private val updateAvatarMotif: UpdateAvatarMotifUseCase,
    private val previewResetFinances: PreviewResetFinancesUseCase,
    private val resetFinances: ResetFinancesUseCase,
    private val deleteUserAccount: DeleteUserAccountUseCase,
    private val changePassword: ChangePasswordUseCase,
    private val changeEmail: ChangeEmailUseCase,
    private val refreshSession: RefreshSessionUseCase,
    private val linkGoogleIdentity: LinkGoogleIdentityUseCase,
    private val getLinkedGoogleIdentity: GetLinkedGoogleIdentityUseCase,
    private val googleCredentialClient: GoogleCredentialClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState(email = getAccountEmail()))
    val uiState: StateFlow<ProfileUiState> = _uiState

    // Seed the name field from the stored row once; later edits must not be clobbered by
    // re-emissions (e.g. when the accent color changes and the user row re-emits).
    private var nameSeeded = false

    init {
        viewModelScope.launch {
            observeCurrentUser().collect { user ->
                _uiState.update { state ->
                    state.copy(
                        nameDraft = if (nameSeeded) state.nameDraft else user?.displayName.orEmpty(),
                        accentColor = user?.accentColor,
                        avatarMotif = user?.avatarMotif,
                        isPaired = user?.coupleId != null,
                    )
                }
                if (user != null) nameSeeded = true
            }
        }
        // Live online state drives the reset dialog's confirm gate (ADR-0037): reset needs the
        // network for its re-auth, so the button stays disabled while offline.
        viewModelScope.launch {
            connectivity.observe().collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
    }

    fun onNameChange(value: String) =
        _uiState.update { it.copy(nameDraft = AuthCredentials.filterNameInput(value), saved = false) }

    fun saveName() {
        val name = _uiState.value.nameDraft
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                updateDisplayName(name)
                _uiState.update { it.copy(saved = true) }
            } catch (_: AuthException) {
                // Reuses registration's validation (≤10, letters+spaces, non-blank); the input
                // filter already blocks bad chars/length, so this is just a defensive backstop.
            }
        }
    }

    fun onAccentColorSelected(hex: String) {
        viewModelScope.launch { updateAccentColor(hex) }
    }

    fun onMotifSelected(key: String) {
        viewModelScope.launch { updateAvatarMotif(key) }
    }

    /**
     * Refreshed on every Profile resume (Item 8 + ADR-0051): pulls the server user into the local
     * session, then re-reads the email (so a confirmed email change shows without a restart) and the
     * linked Google identity (so a Google-signup user, or one linked on another device, renders as
     * Connected). Both are otherwise one-shot session reads that go stale. Best-effort — offline
     * leaves the cached values in place.
     */
    fun refreshAccount() {
        viewModelScope.launch {
            refreshSession()
            val linked = getLinkedGoogleIdentity()
            _uiState.update {
                it.copy(
                    email = getAccountEmail(),
                    googleLinked = linked != null,
                    googleEmail = linked?.email,
                )
            }
        }
    }

    /**
     * Connect a Google account to the signed-in account (ADR-0051), reusing Item 2's Credential
     * Manager sheet. The native link is synchronous, so success flips the row here directly (no
     * deep-link/resume-diff) and failures surface typed — including "already connected to another
     * account". A dismissed picker is silent. Runs on its own [ProfileUiState.isGoogleLinking] flag.
     */
    fun connectGoogle(activity: Activity) {
        if (_uiState.value.isGoogleLinking) return
        _uiState.update {
            it.copy(isGoogleLinking = true, googleLinkError = null, googleJustLinked = false)
        }
        viewModelScope.launch {
            when (val result =
                googleCredentialClient.getIdToken(activity, BuildConfig.GOOGLE_WEB_CLIENT_ID)) {
                is GoogleCredentialResult.Success -> {
                    try {
                        linkGoogleIdentity(result.idToken, result.nonce)
                        // Success ⇒ linked regardless of the re-read's freshness; the email is
                        // best-effort from a post-link identity read.
                        refreshSession()
                        val linked = getLinkedGoogleIdentity()
                        _uiState.update {
                            it.copy(
                                isGoogleLinking = false,
                                googleLinked = true,
                                googleEmail = linked?.email,
                                googleJustLinked = true,
                                googleLinkError = null,
                            )
                        }
                    } catch (e: AuthException) {
                        _uiState.update {
                            it.copy(isGoogleLinking = false, googleLinkError = e.error.message())
                        }
                    }
                }
                // User dismissed the picker — stay silent, just drop the spinner.
                GoogleCredentialResult.Cancelled ->
                    _uiState.update { it.copy(isGoogleLinking = false) }
                is GoogleCredentialResult.Failure ->
                    _uiState.update {
                        it.copy(isGoogleLinking = false, googleLinkError = result.error.message())
                    }
            }
        }
    }

    fun openResetFinances() {
        _uiState.update { it.copy(showResetFinancesDialog = true, resetFinancesError = null) }
        viewModelScope.launch {
            val counts = previewResetFinances()
            _uiState.update { it.copy(resetFinancesCounts = counts) }
        }
    }

    fun dismissResetFinances() = _uiState.update {
        it.copy(
            showResetFinancesDialog = false,
            resetFinancesCounts = null,
            resetFinancesPassword = "",
            resetFinancesError = null,
        )
    }

    fun onResetFinancesPasswordChange(value: String) =
        _uiState.update { it.copy(resetFinancesPassword = value, resetFinancesError = null) }

    fun confirmResetFinances() {
        val password = _uiState.value.resetFinancesPassword
        if (password.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isResetFinancesLoading = true, resetFinancesError = null) }
            try {
                resetFinances(password)
                _uiState.update {
                    it.copy(
                        isResetFinancesLoading = false,
                        showResetFinancesDialog = false,
                        resetFinancesCounts = null,
                        resetFinancesPassword = "",
                        financesJustReset = true,
                    )
                }
            } catch (e: AuthException) {
                _uiState.update {
                    it.copy(isResetFinancesLoading = false, resetFinancesError = e.error.message())
                }
            }
        }
    }

    // ---- Change password (Item 8) ----

    fun openChangePassword() = _uiState.update {
        it.copy(
            showChangePasswordDialog = true,
            currentPasswordInput = "",
            newPasswordInput = "",
            confirmPasswordInput = "",
            changePasswordError = null,
            passwordChanged = false,
        )
    }

    fun dismissChangePassword() = _uiState.update { it.copy(showChangePasswordDialog = false) }

    fun onCurrentPasswordChange(value: String) =
        _uiState.update { it.copy(currentPasswordInput = value, changePasswordError = null) }

    fun onNewPasswordChange(value: String) =
        _uiState.update { it.copy(newPasswordInput = value, changePasswordError = null) }

    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPasswordInput = value, changePasswordError = null) }

    fun confirmChangePassword() {
        val s = _uiState.value
        if (!s.canConfirmChangePassword) return
        viewModelScope.launch {
            _uiState.update { it.copy(isChangePasswordLoading = true, changePasswordError = null) }
            try {
                changePassword(s.currentPasswordInput, s.newPasswordInput, s.confirmPasswordInput)
                _uiState.update {
                    it.copy(
                        isChangePasswordLoading = false,
                        passwordChanged = true,
                        currentPasswordInput = "",
                        newPasswordInput = "",
                        confirmPasswordInput = "",
                    )
                }
            } catch (e: AuthException) {
                _uiState.update {
                    it.copy(isChangePasswordLoading = false, changePasswordError = e.error.message())
                }
            }
        }
    }

    // ---- Change email (Item 8) ----

    fun openChangeEmail() = _uiState.update {
        it.copy(
            showChangeEmailDialog = true,
            changeEmailPasswordInput = "",
            newEmailInput = "",
            changeEmailError = null,
            emailChangeRequested = false,
        )
    }

    fun dismissChangeEmail() = _uiState.update { it.copy(showChangeEmailDialog = false) }

    fun onChangeEmailPasswordChange(value: String) =
        _uiState.update { it.copy(changeEmailPasswordInput = value, changeEmailError = null) }

    fun onNewEmailChange(value: String) =
        _uiState.update { it.copy(newEmailInput = value, changeEmailError = null) }

    fun confirmChangeEmail() {
        val s = _uiState.value
        if (!s.canConfirmChangeEmail) return
        viewModelScope.launch {
            _uiState.update { it.copy(isChangeEmailLoading = true, changeEmailError = null) }
            try {
                changeEmail(s.changeEmailPasswordInput, s.newEmailInput)
                _uiState.update {
                    it.copy(
                        isChangeEmailLoading = false,
                        emailChangeRequested = true,
                        changeEmailPasswordInput = "",
                    )
                }
            } catch (e: AuthException) {
                _uiState.update {
                    it.copy(isChangeEmailLoading = false, changeEmailError = e.error.message())
                }
            }
        }
    }

    // ---- Delete account (ADR-0045) ----

    fun openDeleteAccount() = _uiState.update {
        it.copy(showDeleteAccountDialog = true, deleteAccountPassword = "", deleteAccountError = null)
    }

    fun dismissDeleteAccount() = _uiState.update {
        it.copy(showDeleteAccountDialog = false, deleteAccountPassword = "", deleteAccountError = null)
    }

    fun onDeleteAccountPasswordChange(value: String) =
        _uiState.update { it.copy(deleteAccountPassword = value, deleteAccountError = null) }

    fun confirmDeleteAccount() {
        val password = _uiState.value.deleteAccountPassword
        if (password.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleteAccountLoading = true, deleteAccountError = null) }
            // Stop background sync before the destructive call so it can't race the teardown
            // (mirrors AuthViewModel.signOut).
            WorkManager.getInstance(context).cancelUniqueWork(SyncWorker.WORK_NAME)
            try {
                deleteUserAccount(password)
                // Success tears down the session → the app root's auth gate navigates to login;
                // no local UI update needed (this VM goes away with the wipe).
            } catch (e: AuthException) {
                // Wrong password (re-auth gate) — nothing was deleted.
                _uiState.update {
                    it.copy(isDeleteAccountLoading = false, deleteAccountError = e.error.message())
                }
            } catch (_: Exception) {
                // The RPC failed pre-completion (network / server) — the account is intact.
                _uiState.update {
                    it.copy(
                        isDeleteAccountLoading = false,
                        deleteAccountError = "Couldn't delete your account. Please try again.",
                    )
                }
            }
        }
    }
}
