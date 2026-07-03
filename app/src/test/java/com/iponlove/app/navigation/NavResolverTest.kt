package com.iponlove.app.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NavResolverTest {

    @Test
    fun visiblePinIds_returnsKnownPinsInConfigOrderWhenAllAvailable() {
        // When paired, all pins render in config order. When unpaired, the paired-only Couple pin
        // is hidden and its slot is back-filled by the next available registry module (Manage), so
        // the bar always keeps exactly MAX_PINS items.
        val config = NavConfig(listOf("analysis", "records", "couple"))
        assertThat(NavResolver.visiblePinIds(config, isPaired = false))
            .containsExactly("analysis", "records", "manage").inOrder()
        assertThat(NavResolver.visiblePinIds(config, isPaired = true))
            .containsExactly("analysis", "records", "couple").inOrder()
    }

    @Test
    fun visiblePinIds_alwaysReturnsExactlyMaxPins() {
        // Across pairing states and however many pins are hidden, the bar is always full.
        assertThat(NavResolver.visiblePinIds(NavConfig(listOf("analysis", "records", "couple")), false))
            .hasSize(NavRegistry.MAX_PINS)
        assertThat(NavResolver.visiblePinIds(NavConfig(listOf("analysis", "records", "couple")), true))
            .hasSize(NavRegistry.MAX_PINS)
        assertThat(NavResolver.visiblePinIds(NavConfig(NavRegistry.DEFAULT_PINS), false))
            .hasSize(NavRegistry.MAX_PINS)
    }

    @Test
    fun visiblePinIds_backfillsHiddenPairedOnlySlotInPlace() {
        // Couple is paired-only: unpaired back-fills its slot with the next available module
        // (Manage, first in registry order not already pinned); paired shows Couple itself.
        val config = NavConfig(listOf("couple", "records", "analysis"))
        assertThat(NavResolver.visiblePinIds(config, isPaired = false))
            .containsExactly("manage", "records", "analysis").inOrder()
        assertThat(NavResolver.visiblePinIds(config, isPaired = true))
            .containsExactly("couple", "records", "analysis").inOrder()
        // The saved config is never mutated by the substitution.
        assertThat(config.pinnedIds).containsExactly("couple", "records", "analysis").inOrder()
    }

    @Test
    fun visiblePinIds_backfillsToDistinctThirdModuleWhenCoupleAndManageBothPinned() {
        // Regression for the old understudy behavior: with Couple AND Manage both pinned, unpairing
        // must NOT collapse to 2 items — Couple's slot back-fills with a DISTINCT next module
        // (Analysis) so the bar stays at exactly MAX_PINS.
        val config = NavConfig(listOf("manage", "couple", "records"))
        assertThat(NavResolver.visiblePinIds(config, isPaired = false))
            .containsExactly("manage", "analysis", "records").inOrder()
        assertThat(NavResolver.visiblePinIds(config, isPaired = true))
            .containsExactly("manage", "couple", "records").inOrder()
    }

    @Test
    fun visiblePinIds_fillsMultipleMissingSlotsWithoutDuplication() {
        // A short legacy config with a hidden paired-only pin exercises both back-fill paths at
        // once (the vacated Couple slot AND the missing third slot). Fills distinct modules only.
        val config = NavConfig(listOf("couple", "analysis"))
        val result = NavResolver.visiblePinIds(config, isPaired = false)
        assertThat(result).hasSize(NavRegistry.MAX_PINS)
        assertThat(result).containsExactly("records", "analysis", "manage").inOrder()
        assertThat(result).containsNoDuplicates()
    }

    @Test
    fun visiblePinIds_capsAtMaxPins() {
        val config = NavConfig(listOf("records", "analysis", "couple", "manage", "settings"))
        assertThat(NavResolver.visiblePinIds(config, isPaired = true))
            .hasSize(NavRegistry.MAX_PINS)
    }

    @Test
    fun visibleModuleIds_containsNonPairedModulesWhenUnpaired() {
        val all = NavResolver.visibleModuleIds(isPaired = false)
        assertThat(all).containsAtLeast("records", "analysis", "manage", "settings")
        // Couple is paired-only now (ADR-0026) — hidden from the catalog while unpaired.
        assertThat(all).doesNotContain("couple")
        // combined and partner_debt are now internal Couple tabs, not standalone modules.
        assertThat(all).containsNoneOf("combined", "partner_debt")
    }

    @Test
    fun coupleAppearsInCatalogOnlyWhenPaired() {
        // Couple is paired-only (ADR-0026): hidden while unpaired, present once paired.
        assertThat(NavResolver.visibleModuleIds(isPaired = false)).doesNotContain("couple")
        assertThat(NavResolver.visibleModuleIds(isPaired = true)).contains("couple")
    }

    @Test
    fun moreModuleIds_excludesCurrentlyPinnedModules() {
        val config = NavConfig(listOf("records", "analysis", "couple"))
        val more = NavResolver.moreModuleIds(config, isPaired = true)
        assertThat(more).containsNoneOf("records", "analysis", "couple")
    }

    @Test
    fun moreModuleIds_includesModulesNotOnTheBar() {
        // With the default 3-pin bar (records, analysis, couple), Settings isn't pinned nor pulled
        // in as a back-fill, so it stays reachable in the More sheet whether paired or not.
        val config = NavConfig(listOf("records", "analysis", "couple"))
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
        // Couple is paired-only, so it is skipped as home even when pinned first; Records wins.
        val config = NavConfig(listOf("couple", "records"))
        assertThat(NavResolver.startRoute(config)).isEqualTo(NavRegistry.RECORDS.route)
    }

    @Test
    fun startRoute_defaultConfigIsAnalysis() {
        // DEFAULT_PINS is analysis-first (ADR-0026), so a fresh install lands on Analysis.
        assertThat(NavResolver.startRoute(NavConfig())).isEqualTo(NavRegistry.ANALYSIS.route)
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
