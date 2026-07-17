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
}
