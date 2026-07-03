package com.iponlove.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Resolved navbar state for the dynamic bottom bar, the More sheet, and the editor.
 * [visiblePinIds]/[startRoute] are derived purely via [NavResolver]; the UI maps ids back to
 * [NavDestination] through [NavRegistry].
 */
data class NavUiState(
    val loaded: Boolean = false,
    val isPaired: Boolean = false,
    val config: NavConfig = NavConfig(),
) {
    val visiblePinIds: List<String> get() = NavResolver.visiblePinIds(config, isPaired)
    val moreModuleIds: List<String> get() = NavResolver.moreModuleIds(config, isPaired)
    val startRoute: String get() = NavResolver.startRoute(config)
}

@HiltViewModel
class NavbarViewModel @Inject constructor(
    private val navConfigRepository: NavConfigRepository,
    observePairingState: ObservePairingStateUseCase,
) : ViewModel() {

    val uiState: StateFlow<NavUiState> = combine(
        navConfigRepository.observe(),
        observePairingState().map { it is PairingState.Paired },
    ) { config, paired ->
        NavUiState(loaded = true, isPaired = paired, config = config)
    }.stateIn(
        scope = viewModelScope,
        // Eagerly, not WhileSubscribed: this VM is Activity-scoped and drives the always-present
        // bottom bar. WhileSubscribed(5s) let the cached value freeze stale whenever the sole
        // subscriber (IponApp) was off-composition for longer than the grace window — e.g. the
        // whole onboarding flow — so a pairing that landed during that gap never reached the bar
        // until a full process restart (F11). The upstream is two cheap Room/DataStore flows, so
        // observing continuously for the session costs nothing and can't go stale.
        started = SharingStarted.Eagerly,
        initialValue = NavUiState(),
    )

    /**
     * Persist a layout the editor produced. The editor owns a working [NavConfig] and mutates it
     * through [NavConfig]'s own invariant-keeping methods, so this is a plain save.
     */
    fun applyConfig(config: NavConfig) {
        viewModelScope.launch { navConfigRepository.save(config) }
    }
}
