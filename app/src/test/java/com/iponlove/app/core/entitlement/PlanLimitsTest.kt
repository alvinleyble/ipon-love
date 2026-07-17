package com.iponlove.app.core.entitlement

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlanLimitsTest {

    @Test
    fun forAccess_true_returnsPremiumTier() {
        assertThat(PlanLimits.forAccess(true)).isEqualTo(PlanLimits.PREMIUM)
    }

    @Test
    fun forAccess_false_returnsFreeTier() {
        assertThat(PlanLimits.forAccess(false)).isEqualTo(PlanLimits.FREE)
    }

    @Test
    fun freeTier_matchesSpec() {
        // §10.1's full free-tier row, field by field.
        assertThat(PlanLimits.FREE).isEqualTo(
            PlanLimits(
                maxPersonalAccounts = 10,
                maxSharedAccounts = 1,
                maxPersonalCategories = 10,
                maxSharedCategories = 1,
                maxPersonalBudgets = 5,
                maxSharedBudgets = 1,
                maxPersonalSavingsGoals = 5,
                maxSharedSavingsGoals = 1,
                maxCoupleDebtEntries = 10,
                maxReceiptPhotos = 1,
                maxNoteAttachments = 0,
                maxNoteChars = 5_000,
                maxNotes = 10_000,
            )
        )
    }

    @Test
    fun premiumTier_matchesSpec() {
        // §10.1's full premium-tier row, field by field.
        assertThat(PlanLimits.PREMIUM).isEqualTo(
            PlanLimits(
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
        )
    }

    @Test
    fun maxNotes_isIdenticalOnBothTiers() {
        // Unadvertised ceiling (G1) — same on free and premium.
        assertThat(PlanLimits.PREMIUM.maxNotes).isEqualTo(PlanLimits.FREE.maxNotes)
    }

    @Test
    fun resolve_nullJson_returnsBaseTier() {
        assertThat(PlanLimits.resolve(hasAccess = false, overridesJson = null)).isEqualTo(PlanLimits.FREE)
        assertThat(PlanLimits.resolve(hasAccess = true, overridesJson = null)).isEqualTo(PlanLimits.PREMIUM)
    }

    @Test
    fun resolve_freeOverride_onlyAffectsFreeTier() {
        val json = """{"free":{"maxPersonalAccounts":20,"maxPersonalBudgets":8}}"""

        val free = PlanLimits.resolve(hasAccess = false, overridesJson = json)
        assertThat(free.maxPersonalAccounts).isEqualTo(20)
        assertThat(free.maxPersonalBudgets).isEqualTo(8)
        assertThat(free.maxSharedAccounts).isEqualTo(PlanLimits.FREE.maxSharedAccounts)

        // A free-branch-only override must never bleed onto premium users.
        val premium = PlanLimits.resolve(hasAccess = true, overridesJson = json)
        assertThat(premium).isEqualTo(PlanLimits.PREMIUM)
    }

    @Test
    fun resolve_premiumOverride_onlyAffectsPremiumTier() {
        val json = """{"premium":{"maxReceiptPhotos":5}}"""

        val premium = PlanLimits.resolve(hasAccess = true, overridesJson = json)
        assertThat(premium.maxReceiptPhotos).isEqualTo(5)
        assertThat(premium.maxPersonalBudgets).isEqualTo(PlanLimits.PREMIUM.maxPersonalBudgets)

        val free = PlanLimits.resolve(hasAccess = false, overridesJson = json)
        assertThat(free).isEqualTo(PlanLimits.FREE)
    }

    @Test
    fun resolve_missingBranch_fallsBackToBaseTier() {
        val json = """{"free":{"maxPersonalBudgets":8}}"""
        // No "premium" key at all — premium resolution must fail open to the hardcoded default.
        assertThat(PlanLimits.resolve(hasAccess = true, overridesJson = json)).isEqualTo(PlanLimits.PREMIUM)
    }

    @Test
    fun resolve_sharedBudgetsOverride_appliesToEachTierIndependently() {
        val json = """{"free":{"maxSharedBudgets":3},"premium":{"maxSharedBudgets":80}}"""
        assertThat(PlanLimits.resolve(hasAccess = false, overridesJson = json).maxSharedBudgets).isEqualTo(3)
        assertThat(PlanLimits.resolve(hasAccess = true, overridesJson = json).maxSharedBudgets).isEqualTo(80)
    }

    @Test
    fun resolve_unknownKeys_areIgnored() {
        val json = """{"free":{"someFutureField":99,"maxPersonalBudgets":8}}"""
        assertThat(PlanLimits.resolve(hasAccess = false, overridesJson = json).maxPersonalBudgets).isEqualTo(8)
    }

    @Test
    fun resolve_malformedJson_fallsBackToBaseTier() {
        assertThat(PlanLimits.resolve(hasAccess = false, overridesJson = "not json")).isEqualTo(PlanLimits.FREE)
        assertThat(PlanLimits.resolve(hasAccess = true, overridesJson = "not json")).isEqualTo(PlanLimits.PREMIUM)
    }
}
