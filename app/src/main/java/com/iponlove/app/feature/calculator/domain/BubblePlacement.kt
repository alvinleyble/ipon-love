package com.iponlove.app.feature.calculator.domain

/**
 * Where the collapsed calculator pill rests (ADR-0058 decision 8).
 *
 * Two values, not a free (x, y) point, because the pill only ever *hugs an edge*: horizontally it
 * snaps to whichever side it was released nearest, and vertically it sits at a **fraction of its
 * travel range** rather than an absolute pixel. The fraction is what keeps a rotation from
 * stranding the pill off-screen — a y in pixels from a 2400px-tall portrait window is meaningless
 * in a 1080px-tall landscape one, while 0.55 means the same place in both.
 */
data class BubbleSlot(
    val onRightEdge: Boolean = true,
    val yFraction: Float = BubblePlacement.DEFAULT_Y_FRACTION,
)

/**
 * Pure clamp-and-snap for the draggable pill — Android-free so the boundary behaviour is
 * JVM-testable ([com.iponlove.app.feature.calculator.BubblePlacementTest]) instead of only
 * discoverable by dragging on a device.
 */
object BubblePlacement {

    /** Spawn height: just below centre, clear of both the status bar and the bottom bar. */
    const val DEFAULT_Y_FRACTION = 0.55f

    /**
     * The slot a drag ending at [centerX] / [y] settles into.
     *
     * @param centerX the pill's horizontal *centre* at release, in px from the container's left
     *   edge — the centre rather than the leading edge so a pill straddling the midpoint snaps to
     *   the side the user actually pushed it toward, whichever direction it grew.
     * @param y the pill's top at release, in px measured **from the top of its travel range**
     *   (i.e. status-bar inset already subtracted), so 0 is as high as it may legally sit.
     * @param trackWidth container width in px; a non-positive value (a not-yet-measured frame)
     *   yields the right edge, matching the spawn side.
     * @param trackHeight the height of the travel range in px — container height minus the top
     *   inset, the reserved bottom bar, and the pill's own height. Non-positive (an unmeasured or
     *   absurdly short frame) pins the pill to the top of the range, the only safe answer: there is
     *   no room to place it anywhere else, and 0 can never be off-screen.
     */
    fun snap(centerX: Float, y: Float, trackWidth: Float, trackHeight: Float): BubbleSlot {
        val onRightEdge = trackWidth <= 0f || centerX >= trackWidth / 2f
        val fraction = if (trackHeight <= 0f) 0f else y / trackHeight
        return BubbleSlot(onRightEdge = onRightEdge, yFraction = fraction.coerceIn(0f, 1f))
    }

    /** The pill's top in px (from the top of the travel range) for [slot] on a [trackHeight] track. */
    fun yPx(slot: BubbleSlot, trackHeight: Float): Float =
        (slot.yFraction * trackHeight).coerceIn(0f, trackHeight.coerceAtLeast(0f))
}
