package com.iponlove.app.feature.settings.presentation

import java.time.Instant

/**
 * Settings root UI state (v1.6.5 Item 34 slimmed it): the Sync card (Item 9) and the
 * enforcement-gated Premium row. Appearance + Finance state moved to [AppearanceUiState] /
 * [FinanceUiState].
 */
data class PersonalizeUiState(
    /**
     * Whether to show the Premium Settings row (paywall S5 / Item 12). Driven by the remote
     * enforcement kill-switch: the entry stays **hidden while dormant** (enforcement OFF, the
     * ship default) and appears only once enforcement is flipped ON — which is Alvin's post-beta
     * "explicit go" (§10.7). No separate flag needed; enforcement *is* the go.
     */
    val showPremiumEntry: Boolean = false,
    /** Labels the Premium row "active" vs "upgrade" — the user's own cached entitlement. */
    val isPremium: Boolean = false,
    /** Sync card (Item 9). Last successful full sync, persisted across launches via
     *  SyncStatusStore; null = never synced (or wiped on account switch). */
    val lastSyncedAt: Instant? = null,
    /** A full sync() is in flight — "Sync now" disables into "Syncing…". */
    val isSyncing: Boolean = false,
    /** The engine's last run failed. Transient friendly copy only — the raw cause goes to
     *  logcat (`Log.w`) in the engine, never the UI. */
    val syncFailed: Boolean = false,
    /** Offline disables "Sync now" with a reconnect hint (ConnectivityObserver, Item 16 infra). */
    val isOnline: Boolean = true,
)
