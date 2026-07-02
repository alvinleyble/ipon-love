package com.iponlove.app.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.onboarding.domain.model.StarterBundle
import com.iponlove.app.feature.onboarding.domain.repository.OnboardingRepository
import com.iponlove.app.feature.onboarding.domain.usecase.SeedStarterDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Drives the onboarding graph's last step, the starter-template picker (ADR-0024). */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val seedStarterData: SeedStarterDataUseCase,
    private val onboardingRepository: OnboardingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingTemplatesUiState())
    val state: StateFlow<OnboardingTemplatesUiState> = _state.asStateFlow()

    fun toggleBundle(bundle: StarterBundle) {
        _state.update {
            val selected = it.selectedBundles
            it.copy(selectedBundles = if (bundle in selected) selected - bundle else selected + bundle)
        }
    }

    /** Seeds whatever bundles are still checked (possibly none — "fully skippable"), then
     *  marks onboarding done so the gate never re-prompts this device. */
    fun finish(onDone: () -> Unit) {
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            seedStarterData(_state.value.selectedBundles)
            onboardingRepository.setOnboardingDone()
            onDone()
        }
    }

    fun skip(onDone: () -> Unit) {
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            onboardingRepository.setOnboardingDone()
            onDone()
        }
    }
}
