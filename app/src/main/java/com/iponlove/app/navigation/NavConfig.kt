package com.iponlove.app.navigation

/**
 * The user's pinned bottom-bar layout. Pure value type with pure mutations so the replace/
 * reorder rules are JVM-testable without DataStore or Compose. Invariant the mutations keep:
 *  - always exactly [NavRegistry.MAX_PINS] distinct pinnable ids (floor == ceiling — ADR-0017
 *    addendum 2026-07-03; users may only *replace* a pin, never remove down to fewer)
 * Paired-only filtering is NOT done here — config is layout intent and survives unpair; the
 * runtime hide/back-fill happens in [NavResolver].
 */
data class NavConfig(
    val pinnedIds: List<String> = NavRegistry.DEFAULT_PINS,
) {
    /**
     * Swap the pin [oldId] out for [newId] in place, keeping the pin count fixed. No-op if [oldId]
     * isn't pinned, if [newId] is already pinned (would shrink/duplicate), or if [newId] isn't a
     * pinnable module. This is the only way to change *which* modules are pinned — there is no bare
     * add/remove any more (ADR-0017 addendum).
     */
    fun replace(oldId: String, newId: String): NavConfig {
        val index = pinnedIds.indexOf(oldId)
        if (index < 0) return this
        if (newId in pinnedIds) return this
        if (NavRegistry.byId[newId]?.pinnable != true) return this
        val mutable = pinnedIds.toMutableList()
        mutable[index] = newId
        return copy(pinnedIds = mutable)
    }

    /** Move the pin at [from] to index [to], shifting the rest. No-op on out-of-range. */
    fun move(from: Int, to: Int): NavConfig {
        if (from !in pinnedIds.indices) return this
        val clampedTo = to.coerceIn(0, pinnedIds.lastIndex)
        if (from == clampedTo) return this
        val mutable = pinnedIds.toMutableList()
        mutable.add(clampedTo, mutable.removeAt(from))
        return copy(pinnedIds = mutable)
    }

    fun isPinned(id: String): Boolean = id in pinnedIds

    companion object {
        /**
         * Parse the comma-joined DataStore form, dropping unknown/duplicate/non-pinnable ids, then
         * self-heal up to exactly [NavRegistry.MAX_PINS] pins. Legacy configs that predate the
         * fixed-count rule (Alvin/Patty are active beta users) may hold fewer than [MAX_PINS] valid
         * ids — those are padded via [padToMaxPins] so the invariant always holds on load.
         */
        fun deserialize(raw: String?): NavConfig {
            val parsed = raw
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() && NavRegistry.byId[it]?.pinnable == true }
                ?.distinct()
                ?.take(NavRegistry.MAX_PINS)
                .orEmpty()
            return if (parsed.isEmpty()) NavConfig() else NavConfig(padToMaxPins(parsed))
        }

        fun serialize(config: NavConfig): String = config.pinnedIds.joinToString(",")

        /**
         * Pad [ids] up to exactly [NavRegistry.MAX_PINS] using registry-order pinnable fallbacks,
         * independent of pairing state — config stores layout *intent*, which can already include a
         * paired-only id while unpaired (ADR-0017). Truncates if given more than [MAX_PINS].
         */
        private fun padToMaxPins(ids: List<String>): List<String> {
            if (ids.size >= NavRegistry.MAX_PINS) return ids.take(NavRegistry.MAX_PINS)
            val result = ids.toMutableList()
            for (dest in NavRegistry.all) {
                if (result.size >= NavRegistry.MAX_PINS) break
                if (dest.pinnable && dest.id !in result) result += dest.id
            }
            return result
        }
    }
}
