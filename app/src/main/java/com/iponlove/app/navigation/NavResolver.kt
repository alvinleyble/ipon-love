package com.iponlove.app.navigation

/**
 * Pure resolution of a [NavConfig] into what actually renders. Kept Android-free (operates on
 * ids, not [NavDestination]) so the rules are unit-testable. The UI layer maps the resulting ids
 * back through [NavRegistry.byId].
 *
 * Pairing state plays no part here (2026-07-04 redesign, superseding ADR-0017's hide/back-fill):
 * a pinned module always renders, so the bar, the editor's "On the bar" list, and the saved
 * config are always the same three ids — no more "stored intent" vs "actually rendering" split.
 * Couple handles being unpaired inside its own screen (pairing page) instead of vanishing.
 */
object NavResolver {

    /**
     * The ids that render as bar pins right now — **always exactly [NavRegistry.MAX_PINS]** (floor
     * == ceiling; ADR-0017 addendum 2026-07-03). Normally this is just [NavConfig.pinnedIds];
     * unknown/non-pinnable ids (stale configs referencing removed modules) are dropped and every
     * missing slot — including a legacy config that stored fewer than MAX_PINS ids — is back-filled
     * by the next module in registry order not already chosen.
     */
    fun visiblePinIds(config: NavConfig): List<String> {
        fun isKnown(id: String): Boolean = NavRegistry.byId[id]?.pinnable == true

        val result = config.pinnedIds
            .filter { isKnown(it) }
            .distinct()
            .take(NavRegistry.MAX_PINS)
            .toMutableList()
        // Back-fill pool for short/stale configs: registry order, skipping claimed ids.
        for (dest in NavRegistry.all) {
            if (result.size >= NavRegistry.MAX_PINS) break
            if (dest.pinnable && dest.id !in result) result += dest.id
        }
        return result
    }

    /**
     * Modules to surface in the More sheet: every module that is NOT already on the bar.
     * Excluding the live pins removes the pin/More duplication (ADR-0017) — non-pinnable modules
     * like Settings are never in the pin set, so they always remain here. Couple appears here
     * whenever unpinned, whether paired or not — unpaired it opens its pairing page.
     */
    fun moreModuleIds(config: NavConfig): List<String> {
        val onBar = visiblePinIds(config).toSet()
        return NavRegistry.all.map { it.id }.filter { it !in onBar }
    }

    /** The NavHost start destination route: the first resolved pin, falling back to Records. */
    fun startRoute(config: NavConfig): String =
        visiblePinIds(config)
            .firstOrNull()
            ?.let { NavRegistry.byId[it]?.route }
            ?: NavRegistry.RECORDS.route
}
