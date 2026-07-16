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
     * Restores iff there is a saved location, it names a module we still know, it isn't already the
     * home tab (restoring home would be a redundant reset-to-root), and the user is returning
     * within [windowMs] of leaving. A negative delta — the saved stamp is newer than [now], which
     * happens when `elapsedRealtime` resets on a reboot — fails the window and lands on home, the
     * right call for a new boot.
     *
     * @param now a monotonic clock reading (elapsedRealtime), matching [SavedNavLocation.backgroundedAt].
     * @param isKnownModule guards against a stale/renamed id persisted by an older build.
     */
    fun moduleToRestore(
        saved: SavedNavLocation?,
        homeModuleId: String,
        now: Long,
        windowMs: Long,
        isKnownModule: (String) -> Boolean,
    ): String? {
        if (saved == null) return null
        if (!isKnownModule(saved.moduleId)) return null
        if (saved.moduleId == homeModuleId) return null
        val elapsed = now - saved.backgroundedAt
        if (elapsed < 0L || elapsed > windowMs) return null
        return saved.moduleId
    }
}
