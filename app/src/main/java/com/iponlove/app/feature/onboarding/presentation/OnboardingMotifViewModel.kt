package com.iponlove.app.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.ui.AvatarMotif
import com.iponlove.app.feature.user.domain.usecase.ObserveCurrentUserUseCase
import com.iponlove.app.feature.user.domain.usecase.UpdateAvatarMotifUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the onboarding graph's Motif step (Item 42). Reuses the same [UpdateAvatarMotifUseCase]
 * Settings → Profile already calls ([com.iponlove.app.feature.settings.presentation.ProfileViewModel.onMotifSelected])
 * — this screen is purely a second, earlier entry point onto the existing `avatar_motif` column
 * (v1.6.7 Item 3 Leg 1), not a new storage/model concern. [ObserveCurrentUserUseCase] both seeds
 * the pre-selection (defensive if onboarding is ever re-entered) and supplies the user's
 * `accent_color` (ADR-0014) to tint [com.iponlove.app.core.ui.MotifPicker], matching how
 * Settings → Profile tints the same picker.
 */
@HiltViewModel
class OnboardingMotifViewModel @Inject constructor(
    observeCurrentUser: ObserveCurrentUserUseCase,
    private val updateAvatarMotif: UpdateAvatarMotifUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingMotifUiState())
    val state: StateFlow<OnboardingMotifUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeCurrentUser().collect { user ->
                _state.update {
                    it.copy(
                        selected = user?.avatarMotif ?: AvatarMotif.Default.key,
                        accentColor = user?.accentColor,
                    )
                }
            }
        }
    }

    /** Instant persist on tap, no Apply gate — mirrors [OnboardingCurrencyViewModel.selectSymbol]
     *  and Profile's own [com.iponlove.app.feature.settings.presentation.ProfileViewModel.onMotifSelected]. */
    fun selectMotif(key: String) {
        _state.update { it.copy(selected = key) }
        viewModelScope.launch { updateAvatarMotif(key) }
    }
}
