package com.iponlove.app.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class RelativeTimeTest {

    private val now = Instant.parse("2026-07-14T12:00:00Z")

    @Test
    fun underAMinute_isJustNow() {
        assertThat(relativeTimeLabel(now.minusSeconds(30), now)).isEqualTo("just now")
    }

    @Test
    fun minutes_hours_days() {
        assertThat(relativeTimeLabel(now.minusSeconds(5 * 60), now)).isEqualTo("5m ago")
        assertThat(relativeTimeLabel(now.minusSeconds(3 * 60 * 60), now)).isEqualTo("3h ago")
        assertThat(relativeTimeLabel(now.minusSeconds(2 * 24 * 60 * 60), now)).isEqualTo("2d ago")
    }

    @Test
    fun boundaries_rollToTheCoarserUnit() {
        assertThat(relativeTimeLabel(now.minusSeconds(60), now)).isEqualTo("1m ago")
        assertThat(relativeTimeLabel(now.minusSeconds(60 * 60), now)).isEqualTo("1h ago")
        assertThat(relativeTimeLabel(now.minusSeconds(24 * 60 * 60), now)).isEqualTo("1d ago")
    }
}
