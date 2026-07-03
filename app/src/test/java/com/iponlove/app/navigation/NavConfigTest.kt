package com.iponlove.app.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NavConfigTest {

    @Test
    fun replace_swapsPinInPlaceKeepingCount() {
        val config = NavConfig(listOf("records", "analysis", "couple"))
        assertThat(config.replace("analysis", "manage").pinnedIds)
            .containsExactly("records", "manage", "couple").inOrder()
    }

    @Test
    fun replace_isNoOpWhenOldIdNotPinned() {
        val config = NavConfig(listOf("records", "analysis", "couple"))
        assertThat(config.replace("manage", "savings").pinnedIds).isEqualTo(config.pinnedIds)
    }

    @Test
    fun replace_isNoOpWhenNewIdAlreadyPinned() {
        // Swapping in an already-pinned module would shrink the bar / duplicate — rejected.
        val config = NavConfig(listOf("records", "analysis", "couple"))
        assertThat(config.replace("records", "analysis").pinnedIds).isEqualTo(config.pinnedIds)
    }

    @Test
    fun replace_isNoOpWhenNewIdNotPinnable() {
        // Unknown/non-pinnable ids can't be swapped onto the bar.
        val config = NavConfig(listOf("records", "analysis", "couple"))
        assertThat(config.replace("records", "bogus").pinnedIds).isEqualTo(config.pinnedIds)
    }

    @Test
    fun replace_alwaysKeepsExactlyMaxPins() {
        val config = NavConfig(listOf("records", "analysis", "couple"))
        assertThat(config.replace("couple", "savings").pinnedIds).hasSize(NavRegistry.MAX_PINS)
    }

    @Test
    fun move_reordersAndShiftsTheRest() {
        val config = NavConfig(listOf("records", "analysis", "manage", "couple"))
        // move first to last
        assertThat(config.move(0, 3).pinnedIds)
            .containsExactly("analysis", "manage", "couple", "records").inOrder()
        // move last to first
        assertThat(config.move(3, 0).pinnedIds)
            .containsExactly("couple", "records", "analysis", "manage").inOrder()
    }

    @Test
    fun move_isNoOpWhenIndicesEqualOrOutOfRange() {
        val config = NavConfig(listOf("records", "analysis", "manage"))
        assertThat(config.move(1, 1).pinnedIds).isEqualTo(config.pinnedIds)
        assertThat(config.move(5, 0).pinnedIds).isEqualTo(config.pinnedIds)
    }

    @Test
    fun deserialize_dropsUnknownAndDuplicateIdsAndCaps() {
        val config = NavConfig.deserialize("records, bogus,analysis,records, manage ,couple,notes")
        // bogus dropped, duplicate "records" deduped, capped to MAX_PINS (3)
        assertThat(config.pinnedIds)
            .containsExactly("records", "analysis", "manage").inOrder()
    }

    @Test
    fun deserialize_keepsSettingsNowThatItIsPinnable() {
        // Settings is pinnable as of ADR-0026, so a saved "settings" pin survives.
        val config = NavConfig.deserialize("records,settings,analysis")
        assertThat(config.pinnedIds).containsExactly("records", "settings", "analysis").inOrder()
    }

    @Test
    fun deserialize_padsLegacyOneIdConfigUpToMaxPins() {
        // Legacy config predating the fixed-count rule: a single valid id is padded to exactly
        // MAX_PINS using registry order (records, analysis, manage, couple, ...).
        val config = NavConfig.deserialize("records")
        assertThat(config.pinnedIds).hasSize(NavRegistry.MAX_PINS)
        assertThat(config.pinnedIds).containsExactly("records", "analysis", "manage").inOrder()
    }

    @Test
    fun deserialize_padsLegacyTwoIdConfigPreservingIntentOrder() {
        // The stored ids keep their order and position; only the missing slots are filled from the
        // registry-order fallback (independent of pairing state, so paired-only "couple" is kept).
        val config = NavConfig.deserialize("couple,records")
        assertThat(config.pinnedIds).hasSize(NavRegistry.MAX_PINS)
        assertThat(config.pinnedIds).containsExactly("couple", "records", "analysis").inOrder()
    }

    @Test
    fun deserialize_fallsBackToDefaultsWhenEmptyOrAllInvalid() {
        assertThat(NavConfig.deserialize(null).pinnedIds).isEqualTo(NavRegistry.DEFAULT_PINS)
        assertThat(NavConfig.deserialize("").pinnedIds).isEqualTo(NavRegistry.DEFAULT_PINS)
        assertThat(NavConfig.deserialize("nope,bogus").pinnedIds).isEqualTo(NavRegistry.DEFAULT_PINS)
    }

    @Test
    fun serialize_roundTrips() {
        val config = NavConfig(listOf("couple", "records", "manage"))
        assertThat(NavConfig.deserialize(NavConfig.serialize(config)).pinnedIds)
            .isEqualTo(config.pinnedIds)
    }
}
