package com.iponlove.app.navigation

/**
 * Pure decision for whether — and to which module — the nav shell should restore on a cold start
 * (v1.6.6 Item 39). Side-effect-free so the lifecycle wiring stays trivial and the rules are
 * unit-tested ([NavRestorePolicyTest]) rather than the ViewModel.
 */
object NavRestorePolicy {

    /**
     * The module id to restore, or null to leave the NavHost on its configured home tab.
     *
     * Restores iff there is a saved location, it names a module we can still navigate to, it isn't
     * already the home tab (restoring home would be a redundant reset-to-root), and the user is
     * returning within [windowMs] of leaving. A negative delta — the saved stamp is newer than
     * [now], which happens when `elapsedRealtime` resets on a reboot — fails the window and lands
     * on home, the right call for a new boot.
     *
     * @param now a monotonic clock reading (elapsedRealtime), matching [SavedNavLocation.backgroundedAt].
     * @param isRestorableModule guards against any id that is no longer a live NavHost destination:
     *   a stale/renamed id persisted by an older build, **and** an id that is still in the registry
     *   but has lost its graph (an ADR-0058 overlay module — Calculator persisted `"calculator"`
     *   before that release, and mere registry membership would happily feed a deleted route in as
     *   the start destination). The restored id becomes a start destination, so "known" is not a
     *   strong enough test — it has to be *navigable*.
     */
    fun moduleToRestore(
        saved: SavedNavLocation?,
        homeModuleId: String,
        now: Long,
        windowMs: Long,
        isRestorableModule: (String) -> Boolean,
    ): String? {
        if (saved == null) return null
        if (!isRestorableModule(saved.moduleId)) return null
        if (saved.moduleId == homeModuleId) return null
        val elapsed = now - saved.backgroundedAt
        if (elapsed < 0L || elapsed > windowMs) return null
        return saved.moduleId
    }
}
