package com.iponlove.app.navigation

/**
 * The user's pinned bottom-bar layout. Pure value type with pure mutations so the pin/unpin/
 * reorder rules are JVM-testable without DataStore or Compose. Invariants the mutations keep:
 *  - never more than [NavRegistry.MAX_PINS] pins
 *  - never fewer than 1 pin (the bar must always have a real destination)
 *  - no duplicates
 * Paired-only filtering is NOT done here — config is layout intent and survives unpair; the
 * runtime hide happens in [NavResolver].
 */
data class NavConfig(
    val pinnedIds: List<String> = NavRegistry.DEFAULT_PINS,
) {
    /** Append [id] as a new pin if there's room and it isn't already pinned. */
    fun pin(id: String): NavConfig =
        if (id in pinnedIds || pinnedIds.size >= NavRegistry.MAX_PINS) this
        else copy(pinnedIds = pinnedIds + id)

    /** Remove [id] unless it's the last remaining pin (min 1). */
    fun unpin(id: String): NavConfig =
        if (id !in pinnedIds || pinnedIds.size <= 1) this
        else copy(pinnedIds = pinnedIds - id)

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
        /** Parse the comma-joined DataStore form, dropping unknown/duplicate ids. */
        fun deserialize(raw: String?): NavConfig {
            val ids = raw
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() && it in NavRegistry.byId }
                ?.distinct()
                ?.take(NavRegistry.MAX_PINS)
                .orEmpty()
            return if (ids.isEmpty()) NavConfig() else NavConfig(ids)
        }

        fun serialize(config: NavConfig): String = config.pinnedIds.joinToString(",")
    }
}
