package com.iponlove.app.core.entitlement

import com.iponlove.app.core.sync.FullSyncStep
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rides the foreground / reconnect / WorkManager full sync to refresh own entitlement from Play
 * (paywall S4). A [FullSyncStep], not a [core.sync.PreSyncStep][com.iponlove.app.core.sync.PreSyncStep],
 * so the Play Billing IPC fires only on a real full sync — never on a keystroke-debounced
 * `pushOnly`. Any dirty users row it writes is picked up by the push that follows in the same run.
 */
@Singleton
class EntitlementReconcileStep @Inject constructor(
    private val entitlementRepository: EntitlementRepository,
) : FullSyncStep {
    override suspend fun run() = entitlementRepository.reconcile()
}
