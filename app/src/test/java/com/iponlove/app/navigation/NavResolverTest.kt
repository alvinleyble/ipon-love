package com.iponlove.app.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NavResolverTest {

    @Test
    fun visiblePinIds_returnsKnownPinsInConfigOrder() {
        // When paired, all pins render in config order. When unpaired, the paired-only Couple pin
        // is replaced in place by its understudy (Manage) so the bar keeps its item count.
        val config = NavConfig(listOf("analysis", "records", "couple"))
        assertThat(NavResolver.visiblePinIds(config, isPaired = false))
            .containsExactly("analysis", "records", "manage").inOrder()
        assertThat(NavResolver.visiblePinIds(config, isPaired = true))
            .containsExactly("analysis", "records", "couple").inOrder()
    }

    @Test
    fun visiblePinIds_promotesUnderstudyInPlaceForHiddenPairedOnlyPin() {
        // Couple is paired-only with Manage as understudy: unpaired shows Manage in Couple's slot,
        // paired shows Couple. The understudy sits exactly where the hidden pin was.
        val config = NavConfig(listOf("couple", "records"))
        assertThat(NavResolver.visiblePinIds(config, isPaired = false))
            .containsExactly("manage", "records").inOrder()
        assertThat(NavResolver.visiblePinIds(config, isPaired = true))
            .containsExactly("couple", "records").inOrder()
        // The saved config is never mutated by the substitution.
        assertThat(config.pinnedIds).containsExactly("couple", "records").inOrder()
    }

    @Test
    fun visiblePinIds_doesNotDuplicateUnderstudyAlreadyPinned() {
        // If Manage is already pinned, hiding Couple must not add a second Manage — the slot just
        // collapses instead.
        val config = NavConfig(listOf("manage", "couple", "records"))
        assertThat(NavResolver.visiblePinIds(config, isPaired = false))
            .containsExactly("manage", "records").inOrder()
        assertThat(NavResolver.visiblePinIds(config, isPaired = true))
            .containsExactly("manage", "couple", "records").inOrder()
    }

    @Test
    fun visiblePinIds_capsAtMaxPins() {
        val config = NavConfig(listOf("records", "analysis", "couple", "manage", "notes"))
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
