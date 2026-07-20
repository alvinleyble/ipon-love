package com.iponlove.app.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HeartProgressMathTest {

    @Test
    fun fractionClampsBelowZero() {
        assertThat(HeartProgressMath.fraction(-0.5f)).isEqualTo(0f)
    }

    @Test
    fun fractionClampsAboveOne() {
        assertThat(HeartProgressMath.fraction(1.5f)).isEqualTo(1f)
    }

    @Test
    fun fractionPassesThroughInRange() {
        assertThat(HeartProgressMath.fraction(0.3f)).isEqualTo(0.3f)
    }

    @Test
    fun heartNeverPokesOutLeftAtZero() {
        // centerX = 0 → left would be -7; clamped to 0 so the heart stays inside the track.
        assertThat(HeartProgressMath.heartOffsetPx(0f, trackWidthPx = 100f, heartSizePx = 14f))
            .isEqualTo(0f)
    }

    @Test
    fun heartNeverPokesOutRightAtFull() {
        // centerX = 100 → left would be 93; clamped to trackWidth - heartSize = 86.
        assertThat(HeartProgressMath.heartOffsetPx(1f, trackWidthPx = 100f, heartSizePx = 14f))
            .isEqualTo(86f)
    }

    @Test
    fun heartCentersOnTipMidTrack() {
        // centerX = 50 → left = 43, within [0, 86].
        assertThat(HeartProgressMath.heartOffsetPx(0.5f, trackWidthPx = 100f, heartSizePx = 14f))
            .isEqualTo(43f)
    }

    @Test
    fun degenerateTrackReturnsZero() {
        assertThat(HeartProgressMath.heartOffsetPx(0.5f, trackWidthPx = 10f, heartSizePx = 14f))
            .isEqualTo(0f)
    }
}
