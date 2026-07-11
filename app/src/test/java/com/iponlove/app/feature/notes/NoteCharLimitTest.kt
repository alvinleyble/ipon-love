package com.iponlove.app.feature.notes

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.notes.domain.usecase.NoteCharLimit
import org.junit.Test

class NoteCharLimitTest {

    private val limit = 5_000

    @Test
    fun overflow_isZero_whenWithinOrAtLimit() {
        assertThat(NoteCharLimit.overflow(0, limit)).isEqualTo(0)
        assertThat(NoteCharLimit.overflow(4_999, limit)).isEqualTo(0)
        assertThat(NoteCharLimit.overflow(5_000, limit)).isEqualTo(0)
    }

    @Test
    fun overflow_isExcess_whenOverLimit() {
        assertThat(NoteCharLimit.overflow(5_001, limit)).isEqualTo(1)
        // A big paste past the cap reports the whole excess so it can be trimmed to fit.
        assertThat(NoteCharLimit.overflow(9_500, limit)).isEqualTo(4_500)
    }

    @Test
    fun isOver_flipsStrictlyAboveLimit() {
        assertThat(NoteCharLimit.isOver(5_000, limit)).isFalse()
        assertThat(NoteCharLimit.isOver(5_001, limit)).isTrue()
    }

    @Test
    fun shouldShowCounter_appearsOnlyNearTheCap() {
        val threshold = limit - NoteCharLimit.COUNTER_THRESHOLD // 4_500
        assertThat(NoteCharLimit.shouldShowCounter(threshold - 1, limit)).isFalse()
        assertThat(NoteCharLimit.shouldShowCounter(threshold, limit)).isTrue()
        assertThat(NoteCharLimit.shouldShowCounter(limit + 100, limit)).isTrue()
    }

    @Test
    fun defaultMax_isThePremiumCeiling_soBaseBehaviourShipsDormant() {
        // Base ceiling = premium max; a normal note never approaches it (S10 lowers the free tier).
        assertThat(NoteCharLimit.DEFAULT_MAX).isEqualTo(50_000)
        assertThat(NoteCharLimit.shouldShowCounter(2_000, NoteCharLimit.DEFAULT_MAX)).isFalse()
    }

    @Test
    fun effectiveLimit_isTheTierCap_whenTheNoteIsWithinIt() {
        // A short note on the free tier is simply capped at the tier limit.
        assertThat(NoteCharLimit.effectiveLimit(tierLimit = 5_000, existingLength = 0)).isEqualTo(5_000)
        assertThat(NoteCharLimit.effectiveLimit(tierLimit = 5_000, existingLength = 3_000)).isEqualTo(5_000)
        assertThat(NoteCharLimit.effectiveLimit(tierLimit = 5_000, existingLength = 5_000)).isEqualTo(5_000)
    }

    @Test
    fun effectiveLimit_freezesAnOverCapNote_neverTruncating() {
        // T1 freeze (§10.7): a 40k note authored while premium, opened on the free tier after a
        // flip, is frozen at 40k — the ceiling never drops below the length already on disk, so no
        // overflow is computed and no content is stripped on open.
        val frozen = NoteCharLimit.effectiveLimit(tierLimit = 5_000, existingLength = 40_000)
        assertThat(frozen).isEqualTo(40_000)
        assertThat(NoteCharLimit.overflow(40_000, frozen)).isEqualTo(0)
        // ...but it still can't grow past the frozen length.
        assertThat(NoteCharLimit.overflow(40_001, frozen)).isEqualTo(1)
    }
}
