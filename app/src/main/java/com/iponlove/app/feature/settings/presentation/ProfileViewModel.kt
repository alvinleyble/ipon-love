package com.iponlove.app.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.network.ConnectivityObserver
import com.iponlove.app.feature.auth.domain.model.AuthException
import com.iponlove.app.feature.auth.domain.usecase.AuthCredentials
import com.iponlove.app.feature.auth.presentation.message
import com.iponlove.app.feature.settings.domain.usecase.PreviewResetFinancesUseCase
import com.iponlove.app.feature.settings.domain.usecase.ResetFinancesUseCase
import com.iponlove.app.feature.user.domain.usecase.GetAccountEmailUseCase
import com.iponlove.app.feature.user.domain.usecase.ObserveCurrentUserUseCase
import com.iponlove.app.feature.user.domain.usecase.UpdateAccentColorUseCase
import com.iponlove.app.feature.user.domain.usecase.UpdateDisplayNameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    observeCurrentUser: ObserveCurrentUserUseCase,
    getAccountEmail: GetAccountEmailUseCase,
    connectivity: ConnectivityObserver,
    private val updateDisplayName: UpdateDisplayNameUseCase,
    private val updateAccentColor: UpdateAccentColorUseCase,
    private val previewResetFinances: PreviewResetFinancesUseCase,
    private val resetFinances: ResetFinancesUseCase,
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
}
