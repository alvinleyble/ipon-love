package com.iponlove.app.core.entitlement

import com.iponlove.app.core.config.AppConfigRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single seam every Phase 2 count-cap gate consults (S7). It folds the three inputs a gate
 * needs — [Enforcement][AppConfigRepository] (the dormant kill-switch), the self/partner
 * [Entitlement][EntitlementRepository], and the resolved [PlanLimits] (with any remote
 * `cap_overrides`) — into one create-time decision, so a feature's ViewModel only has to supply
 * *which* cap (the [Scope], the current row count, and the field selector) and react to the result.
 *
 * Everything stays dormant until `enforcement_enabled` flips ON: [checkCap] short-circuits to
 * [CapCheck.Allowed] the moment [EffectiveAccess.shouldLock] is false, so with enforcement off (or
 * with premium access) it never even reads the limits. Reads are all local + instant (Room-backed
 * caches); the gate never touches the network.
 */
@Singleton
class PremiumGate @Inject constructor(
    private val entitlement: EntitlementRepository,
    private val appConfig: AppConfigRepository,
) {
    /**
     * Whether creating one more of a count-capped entity is blocked right now.
     *
     * @param scope INDIVIDUAL (gated on the signed-in user alone) or SHARED (either partner's
     *   premium unlocks it, D1) — resolved by the caller from the *row's* ownership, per G6.
     * @param currentCount the G1 count of existing rows for this cap (all non-deleted rows,
     *   archived included; un-settled only for partner debts) — a per-entity DAO query the caller
     *   owns, never this gate.
     * @param limitOf picks the matching cap field off a [PlanLimits] instance
     *   (e.g. `PlanLimits::maxPersonalAccounts`).
     */
    suspend fun checkCap(
        scope: Scope,
        currentCount: Int,
        limitOf: (PlanLimits) -> Int,
    ): CapCheck {
        val config = appConfig.observe().first()
        val self = entitlement.observeSelf().first()
        val partner = entitlement.observePartner().first()
        val now = Instant.now()

        // Dormant / has-access fast path: no lock ⇒ create always allowed, limits never read.
        if (!EffectiveAccess.shouldLock(config.enforcementEnabled, scope, self, partner, now)) {
            return CapCheck.Allowed
        }

        // Locked ⇒ hasAccess is false, so the FREE tier governs; premium max is only for the copy.
        val freeLimit = limitOf(PlanLimits.resolve(hasAccess = false, config.capOverridesJson))
        return if (CapCount.blocksCreate(currentCount, freeLimit)) {
            CapCheck.Blocked(
                freeLimit = freeLimit,
                premiumMax = limitOf(PlanLimits.resolve(hasAccess = true, config.capOverridesJson)),
            )
        } else {
            CapCheck.Allowed
        }
    }
}
