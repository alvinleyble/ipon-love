package com.iponlove.app.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NavResolverTest {

    @Test
    fun visiblePinIds_keepsPairedOnlyOnlyWhenPaired() {
        val config = NavConfig(listOf("records", "combined", "analysis"))
        assertThat(NavResolver.visiblePinIds(config, isPaired = false))
            .containsExactly("records", "analysis").inOrder()
        assertThat(NavResolver.visiblePinIds(config, isPaired = true))
            .containsExactly("records", "combined", "analysis").inOrder()
    }

    @Test
    fun visiblePinIds_preservesConfigWhenUnpaired() {
        // config still holds the hidden pin, so re-pairing restores the layout
        val config = NavConfig(listOf("combined", "records"))
        assertThat(NavResolver.visiblePinIds(config, isPaired = false)).containsExactly("records")
        assertThat(config.pinnedIds).contains("combined")
    }

    @Test
    fun visiblePinIds_canBeEmptyWhenEveryPinIsPairedOnlyAndUnpaired() {
        val config = NavConfig(listOf("combined", "partner_debt"))
        assertThat(NavResolver.visiblePinIds(config, isPaired = false)).isEmpty()
    }

    @Test
    fun visibleModuleIds_gatesPairedOnlyModules() {
        assertThat(NavResolver.visibleModuleIds(isPaired = false))
            .containsNoneOf("combined", "partner_debt")
        assertThat(NavResolver.visibleModuleIds(isPaired = true))
            .containsAtLeast("combined", "partner_debt")
    }

    @Test
    fun coupleIsAlwaysVisible_evenUnpaired() {
        // Couple doubles as the pairing entry point, so it is not paired-only.
        assertThat(NavResolver.visibleModuleIds(isPaired = false)).contains("couple")
    }

    @Test
    fun startRoute_isFirstNonPairedOnlyPin() {
        val config = NavConfig(listOf("couple", "records"))
        assertThat(NavResolver.startRoute(config)).isEqualTo(NavRegistry.COUPLE.route)
    }

    @Test
    fun startRoute_skipsPairedOnlyFirstPinSoHomeSurvivesUnpair() {
        val config = NavConfig(listOf("combined", "budgets"))
        assertThat(NavResolver.startRoute(config)).isEqualTo(NavRegistry.BUDGETS.route)
    }

    @Test
    fun startRoute_fallsBackToRecordsWhenAllPinsPairedOnly() {
        val config = NavConfig(listOf("combined", "partner_debt"))
        assertThat(NavResolver.startRoute(config)).isEqualTo(NavRegistry.RECORDS.route)
    }
}
