package com.iponlove.app.core.entitlement

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Count-cap limits (§10.1, `proposed` values). One instance per tier — shared caps (annotated
 * in the design doc, not modeled as a separate type here) resolve to [PREMIUM] whenever
 * [EffectiveAccess] is true for either partner (D1); callers pick the tier via [resolve]
 * before reading a field, so this class itself carries no scope.
 *
 * Adding a field here also requires adding it to [PlanLimitsOverrides] below — nothing links
 * the two at compile time (§11 review finding #3).
 */
@Serializable
data class PlanLimits(
    val maxPersonalAccounts: Int,
    val maxSharedAccounts: Int,
    val maxPersonalCategories: Int,
    val maxSharedCategories: Int,
    val maxPersonalBudgets: Int,
    val maxSharedBudgets: Int,
    val maxPersonalSavingsGoals: Int,
    val maxSharedSavingsGoals: Int,
    val maxCoupleDebtEntries: Int,
    val maxReceiptPhotos: Int,
    val maxNoteAttachments: Int,
    val maxNoteChars: Int,
    val maxNotes: Int,
) {
    companion object {
        val FREE = PlanLimits(
            maxPersonalAccounts = 10,
            maxSharedAccounts = 1,
            maxPersonalCategories = 10,
            maxSharedCategories = 1,
            maxPersonalBudgets = 5,
            maxSharedBudgets = 1,
            maxPersonalSavingsGoals = 5,
            maxSharedSavingsGoals = 1,
            maxCoupleDebtEntries = 10,
            maxReceiptPhotos = 0,
            maxNoteAttachments = 0,
            maxNoteChars = 5_000,
            maxNotes = 10_000,
        )

        val PREMIUM = PlanLimits(
            maxPersonalAccounts = 100,
            maxSharedAccounts = 50,
            maxPersonalCategories = 150,
            maxSharedCategories = 30,
            maxPersonalBudgets = 100,
            maxSharedBudgets = 50,
            maxPersonalSavingsGoals = 50,
            maxSharedSavingsGoals = 20,
            maxCoupleDebtEntries = 100,
            maxReceiptPhotos = 3,
            maxNoteAttachments = 3,
            maxNoteChars = 50_000,
            maxNotes = 10_000,
        )

        fun forAccess(hasAccess: Boolean): PlanLimits = if (hasAccess) PREMIUM else FREE

        /**
         * Resolves the effective limits for [hasAccess] (from [EffectiveAccess.hasAccess]),
         * with the matching free/premium branch of the raw `app_config.cap_overrides` jsonb
         * ([overridesJson], verbatim from `AppConfig.capOverridesJson`, core/config's S1 cache)
         * applied on top. The two tiers are overridden independently — the jsonb shape is
         * `{"free": {...}, "premium": {...}}` — so tuning the free cap during a promo can never
         * leak onto premium users (or vice versa); every cap is remote-overridable per D3, but
         * only within the tier it's meant for. A missing branch, a missing field, or malformed
         * JSON all fail open to the hardcoded tier default (fail-open, per G4's spirit) — a bad
         * remote value must never crash or lock out a device.
         */
        fun resolve(hasAccess: Boolean, overridesJson: String?): PlanLimits {
            val base = forAccess(hasAccess)
            if (overridesJson == null) return base
            val envelope = runCatching {
                overrideJson.decodeFromString<PlanLimitsOverridesEnvelope>(overridesJson)
            }.getOrNull() ?: return base
            val overrides = if (hasAccess) envelope.premium else envelope.free
            return base.applyOverrides(overrides)
        }
    }
}

@Serializable
private data class PlanLimitsOverridesEnvelope(
    val free: PlanLimitsOverrides? = null,
    val premium: PlanLimitsOverrides? = null,
)

// Mirrors PlanLimits with every field optional — an absent key leaves the base tier's value
// untouched, so a cap_overrides branch only needs to carry the fields being tuned.
@Serializable
private data class PlanLimitsOverrides(
    val maxPersonalAccounts: Int? = null,
    val maxSharedAccounts: Int? = null,
    val maxPersonalCategories: Int? = null,
    val maxSharedCategories: Int? = null,
    val maxPersonalBudgets: Int? = null,
    val maxSharedBudgets: Int? = null,
    val maxPersonalSavingsGoals: Int? = null,
    val maxSharedSavingsGoals: Int? = null,
    val maxCoupleDebtEntries: Int? = null,
    val maxReceiptPhotos: Int? = null,
    val maxNoteAttachments: Int? = null,
    val maxNoteChars: Int? = null,
    val maxNotes: Int? = null,
)

private val overrideJson = Json { ignoreUnknownKeys = true }

private fun PlanLimits.applyOverrides(overrides: PlanLimitsOverrides?): PlanLimits {
    if (overrides == null) return this
    return copy(
        maxPersonalAccounts = overrides.maxPersonalAccounts ?: maxPersonalAccounts,
        maxSharedAccounts = overrides.maxSharedAccounts ?: maxSharedAccounts,
        maxPersonalCategories = overrides.maxPersonalCategories ?: maxPersonalCategories,
        maxSharedCategories = overrides.maxSharedCategories ?: maxSharedCategories,
        maxPersonalBudgets = overrides.maxPersonalBudgets ?: maxPersonalBudgets,
        maxSharedBudgets = overrides.maxSharedBudgets ?: maxSharedBudgets,
        maxPersonalSavingsGoals = overrides.maxPersonalSavingsGoals ?: maxPersonalSavingsGoals,
        maxSharedSavingsGoals = overrides.maxSharedSavingsGoals ?: maxSharedSavingsGoals,
        maxCoupleDebtEntries = overrides.maxCoupleDebtEntries ?: maxCoupleDebtEntries,
        maxReceiptPhotos = overrides.maxReceiptPhotos ?: maxReceiptPhotos,
        maxNoteAttachments = overrides.maxNoteAttachments ?: maxNoteAttachments,
        maxNoteChars = overrides.maxNoteChars ?: maxNoteChars,
        maxNotes = overrides.maxNotes ?: maxNotes,
    )
}
