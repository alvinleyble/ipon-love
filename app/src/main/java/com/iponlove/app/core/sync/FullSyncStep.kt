package com.iponlove.app.core.sync

/**
 * A hook that runs at the start of a *full* [SyncEngine.sync] only — never on the narrow
 * [SyncEngine.pushOnly] (debounced write→push) or [SyncEngine.pullOnly] (bell catch-up) passes.
 *
 * This is the deliberate difference from [PreSyncStep], which fires on `pushOnly` too: use
 * [FullSyncStep] for advisory, best-effort refresh work that should ride the
 * foreground / reconnect / WorkManager sync trigger but must NOT run on every keystroke-debounced
 * micro-push — e.g. reconciling Play entitlement (a Play Billing IPC) or refreshing the remote
 * paywall config (paywall S4). Failures are swallowed by the engine so they can never abort the
 * data sync that follows.
 */
interface FullSyncStep {
    suspend fun run()
}
