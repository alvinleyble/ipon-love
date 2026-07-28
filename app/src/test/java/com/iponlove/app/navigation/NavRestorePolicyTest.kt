package com.iponlove.app.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NavRestorePolicyTest {

    // Restorable == navigable. Calculator is deliberately absent: it is still a registry module,
    // but an overlay one with no graph (ADR-0058).
    private val restorable = setOf("analysis", "records", "settings", "manage")
    private val isKnown: (String) -> Boolean = restorable::contains
    private val window = 5 * 60 * 1000L // 5 minutes
    private val home = "analysis"
    private val now = 1_000_000L

    private fun restore(saved: SavedNavLocation?) =
        NavRestorePolicy.moduleToRestore(saved, home, now, window, isKnown)

    @Test
    fun recentNonHomeModule_restores() {
        val saved = SavedNavLocation("settings", backgroundedAt = now - 10_000L)
        assertThat(restore(saved)).isEqualTo("settings")
    }

    @Test
    fun tooOld_doesNotRestore() {
        val saved = SavedNavLocation("settings", backgroundedAt = now - (window + 1L))
        assertThat(restore(saved)).isNull()
    }

    @Test
    fun noSavedLocation_doesNotRestore() {
        assertThat(restore(null)).isNull()
    }

    @Test
    fun unknownModule_doesNotRestore() {
        // e.g. a module id removed/renamed since it was persisted by an older build.
        val saved = SavedNavLocation("legacy_module", backgroundedAt = now - 10_000L)
        assertThat(restore(saved)).isNull()
    }

    @Test
    fun nonNavigableModule_doesNotRestore() {
        // The ADR-0058 upgrade trap: a device backgrounded on the Calculator *screen* before that
        // release still has "calculator" on disk, and it is still a registry entry — so a
        // membership test would pass it through and hand the NavHost a deleted graph route on the
        // very first launch after updating. Only navigability rules it out.
        val saved = SavedNavLocation("calculator", backgroundedAt = now - 10_000L)
        assertThat(restore(saved)).isNull()
    }

    @Test
    fun homeModule_doesNotRestore() {
        // Cold start already lands on home; restoring it would be a redundant reset-to-root.
        val saved = SavedNavLocation(home, backgroundedAt = now - 10_000L)
        assertThat(restore(saved)).isNull()
    }

    @Test
    fun exactlyAtWindowBoundary_restores() {
        val saved = SavedNavLocation("records", backgroundedAt = now - window)
        assertThat(restore(saved)).isEqualTo("records")
    }

    @Test
    fun negativeDelta_fromRebootClockReset_doesNotRestore() {
        // elapsedRealtime resets on reboot, so a persisted stamp can exceed `now`.
        val saved = SavedNavLocation("settings", backgroundedAt = now + 5_000L)
        assertThat(restore(saved)).isNull()
    }
}
