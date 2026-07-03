package com.iponlove.app.navigation

/**
 * Pure resolution of a [NavConfig] + pairing state into what actually renders. Kept Android-free
 * (operates on ids, not [NavDestination]) so the hide/collapse rules are unit-testable. The UI
 * layer maps the resulting ids back through [NavRegistry.byId].
 */
object NavResolver {

    /**
     * The ids that render as bar pins right now — **always exactly [NavRegistry.MAX_PINS]** (floor
     * == ceiling; ADR-0017 addendum 2026-07-03). Two-pass generic resolution, so this one path
     * covers both "a pin is hidden by pairing state" and "a legacy config holds fewer than MAX_PINS
     * ids":
     *
     *  1. Walk [NavConfig.pinnedIds] in order; each pin that is currently available (known,
     *     pinnable, and not paired-only-while-unpaired) keeps its slot.
     *  2. Every remaining slot — a slot vacated by a hidden pin, or a slot missing because the
     *     config was short — is back-filled by the next module in registry order that is available
     *     and not already chosen. In practice Manage back-fills Couple's slot when unpaired.
     *
     * Returns fewer than [NavRegistry.MAX_PINS] only in the degenerate case where the registry
     * can't supply enough available modules (never happens with the real registry) — callers still
     * treat the result as the full pin set.
     */
    fun visiblePinIds(config: NavConfig, isPaired: Boolean): List<String> {
        fun isAvailable(id: String): Boolean {
            val dest = NavRegistry.byId[id] ?: return false
            if (!dest.pinnable) return false
            return dest.id !in NavRegistry.pairedOnlyIds || isPaired
        }

        // Registry-order back-fill pool: available modules not already claimed by a stored pin.
        val backfill = NavRegistry.all
            .map { it.id }
            .filter { isAvailable(it) && it !in config.pinnedIds }
            .toMutableList()
        fun nextBackfill(): String? = if (backfill.isEmpty()) null else backfill.removeAt(0)

        val result = mutableListOf<String>()
        for (id in config.pinnedIds) {
            if (result.size >= NavRegistry.MAX_PINS) break
            if (isAvailable(id) && id !in result) {
                result += id
            } else {
                nextBackfill()?.let { result += it }
            }
        }
        // Config shorter than MAX_PINS (legacy) — keep filling from the registry-order pool.
        while (result.size < NavRegistry.MAX_PINS) {
            result += nextBackfill() ?: break
        }
        return result.take(NavRegistry.MAX_PINS)
    }

    /** Every module reachable right now (for the editor's catalog), paired-only gated. */
    fun visibleModuleIds(isPaired: Boolean): List<String> =
        NavRegistry.all.map { it.id }
            .filter { it !in NavRegistry.pairedOnlyIds || isPaired }

    /**
     * Modules to surface in the More sheet: every reachable module that is NOT already on the
     * bar. Excluding the live pins removes the pin/More duplication (ADR-0017) — non-pinnable
     * modules like Settings are never in the pin set, so they always remain here.
     */
    fun moreModuleIds(config: NavConfig, isPaired: Boolean): List<String> {
        val onBar = visiblePinIds(config, isPaired).toSet()
        return visibleModuleIds(isPaired).filter { it !in onBar }
    }

    /**
     * The NavHost start destination route. Always the first pin that is NOT paired-only (so the
     * graph's start never disappears across pair/unpair), falling back to Records.
     */
    fun startRoute(config: NavConfig): String =
        config.pinnedIds
            .firstOrNull { it !in NavRegistry.pairedOnlyIds }
            ?.let { NavRegistry.byId[it]?.route }
            ?: NavRegistry.RECORDS.route
}
