package com.iponlove.app.feature.calculator

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.calculator.domain.BubblePlacement
import com.iponlove.app.feature.calculator.domain.BubbleSlot
import org.junit.Test

/**
 * The clamp-and-snap rules for the calculator bubble's collapsed pill (ADR-0058 decision 8).
 * Pure, so the "can the pill end up under the bottom bar / off the top of the screen" question is
 * answered here rather than only by dragging on a device.
 */
class BubblePlacementTest {

    private val width = 1080f
    private val track = 1600f

    @Test
    fun releasedOnRightHalf_snapsToRightEdge() {
        assertThat(BubblePlacement.snap(centerX = 900f, y = 400f, trackWidth = width, trackHeight = track).onRightEdge)
            .isTrue()
    }

    @Test
    fun releasedOnLeftHalf_snapsToLeftEdge() {
        assertThat(BubblePlacement.snap(centerX = 180f, y = 400f, trackWidth = width, trackHeight = track).onRightEdge)
            .isFalse()
    }

    @Test
    fun exactMidpoint_snapsRight() {
        // Documented tie-break: the midpoint goes right, matching the spawn side.
        assertThat(BubblePlacement.snap(centerX = width / 2f, y = 0f, trackWidth = width, trackHeight = track).onRightEdge)
            .isTrue()
    }

    @Test
    fun draggedAboveTheTrack_clampsToTop() {
        // Negative y = dragged up into the status bar.
        val slot = BubblePlacement.snap(centerX = 900f, y = -500f, trackWidth = width, trackHeight = track)
        assertThat(slot.yFraction).isEqualTo(0f)
    }

    @Test
    fun draggedBelowTheTrack_clampsToBottom() {
        // Past the end of the track = dragged down onto the bottom bar.
        val slot = BubblePlacement.snap(centerX = 900f, y = track + 900f, trackWidth = width, trackHeight = track)
        assertThat(slot.yFraction).isEqualTo(1f)
    }

    @Test
    fun withinTrack_keepsProportionalHeight() {
        val slot = BubblePlacement.snap(centerX = 900f, y = track / 4f, trackWidth = width, trackHeight = track)
        assertThat(slot.yFraction).isWithin(0.001f).of(0.25f)
    }

    @Test
    fun unmeasuredFrame_isSafe() {
        // A zero-size container (first frame) must not divide by zero or produce a NaN offset.
        val slot = BubblePlacement.snap(centerX = 0f, y = 0f, trackWidth = 0f, trackHeight = 0f)
        assertThat(slot.onRightEdge).isTrue()
        assertThat(slot.yFraction).isEqualTo(0f)
    }

    @Test
    fun yPx_staysInsideTheTrack() {
        // The stored fraction is the same in portrait and landscape; the pixels it resolves to must
        // stay on screen in both, which is the whole reason the slot is a fraction (decision: the
        // pill's position is stored as a fraction of screen bounds, not pixels).
        val slot = BubbleSlot(onRightEdge = true, yFraction = 1f)
        assertThat(BubblePlacement.yPx(slot, trackHeight = 800f)).isEqualTo(800f)
        assertThat(BubblePlacement.yPx(slot, trackHeight = 0f)).isEqualTo(0f)
        assertThat(BubblePlacement.yPx(BubbleSlot(yFraction = 0.5f), trackHeight = 1000f)).isEqualTo(500f)
    }

    @Test
    fun defaultSlot_spawnsOnTheRightJustBelowCentre() {
        val slot = BubbleSlot()
        assertThat(slot.onRightEdge).isTrue()
        assertThat(slot.yFraction).isEqualTo(BubblePlacement.DEFAULT_Y_FRACTION)
    }
}
