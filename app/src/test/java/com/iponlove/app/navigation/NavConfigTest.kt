package com.iponlove.app.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NavConfigTest {

    @Test
    fun pin_appendsWhenRoomAndNotPresent() {
        val config = NavConfig(listOf("records", "analysis"))
        assertThat(config.pin("manage").pinnedIds)
            .containsExactly("records", "analysis", "manage").inOrder()
    }

    @Test
    fun pin_isNoOpWhenAlreadyPinned() {
        val config = NavConfig(listOf("records", "analysis"))
        assertThat(config.pin("records").pinnedIds).isEqualTo(config.pinnedIds)
    }

    @Test
    fun pin_isNoOpWhenAtMax() {
        val config = NavConfig(listOf("records", "analysis", "manage"))
        assertThat(config.pin("notes").pinnedIds).hasSize(NavRegistry.MAX_PINS)
        assertThat(config.pin("notes").pinnedIds).doesNotContain("notes")
    }

    @Test
    fun pin_allowsSettingsNowThatItIsPinnable() {
        // Settings flipped to pinnable in ADR-0026, so it can now sit on the bar.
        val config = NavConfig(listOf("records"))
        assertThat(config.pin("settings").pinnedIds)
            .containsExactly("records", "settings").inOrder()
    }

    @Test
    fun unpin_removesWhenMoreThanOne() {
        val config = NavConfig(listOf("records", "analysis"))
        assertThat(config.unpin("records").pinnedIds).containsExactly("analysis")
    }

    @Test
    fun unpin_isNoOpOnLastPin() {
        val config = NavConfig(listOf("records"))
        assertThat(config.unpin("records").pinnedIds).containsExactly("records")
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
