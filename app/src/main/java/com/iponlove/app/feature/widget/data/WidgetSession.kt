package com.iponlove.app.feature.widget.data

/**
 * The outcome of resolving the widget's session state (Item 36): what to display now, and whether to
 * write a hint back to [WidgetSessionStore].
 */
data class WidgetSessionResolution(
    /** Whether the widget should treat this render as signed in. */
    val hasSession: Boolean,
    /** A hint value to persist, or `null` to leave the store untouched (nothing new was learned). */
    val seedHint: Boolean?,
)

/**
 * Decide the widget's session state from the fast persisted hint, falling back to a live probe only
 * when the hint was never written. Pure over its inputs (the probe is injected), so it's unit-tested.
 *
 * - hint present → use it directly, persist nothing (the always-on writer owns the hint).
 * - hint `null` (migration gap) → run the probe. The probe returns `null` when it couldn't determine
 *   a session in time; we then display **fail-closed** (masked) but persist **nothing**, so a slow
 *   cold start never bakes a wrong "signed out" hint. Only a real probe answer is seeded.
 */
suspend fun resolveWidgetSession(
    hint: Boolean?,
    liveProbe: suspend () -> Boolean?,
): WidgetSessionResolution =
    if (hint != null) {
        WidgetSessionResolution(hasSession = hint, seedHint = null)
    } else {
        val probed = liveProbe()
        WidgetSessionResolution(hasSession = probed ?: false, seedHint = probed)
    }
