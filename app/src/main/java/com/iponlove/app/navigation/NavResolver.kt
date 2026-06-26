package com.iponlove.app.navigation

/**
 * Pure resolution of a [NavConfig] + pairing state into what actually renders. Kept Android-free
 * (operates on ids, not [NavDestination]) so the hide/collapse rules are unit-testable. The UI
 * layer maps the resulting ids back through [NavRegistry.byId].
 */
object NavResolver {

    /**
     * Pinned ids that should appear in the bar right now: config order, paired-only entries
     * dropped while unpaired, capped at [NavRegistry.MAX_PINS]. May be empty if every pin is a
     * paired-only destination and the user is unpaired — callers supply a fallback.
     */
    fun visiblePinIds(config: NavConfig, isPaired: Boolean): List<String> =
        config.pinnedIds
            .filter { it !in NavRegistry.pairedOnlyIds || isPaired }
            .take(NavRegistry.MAX_PINS)

    /** Every module reachable right now (for the More grid / editor), paired-only gated. */
    fun visibleModuleIds(isPaired: Boolean): List<String> =
        NavRegistry.all.map { it.id }
            .filter { it !in NavRegistry.pairedOnlyIds || isPaired }

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
