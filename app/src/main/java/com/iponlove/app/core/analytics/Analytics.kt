package com.iponlove.app.core.analytics

/**
 * Minimal paywall-funnel telemetry (G10 / §10.10) — **no third-party SDK** (Firebase banned; no
 * off-device behavioral vendor for a finance app). [log] is fire-and-forget: it buffers a row into
 * Room and returns immediately; the buffer is flushed to Supabase on the existing sync trigger
 * (see [AnalyticsFlusher]). Only paywall-interaction events + the user id are ever recorded — never
 * financial content — so the existing privacy policy already covers it.
 *
 * Event set (§10.10): `paywall_impression`, `upsell_tap`, `purchase_started`, `purchase_success`,
 * `purchase_cancelled`, `restore`, `refund_detected`.
 */
interface Analytics {
    /**
     * Record one event. [source] is the surface it fired from (e.g. `"settings"`, a `Feature` key);
     * [params] is an optional extra bag. No-op when there is no signed-in user.
     */
    fun log(name: String, source: String? = null, params: Map<String, String> = emptyMap())
}
