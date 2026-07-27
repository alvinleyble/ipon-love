package com.iponlove.app.feature.partnerdebt.domain.usecase

import java.math.BigDecimal

/**
 * A debt eligible to absorb part of a lump settlement, with its already-netted remaining
 * balance. Order matters: [DebtAllocationCalculator.allocate] fills targets in the order given
 * (the tapped debt first, then the others in the order the user ticked them — ADR-0055 #3).
 */
data class AllocationTarget(val debtId: String, val remaining: BigDecimal)

/** How much of one lump settlement lands on one debt. */
data class DebtAllocation(val debtId: String, val amount: BigDecimal)

/**
 * Splits one lump settlement across several same-direction debts (ADR-0055). The money-math
 * end-state is the same however the lump spreads — each debt floors at zero and the couple's
 * net is unchanged — so this is really about *which labelled debt gets marked paid off*, which
 * is why the fill order is the user's tick order rather than anything derived.
 *
 * Pure and total: no rounding is possible (only subtraction of BigDecimals that already share
 * the money scale), so a split always sums back to exactly the lump.
 */
object DebtAllocationCalculator {

    /**
     * The most that can be paid across [targets] — their combined remaining. Typing more than
     * this is blocked in the UI rather than silently capped (ADR-0055 #4), and it rises live
     * as the user ticks more debts.
     */
    fun ceiling(targets: List<AllocationTarget>): BigDecimal =
        targets.fold(BigDecimal.ZERO) { sum, target -> sum + target.remaining }

    /**
     * Fills [targets] in order from [lump], each floored at its own remaining, the last one
     * touched taking whatever is left (possibly partial). Targets past the point the lump runs
     * out get no allocation at all — a zero-amount payment row would be noise, and
     * [RecordDebtPaymentUseCase] rejects it anyway.
     *
     * @throws IllegalArgumentException if [lump] is not positive or exceeds [ceiling] — the UI
     *   surfaces both as inline errors, so reaching here with either is a programming error.
     */
    fun allocate(targets: List<AllocationTarget>, lump: BigDecimal): List<DebtAllocation> {
        require(lump.signum() > 0) { "Settlement amount must be greater than zero" }
        require(lump <= ceiling(targets)) { "Settlement amount exceeds the selected debts' total" }

        val allocations = mutableListOf<DebtAllocation>()
        var rest = lump
        for (target in targets) {
            if (rest.signum() <= 0) break
            val take = rest.coerceAtMost(target.remaining)
            if (take.signum() <= 0) continue
            allocations += DebtAllocation(target.debtId, take)
            rest -= take
        }
        return allocations
    }
}
