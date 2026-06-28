package com.iponlove.app.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NavResolverTest {

    @Test
    fun visiblePinIds_returnsKnownPinsInConfigOrder() {
        // All current modules are returned in the order stored in config.
        val config = NavConfig(listOf("records", "analysis", "couple"))
        assertThat(NavResolver.visiblePinIds(config, isPaired = false))
            .containsExactly("records", "analysis", "couple").inOrder()
        assertThat(NavResolver.visiblePinIds(config, isPaired = true))
            .containsExactly("records", "analysis", "couple").inOrder()
    }

    @Test
    fun visiblePinIds_preservesConfigIds() {
        // Stored pin ids survive the resolver (UI's mapNotNull later drops any unknown ids).
        val config = NavConfig(listOf("couple", "records"))
        assertThat(NavResolver.visiblePinIds(config, isPaired = false))
            .containsExactly("couple", "records").inOrder()
        assertThat(config.pinnedIds).containsExactly("couple", "records").inOrder()
    }

    @Test
    fun visiblePinIds_capsAtMaxPins() {
        val config = NavConfig(listOf("records", "analysis", "couple", "manage", "notes"))
        assertThat(NavResolver.visiblePinIds(config, isPaired = true))
            .hasSize(NavRegistry.MAX_PINS)
    }

    @Test
    fun visibleModuleIds_containsAllCurrentModules() {
        // No paired-only modules exist in the current registry; all are reachable regardless.
        val all = NavResolver.visibleModuleIds(isPaired = false)
        assertThat(all).containsAtLeast("records", "analysis", "manage", "couple", "settings")
        // combined and partner_debt are now internal Couple tabs, not standalone modules.
        assertThat(all).containsNoneOf("combined", "partner_debt")
    }

    @Test
    fun coupleIsAlwaysVisible_evenUnpaired() {
        // Couple doubles as the pairing entry point, so it is not paired-only.
        assertThat(NavResolver.visibleModuleIds(isPaired = false)).contains("couple")
    }

    @Test
    fun moreModuleIds_excludesCurrentlyPinnedModules() {
        val config = NavConfig(listOf("records", "analysis", "couple"))
        val more = NavResolver.moreModuleIds(config, isPaired = true)
        assertThat(more).containsNoneOf("records", "analysis", "couple")
    }

    @Test
    fun moreModuleIds_alwaysIncludesNonPinnableSettings() {
        // Settings can't be pinned, so it must always stay reachable in the More sheet.
        val config = NavConfig(listOf("records", "analysis", "couple", "manage"))
        assertThat(NavResolver.moreModuleIds(config, isPaired = true)).contains("settings")
        assertThat(NavResolver.moreModuleIds(config, isPaired = false)).contains("settings")
    }

    @Test
    fun moreModuleIds_doesNotContainFormerStandaloneModules() {
        // combined and partner_debt were removed from NavRegistry.all in V1.4 —
        // they live as tabs inside Couple now and must not appear in More.
        val config = NavConfig(listOf("records"))
        assertThat(NavResolver.moreModuleIds(config, isPaired = false))
            .containsNoneOf("combined", "partner_debt")
        assertThat(NavResolver.moreModuleIds(config, isPaired = true))
            .containsNoneOf("combined", "partner_debt")
    }

    @Test
    fun startRoute_isFirstNonPairedOnlyPin() {
        val config = NavConfig(listOf("couple", "records"))
        assertThat(NavResolver.startRoute(config)).isEqualTo(NavRegistry.COUPLE.route)
    }

    @Test
    fun startRoute_fallsBackToRecordsWhenFirstPinIsUnknown() {
        // startRoute tries only the first non-paired-only id; if that id is not in
        // NavRegistry.byId (stale or removed module), it falls back to Records.
        val config = NavConfig(listOf("combined", "manage"))
        assertThat(NavResolver.startRoute(config)).isEqualTo(NavRegistry.RECORDS.route)
    }

    @Test
    fun startRoute_fallsBackToRecordsWhenAllPinsUnknown() {
        val config = NavConfig(listOf("combined", "partner_debt"))
        assertThat(NavResolver.startRoute(config)).isEqualTo(NavRegistry.RECORDS.route)
    }
}
