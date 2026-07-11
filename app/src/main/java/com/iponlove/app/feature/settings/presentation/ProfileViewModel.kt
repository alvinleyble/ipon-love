package com.iponlove.app.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.auth.domain.model.AuthException
import com.iponlove.app.feature.auth.domain.usecase.AuthCredentials
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
    private val updateDisplayName: UpdateDisplayNameUseCase,
    private val updateAccentColor: UpdateAccentColorUseCase,
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
}
