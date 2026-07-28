package com.iponlove.app.navigation

import android.os.SystemClock
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
 * [NavDestination] through [NavRegistry]. [isPaired] only feeds the editor's informational
 * "Paired only" caption — it never changes what renders (2026-07-04 redesign).
 */
data class NavUiState(
    val loaded: Boolean = false,
    val isPaired: Boolean = false,
    val config: NavConfig = NavConfig(),
) {
    val visiblePinIds: List<String> get() = NavResolver.visiblePinIds(config)
    val moreModuleIds: List<String> get() = NavResolver.moreModuleIds(config)
    val startRoute: String get() = NavResolver.startRoute(config)
}

@HiltViewModel
class NavbarViewModel @Inject constructor(
    private val navConfigRepository: NavConfigRepository,
    private val navStateStore: NavStateStore,
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

    /**
     * Record the module the user is in as they background the app (v1.6.6 Item 39) — the last
     * reliable moment before the ROM may force-stop us. Null on a transient standalone screen
     * (add/edit-transaction, nav editor): skip it so a restore never lands on a modal, keeping the
     * previously recorded module instead.
     */
    fun rememberLocation(moduleId: String?) {
        if (moduleId == null) return
        viewModelScope.launch { navStateStore.save(moduleId, SystemClock.elapsedRealtime()) }
    }

    /**
     * The module to switch to on a cold start, or null to stay on the home tab [homeModuleId].
     * See [NavRestorePolicy] for the recency / known-module rules.
     */
    suspend fun moduleToRestore(homeModuleId: String): String? =
        NavRestorePolicy.moduleToRestore(
            saved = navStateStore.read(),
            homeModuleId = homeModuleId,
            now = SystemClock.elapsedRealtime(),
            windowMs = RESTORE_WINDOW_MS,
            // Navigable, not merely known (ADR-0058): Calculator is still a registry entry but its
            // graph is gone, and a device backgrounded on it before that release has "calculator"
            // on disk — `containsKey` would pass it through and start the NavHost on a dead route.
            isRestorableModule = { NavRegistry.byId[it]?.navigable == true },
        )

    companion object {
        /** How long after backgrounding a return still restores the last module (Item 39). */
        private const val RESTORE_WINDOW_MS = 5 * 60 * 1000L
    }
}
