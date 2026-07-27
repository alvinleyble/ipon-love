package com.iponlove.app.feature.partnerdebt

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.partnerdebt.domain.usecase.CheckPartnerDebtNotificationsUseCase
import org.junit.Test

/** Item 9 grill: notify iff the partner authored the debt AND the current user is the borrower. */
class CheckPartnerDebtNotificationsUseCaseTest {

    private val useCase = CheckPartnerDebtNotificationsUseCase()

    @Test
    fun fires_whenCurrentUserIsBorrowerAndDebtNotSeenOrRaised() {
        val debt = partnerDebt("d-1", borrowerId = "me", lenderId = "partner")

        val result = useCase(listOf(debt), currentUserId = "me", seenDebtIds = emptySet(), alreadyRaisedIds = emptySet())

        assertThat(result).hasSize(1)
        assertThat(result.single().debt.id).isEqualTo("d-1")
        assertThat(result.single().notificationId).isEqualTo("debt:d-1")
    }

    @Test
    fun silent_whenCurrentUserIsLenderNotBorrower() {
        // "Patty logs I owe Alvin" (current user = Alvin = lender) — Alvin isn't the one who owes.
        val debt = partnerDebt("d-1", borrowerId = "partner", lenderId = "me")

        val result = useCase(listOf(debt), currentUserId = "me", seenDebtIds = emptySet(), alreadyRaisedIds = emptySet())

        assertThat(result).isEmpty()
    }

    @Test
    fun silent_whenDebtIsSelfAuthored() {
        // Marked authored at create time regardless of direction — a user's own entry must
        // never notify themselves, even when they made themselves the borrower.
        val debt = partnerDebt("d-1", borrowerId = "me", lenderId = "partner")

        val result = useCase(listOf(debt), currentUserId = "me", seenDebtIds = setOf("d-1"), alreadyRaisedIds = emptySet())

        assertThat(result).isEmpty()
    }

    @Test
    fun silent_whenAlreadyRaised_dedupedBySlotNotRawId() {
        val debt = partnerDebt("d-1", borrowerId = "me", lenderId = "partner")

        val result = useCase(
            listOf(debt),
            currentUserId = "me",
            seenDebtIds = emptySet(),
            alreadyRaisedIds = setOf("debt:d-1"),
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun editingAnExistingDebt_staysSilent_sinceIdIsUnchanged() {
        // An edit reuses the same id; once raised, the inbox's own id-based dedup covers it —
        // this use case doesn't need separate "edit" handling.
        val edited = partnerDebt("d-1", borrowerId = "me", lenderId = "partner", amount = "999.00")

        val result = useCase(
            listOf(edited),
            currentUserId = "me",
            seenDebtIds = emptySet(),
            alreadyRaisedIds = setOf("debt:d-1"),
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun multipleDebts_onlyBorrowerAndUnseenAndUnraisedOnesFire() {
        val iOwe = partnerDebt("d-borrower", borrowerId = "me", lenderId = "partner")
        val theyOwe = partnerDebt("d-lender", borrowerId = "partner", lenderId = "me")
        val selfAuthored = partnerDebt("d-authored", borrowerId = "me", lenderId = "partner")
        val alreadyNotified = partnerDebt("d-raised", borrowerId = "me", lenderId = "partner")

        val result = useCase(
            debts = listOf(iOwe, theyOwe, selfAuthored, alreadyNotified),
            currentUserId = "me",
            seenDebtIds = setOf("d-authored"),
            alreadyRaisedIds = setOf("debt:d-raised"),
        )

        assertThat(result.map { it.debt.id }).containsExactly("d-borrower")
    }
}
