package com.iponlove.app.core.entitlement

import java.time.Instant

/** Whether the entity being gated is owned by one user or the couple (G6 / ADR-0044 §5): a
 *  row attached to a couple-owned entity is [SHARED] (`me.active || partner.active`, D1); a
 *  row attached to a single user, or a feature attached to no entity at all, is [INDIVIDUAL]
 *  (`me.active`). Resolved per call site from the actual row's ownership, not from a static
 *  per-[Feature] default. */
enum class Scope { INDIVIDUAL, SHARED }

/**
 * Resolves whether a gate lets an action through (CONTEXT.md "Effective access") and whether
 * it should actually lock (folding in [Enforcement][enforcementEnabled] — the dormant
 * kill-switch). `shouldLock` is the one gates call: `enforcement_enabled && !hasAccess`.
 */
object EffectiveAccess {

    fun hasAccess(scope: Scope, self: Entitlement, partner: Entitlement?, now: Instant): Boolean {
        val selfActive = self.isActive(now)
        return when (scope) {
            Scope.INDIVIDUAL -> selfActive
            Scope.SHARED -> selfActive || (partner?.isActive(now) ?: false)
        }
    }

    fun shouldLock(
        enforcementEnabled: Boolean,
        scope: Scope,
        self: Entitlement,
        partner: Entitlement?,
        now: Instant,
    ): Boolean = enforcementEnabled && !hasAccess(scope, self, partner, now)
}
