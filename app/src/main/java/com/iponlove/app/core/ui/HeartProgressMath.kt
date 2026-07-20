package com.iponlove.app.core.ui

/**
 * Pure math for the heart-tipped progress bar (v1.6.7 Item 8) — extracted (no Compose imports) so
 * it's JVM-unit-testable. See `HeartProgressMathTest`.
 */
object HeartProgressMath {

    /** Clamp an arbitrary progress ratio into a drawable fraction [0f, 1f]. */
    fun fraction(progress: Float): Float = progress.coerceIn(0f, 1f)

    /**
     * The x-offset (px) for the *left edge* of the heart so its center rides the fill tip, clamped
     * so the heart never pokes out of either end of the track.
     */
    fun heartOffsetPx(fraction: Float, trackWidthPx: Float, heartSizePx: Float): Float {
        if (trackWidthPx <= heartSizePx) return 0f
        val centerX = fraction.coerceIn(0f, 1f) * trackWidthPx
        val left = centerX - heartSizePx / 2f
        return left.coerceIn(0f, trackWidthPx - heartSizePx)
    }
}
