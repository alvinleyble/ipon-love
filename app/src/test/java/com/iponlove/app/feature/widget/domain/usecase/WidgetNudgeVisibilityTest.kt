package com.iponlove.app.feature.widget.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WidgetNudgeVisibilityTest {

    private val now = 1_700_000_000_000L
    private val dayMillis = 24L * 60 * 60 * 1000

    @Test
    fun adopted_neverShows_evenNeverShownBefore() {
        assertThat(WidgetNudgeVisibility.shouldShow(adopted = true, lastShownAtMillis = null, nowMillis = now))
            .isFalse()
    }

    @Test
    fun notAdopted_neverShownBefore_shows() {
        assertThat(WidgetNudgeVisibility.shouldShow(adopted = false, lastShownAtMillis = null, nowMillis = now))
            .isTrue()
    }

    @Test
    fun notAdopted_shownYesterday_staysSuppressed() {
        val lastShown = now - dayMillis
        assertThat(WidgetNudgeVisibility.shouldShow(adopted = false, lastShownAtMillis = lastShown, nowMillis = now))
            .isFalse()
    }

    @Test
    fun notAdopted_shownExactly29DaysAgo_staysSuppressed() {
        val lastShown = now - 29 * dayMillis
        assertThat(WidgetNudgeVisibility.shouldShow(adopted = false, lastShownAtMillis = lastShown, nowMillis = now))
            .isFalse()
    }

    @Test
    fun notAdopted_shownExactly30DaysAgo_resurfaces() {
        val lastShown = now - 30 * dayMillis
        assertThat(WidgetNudgeVisibility.shouldShow(adopted = false, lastShownAtMillis = lastShown, nowMillis = now))
            .isTrue()
    }

    @Test
    fun notAdopted_shown31DaysAgo_resurfaces() {
        val lastShown = now - 31 * dayMillis
        assertThat(WidgetNudgeVisibility.shouldShow(adopted = false, lastShownAtMillis = lastShown, nowMillis = now))
            .isTrue()
    }
}
