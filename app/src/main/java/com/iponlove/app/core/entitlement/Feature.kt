package com.iponlove.app.core.entitlement

/**
 * Boolean premium gates (§10.1 of subscription-paywall-design.md). Count-capped entities live
 * in [PlanLimits], not here; theme palettes are an allowlist, not a boolean feature.
 *
 * No scope is attached to a [Feature] itself — per G6 / ADR-0044, scope follows the ownership
 * of the specific row/instance being gated (e.g. [BUDGET_ROLLOVER] is individual on a personal
 * budget but shared on the couple budget), so callers resolve [Scope] at the call site.
 */
enum class Feature {
    NO_ADS,
    RECURRING_CALENDAR,
    RECURRING_FORECAST,
    BUDGET_ROLLOVER,
    CALCULATOR,
    ANALYSIS_EXTENDED_RANGES,
    DEEP_HISTORY,

    /**
     * Exporting **with receipt photos** — the PDF and ZIP formats (v1.7.0 Item 6 Slice 2). Plain
     * CSV is deliberately never gated: data portability stays free, text costs no Storage egress,
     * and a free user has zero receipt photos anyway (the free cap is 0 since v1.6.7 Item 9), so
     * this gate is non-cannibalising by construction. Soft gate, individual scope — export reads
     * only the signed-in user's own rows.
     */
    EXPORT_WITH_ATTACHMENTS,

    /**
     * Uploading a **premium couple photo** to override Item 9's derived banner (v1.7.0 Item 10).
     * Soft gate, **shared** scope (D1 — either partner's premium unlocks it for the couple, the
     * first shared soft gate; consumed via `PremiumGate.observeLocked(Scope.SHARED)`). Lapse is
     * non-destructive: the render is derived at read time from `unlocked && banner_url != null`
     * (see `effectiveBannerSource`), so a freeze reverts to the gradient and a re-grant auto-restores
     * the chosen photo with no re-upload. Ships dormant (kill-switch OFF).
     */
    COUPLE_BANNER_PHOTO,

    /**
     * Photographing or picking a receipt to prefill a transaction (v1.7.3 Item 2). Soft gate,
     * **individual** scope, consumed via `PremiumGate.observeLocked(Scope.INDIVIDUAL)` at both
     * entry buttons (camera and gallery — gating only one would leave a free bypass, ADR-0062
     * decision 3). **Reversed from the original "never gated" design** (ADR-0062 decision 6,
     * captain's ruling 2026-08-03): this is convenience layered on recording, not recording
     * itself — manual entry stays untouched and fully capable. `PlanLimits.maxReceiptPhotos`
     * still separately governs how many photos an unlocked scan can attach, unchanged. Ships
     * dormant (kill-switch OFF, `AppConfig.DORMANT`).
     */
    RECEIPT_SCANNING,
}
