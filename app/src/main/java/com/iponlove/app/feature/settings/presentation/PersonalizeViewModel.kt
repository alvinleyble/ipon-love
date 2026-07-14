package com.iponlove.app.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.config.AppConfigRepository
import com.iponlove.app.core.entitlement.EntitlementRepository
import com.iponlove.app.core.network.ConnectivityObserver
import com.iponlove.app.core.sync.SyncEngine
import com.iponlove.app.core.sync.SyncState
import com.iponlove.app.core.sync.data.SyncStatusStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Settings root VM (v1.6.5 Item 34 slimmed it to two concerns): the top Sync card (Item 9) and
 * the enforcement-gated Premium row's visibility/label (S5 / Item 12). Appearance (palette + dark
 * mode) and Finance (currency + privacy + budget cycle) moved to their own sub-screen VMs.
 */
@HiltViewModel
class PersonalizeViewModel @Inject constructor(
    appConfig: AppConfigRepository,
    entitlement: EntitlementRepository,
    private val syncEngine: SyncEngine,
    syncStatusStore: SyncStatusStore,
    connectivity: ConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonalizeUiState())
    val uiState: StateFlow<PersonalizeUiState> = _uiState

    init {
        // The Premium entry is hidden while the paywall is dormant (enforcement OFF) and only
        // appears once enforcement flips ON (S5 / Item 12 / §10.7). Its label follows the user's
        // own cached entitlement, live.
        combine(
            appConfig.observe(),
            entitlement.observeSelf(),
        ) { config, self ->
            config.enforcementEnabled to self.isActive(Instant.now())
        }.onEach { (enforcementOn, isPremium) ->
            _uiState.update {
                it.copy(showPremiumEntry = enforcementOn, isPremium = isPremium)
            }
        }.launchIn(viewModelScope)

        // Sync card (Item 9): engine state (in-memory, boots Idle) + the persisted last-synced
        // timestamp + connectivity.
        combine(
            syncEngine.state,
            syncStatusStore.observe(),
            connectivity.observe(),
        ) { syncState, lastSyncedAt, online ->
            Triple(syncState, lastSyncedAt, online)
        }.onEach { (syncState, lastSyncedAt, online) ->
            _uiState.update {
                it.copy(
                    isSyncing = syncState is SyncState.Syncing,
                    syncFailed = syncState is SyncState.Error,
                    lastSyncedAt = lastSyncedAt,
                    isOnline = online,
                )
            }
        }.launchIn(viewModelScope)
    }

    /** "Sync now" — the same full sync() as pull-to-refresh; single-flight coalescing absorbs
     *  double-taps (ADR-0015). Failure surfaces reactively via [SyncEngine.state] as friendly
     *  copy only; the raw cause is already logged in the engine (Item 9). */
    fun syncNow() {
        viewModelScope.launch { runCatching { syncEngine.sync() } }
    }
}
