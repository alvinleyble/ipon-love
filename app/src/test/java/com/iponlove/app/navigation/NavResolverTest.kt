package com.iponlove.app.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NavResolverTest {

    @Test
    fun visiblePinIds_returnsPinsInConfigOrderRegardlessOfPairing() {
        // 2026-07-04 redesign: pairing state no longer affects the bar — a pinned Couple always
        // renders (its screen shows the pairing page when unpaired).
        val config = NavConfig(listOf("analysis", "records", "couple"))
        assertThat(NavResolver.visiblePinIds(config))
            .containsExactly("analysis", "records", "couple").inOrder()
    }

    @Test
    fun visiblePinIds_alwaysReturnsExactlyMaxPins() {
        assertThat(NavResolver.visiblePinIds(NavConfig(listOf("analysis", "records", "couple"))))
            .hasSize(NavRegistry.MAX_PINS)
        assertThat(NavResolver.visiblePinIds(NavConfig(NavRegistry.DEFAULT_PINS)))
            .hasSize(NavRegistry.MAX_PINS)
    }

    @Test
    fun visiblePinIds_dropsUnknownIdsAndBackfillsFromRegistryOrder() {
        // Stale ids referencing removed modules are dropped; the freed slots are back-filled by
        // registry order (records, analysis, manage, ...) without duplicating live pins.
        val config = NavConfig(listOf("combined", "analysis"))
        val result = NavResolver.visiblePinIds(config)
        assertThat(result).hasSize(NavRegistry.MAX_PINS)
        assertThat(result).containsExactly("analysis", "records", "manage").inOrder()
        assertThat(result).containsNoDuplicates()
    }

    @Test
    fun visiblePinIds_padsShortLegacyConfig() {
        val config = NavConfig(listOf("couple", "analysis"))
        val result = NavResolver.visiblePinIds(config)
        assertThat(result).hasSize(NavRegistry.MAX_PINS)
        assertThat(result).containsExactly("couple", "analysis", "records").inOrder()
    }

    @Test
    fun visiblePinIds_capsAtMaxPins() {
        val config = NavConfig(listOf("records", "analysis", "couple", "manage", "settings"))
        assertThat(NavResolver.visiblePinIds(config)).hasSize(NavRegistry.MAX_PINS)
    }

    @Test
    fun moreModuleIds_excludesCurrentlyPinnedModules() {
        val config = NavConfig(listOf("records", "analysis", "couple"))
        assertThat(NavResolver.moreModuleIds(config))
            .containsNoneOf("records", "analysis", "couple")
    }

    @Test
    fun moreModuleIds_includesCoupleWheneverUnpinned() {
        // Couple must never be unreachable: unpinned, it lives in More (opening its pairing page
        // when unpaired) — this is the fix for the module vanishing entirely.
        val config = NavConfig(listOf("records", "analysis", "manage"))
        assertThat(NavResolver.moreModuleIds(config)).contains("couple")
    }

    @Test
    fun moreModuleIds_includesModulesNotOnTheBar() {
        val config = NavConfig(listOf("records", "analysis", "couple"))
        assertThat(NavResolver.moreModuleIds(config)).contains("settings")
    }

    @Test
    fun moreModuleIds_doesNotContainFormerStandaloneModules() {
        // combined and partner_debt were removed from NavRegistry.all in V1.4 —
        // they live as tabs inside Couple now and must not appear in More.
        val config = NavConfig(listOf("records"))
        assertThat(NavResolver.moreModuleIds(config)).containsNoneOf("combined", "partner_debt")
    }

    @Test
    fun startRoute_isFirstPinEvenWhenCouple() {
        // Couple is a normal module now — pinned first, it is home.
        val config = NavConfig(listOf("couple", "records", "analysis"))
        assertThat(NavResolver.startRoute(config)).isEqualTo(NavRegistry.COUPLE.route)
    }

    @Test
    fun startRoute_defaultConfigIsAnalysis() {
        // DEFAULT_PINS is analysis-first (ADR-0026), so a fresh install lands on Analysis.
        assertThat(NavResolver.startRoute(NavConfig())).isEqualTo(NavRegistry.ANALYSIS.route)
    }

    @Test
    fun startRoute_skipsUnknownFirstPin() {
        // A stale first id is dropped by resolution, so home is the first *valid* pin.
        val config = NavConfig(listOf("combined", "manage"))
        assertThat(NavResolver.startRoute(config)).isEqualTo(NavRegistry.MANAGE.route)
    }

    @Test
    fun startRoute_skipsANonNavigableFirstPin() {
        // Calculator is pinnable, so it can legally hold the first slot — but it is an overlay
        // module with no graph (ADR-0058). Handing its route to the NavHost as a start destination
        // would crash on launch, every launch, for as long as it sat there.
        val config = NavConfig(listOf("calculator", "records", "manage"))
        assertThat(NavResolver.startRoute(config)).isEqualTo(NavRegistry.RECORDS.route)
    }

    @Test
    fun startRoute_stillPinsANonNavigableModuleToTheBar() {
        // ...and it keeps its pin while doing so: only *home* skips it, the bar does not.
        val config = NavConfig(listOf("calculator", "records", "manage"))
        assertThat(NavResolver.visiblePinIds(config)).containsExactly("calculator", "records", "manage").inOrder()
    }
}
