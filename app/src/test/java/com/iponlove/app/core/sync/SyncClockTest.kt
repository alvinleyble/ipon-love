package com.iponlove.app.core.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

/** ADR-0001: offset-corrected, locally monotonic `updated_at`. */
class SyncClockTest {

    @Test
    fun stamp_appliesClockOffsetTowardServerTime() {
        // Device clock is 5s behind server; offset (server - local) = +5000ms.
        val clock = SyncClock(now = { at(1_000_000) }, initialOffsetMillis = 5_000)

        assertThat(clock.stamp()).isEqualTo(at(1_005_000))
    }

    @Test
    fun stamp_brandNewRow_usesCorrectedNow() {
        val clock = SyncClock(now = { at(2_000) }, initialOffsetMillis = 0)

        assertThat(clock.stamp(previousUpdatedAt = null)).isEqualTo(at(2_000))
    }

    @Test
    fun stamp_isMonotonic_whenPreviousIsAheadOfCorrectedNow() {
        // now < previous: a backward clock jump must not produce a non-increasing stamp.
        val clock = SyncClock(now = { at(1_000) }, initialOffsetMillis = 0)

        val stamped = clock.stamp(previousUpdatedAt = at(5_000))

        // Floored to previous + 1ms, never below the row's own prior version.
        assertThat(stamped).isEqualTo(at(5_001))
    }

    @Test
    fun stamp_usesCorrectedNow_whenAheadOfPrevious() {
        val clock = SyncClock(now = { at(10_000) }, initialOffsetMillis = 0)

        assertThat(clock.stamp(previousUpdatedAt = at(5_000))).isEqualTo(at(10_000))
    }

    @Test
    fun stamp_equalNowAndPrevious_stepsForwardOneMilli() {
        val clock = SyncClock(now = { at(7_000) }, initialOffsetMillis = 0)

        assertThat(clock.stamp(previousUpdatedAt = at(7_000))).isEqualTo(at(7_001))
    }

    @Test
    fun recordServerTime_recomputesOffsetFromObservedSkew() {
        var device = Instant.ofEpochMilli(1_000)
        val clock = SyncClock(now = { device })

        // Server is 3s ahead of the device at the moment of observation.
        clock.recordServerTime(serverNow = at(4_000), localNow = at(1_000))
        assertThat(clock.offsetMillis).isEqualTo(3_000)

        device = Instant.ofEpochMilli(1_000)
        assertThat(clock.stamp()).isEqualTo(at(4_000))
    }
}
