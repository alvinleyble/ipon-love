package com.iponlove.app.core.util

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.analysis.domain.usecase.FlowComparisonCalculator
import com.iponlove.app.feature.analysis.domain.usecase.FlowMetricsCalculator
import com.iponlove.app.feature.analysis.domain.model.AnalysisPeriod
import com.iponlove.app.feature.analysis.domain.model.AnalysisWindow
import com.iponlove.app.feature.analysis.domain.model.FlowBucketMode
import com.iponlove.app.feature.analysis.domain.usecase.AnalysisCalculator
import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.budgets.domain.usecase.BudgetAlertSlot
import com.iponlove.app.feature.budgets.domain.usecase.BudgetProgressCalculator
import com.iponlove.app.feature.budgets.domain.usecase.CheckBudgetAlertsUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.AllocationTarget
import com.iponlove.app.feature.partnerdebt.domain.usecase.DebtAllocationCalculator
import com.iponlove.app.feature.savings.domain.model.GoalContribution
import com.iponlove.app.feature.savings.domain.model.SavingsGoal
import com.iponlove.app.feature.savings.domain.usecase.SavingsGoalCalculator
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.AccountBalanceCalculator
import org.junit.Test
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * The frozen conformance vectors of `docs/web/cross-platform-contract.md` (§1, §1b, §4),
 * executed against the live Android implementation.
 *
 * **This suite is not testing Android; it is testing the *contract*.** Every constant below is
 * a frozen value in the contract doc, and every assertion exists so that a client which drifts
 * from it — including this one — turns a *silent* divergence (duplicated rows, centavo-off
 * numbers, days later) into a red build now. It is also the regression harness the
 * Kotlin-Multiplatform extraction (W10 phase 2) is audited against: `DeterministicUuid` is
 * JVM-bound (`ByteBuffer` / `MessageDigest` / `java.util.UUID`), so sharing it is a *rewrite*
 * onto different SHA-1 and UUID primitives, and a byte-order slip in that rewrite produces
 * different ids with no other symptom.
 *
 * **A failure here is never fixed by editing the expected value.** Either the production code
 * regressed (fix the code), or the contract is being deliberately changed (which requires
 * amending `cross-platform-contract.md` and accepting that every already-written row keeps its
 * old id — see the doc's Authority note).
 */
class CrossPlatformContractConformanceTest {

    // Fixed, obviously-synthetic ids so the vectors are reproducible by hand on any platform.
    private companion object {
        const val USER = "22222222-2222-4222-8222-222222222222"
        const val RULE = "11111111-1111-4111-8111-111111111111"
        const val TXN = "33333333-3333-4333-8333-333333333333"
        const val DEBT_A = "aaaaaaaa-0000-4000-8000-000000000001"
        const val DEBT_B = "bbbbbbbb-0000-4000-8000-000000000002"

        const val NAMESPACE = "9d8f6c2e-5b1a-4f3d-9e7c-1a2b3c4d5e6f"

        val ZONE: ZoneId = ZoneId.of("Asia/Manila")
    }

    // ---------------------------------------------------------------- §1 deterministic UUIDs

    @Test
    fun v5_matchesTheFrozenVectorsForEveryNameScheme() {
        // Contract §1, "Conformance vectors". Name string on the left is verbatim what the
        // production call site builds; the UUID on the right is the frozen expectation.
        assertVector("$RULE:2026-08-05", "36cb0753-0599-5f1a-96c8-134d33b3bb7d")
        assertVector("netting:$DEBT_A:$DEBT_B", "45018764-91e5-5ded-87f8-c2640b685be4")
        assertVector("netting:$DEBT_B:$DEBT_A", "fef252c2-a279-5c95-a2f5-ef842a1f177a")
        assertVector("paid-on-behalf:$TXN", "16b41f0d-3057-5e48-9874-5575443dd715")
        assertVector("builtin-category:transfer-fee:$USER", "e10af228-a1c9-5e68-82ef-2cdeea8ed57f")
        assertVector("starter-category:$USER:food", "71ed9c9d-0291-5fcd-86d6-f6bf71e25559")
        assertVector("starter-account:$USER:cash", "1d169077-c848-51bf-b021-9d375d3df692")
        // The empty name is the purest namespace-serialization probe: nothing but the 16
        // namespace bytes are hashed, so only a byte-order or namespace-value slip can move it.
        assertVector("", "12e5b210-9978-5686-ae17-0da3b40280bb")
    }

    @Test
    fun v5_matchesTheFrozenVectorForEveryStarterCatalogKey() {
        // Contract §1, starter `{key}` catalog. Keys are frozen forever: renaming one turns a
        // reseed into a duplicate row instead of an idempotent overwrite.
        val categories = mapOf(
            "food" to "71ed9c9d-0291-5fcd-86d6-f6bf71e25559",
            "groceries" to "316bc914-9c0d-5c50-abfa-c6e752716118",
            "transport" to "1808c6d7-2915-5076-af8a-e6ce07395816",
            "shopping" to "6dd43d60-6fbe-5bc7-b4ba-885221485848",
            "rent" to "e8e3374a-6595-5e44-9b22-e359a8e3452f",
            "electricity" to "31067ccd-92b9-5863-b874-3a7aa14d6b17",
            "water" to "b246f802-6f56-56bf-b26e-c9965b2e5a63",
            "internet" to "d63599b0-8954-5c8c-89f0-9f88e0f1a4e0",
            "phoneload" to "8abc2ec4-a21d-5288-b51e-3cf2a0986e5e",
            "salary" to "0fa355e5-37a2-55fd-8282-f9ada1aee216",
            "business" to "627bd0a2-bce1-59f9-b6d2-d7fd860e524a",
            "gift" to "9d5d6838-56b0-5a9a-beaa-bcc869f4d9d9",
            "reimbursable" to "5b6f73d7-ff39-5234-bc39-83e47a72b4b0",
            "reimbursement" to "9cf70cce-2110-56ee-ad62-fa6e68316a8c",
        )
        val accounts = mapOf(
            "cash" to "1d169077-c848-51bf-b021-9d375d3df692",
            "gcash" to "8f030d9a-44c4-5988-a6ee-d1a5bcc9a520",
            "bank" to "7c8097c3-a12e-5e5e-8317-67c9117479b6",
        )
        for ((key, expected) in categories) assertVector("starter-category:$USER:$key", expected)
        for ((key, expected) in accounts) assertVector("starter-account:$USER:$key", expected)
    }

    @Test
    fun v5_setsRfc4122Version5AndVariant() {
        // Not v4 (random) and not the JDK's nameUUIDFromBytes (v3/MD5) — §1's first bullet.
        val id = DeterministicUuid.v5("$RULE:2026-08-05")
        assertThat(id.version()).isEqualTo(5)
        assertThat(id.variant()).isEqualTo(2) // RFC 4122
        assertThat(DeterministicUuid.v5("$RULE:2026-08-05")).isEqualTo(id)
        assertThat(UUID.nameUUIDFromBytes("$RULE:2026-08-05".toByteArray())).isNotEqualTo(id)
    }

    @Test
    fun v5_hashesTheNamespaceMostSignificantByteFirst() {
        // §1 pins the namespace byte-order. This asserts the *mechanism*, not just the output:
        // hashing the same 16 bytes in the other order yields a different id, so a port that
        // gets this wrong cannot accidentally pass the vectors above.
        val ns = UUID.fromString(NAMESPACE)
        val bigEndian = ByteArray(16) { i ->
            val half = if (i < 8) ns.mostSignificantBits else ns.leastSignificantBits
            (half ushr (8 * (7 - (i % 8)))).toByte()
        }
        assertThat(v5With(bigEndian, "")).isEqualTo(DeterministicUuid.v5(""))
        assertThat(v5With(bigEndian.reversedArray(), "")).isNotEqualTo(DeterministicUuid.v5(""))
    }

    @Test
    fun v5_occurrenceDateIsIsoYyyyMmDd() {
        // §1 pins `{occurrenceDate}`: the call sites interpolate a java.time.LocalDate, whose
        // toString() is ISO-8601 `yyyy-MM-dd` — zero-padded, no zone, no time.
        val date = java.time.LocalDate.of(2026, 8, 5)
        assertThat(date.toString()).isEqualTo("2026-08-05")
        assertThat(DeterministicUuid.v5("$RULE:$date"))
            .isEqualTo(UUID.fromString("36cb0753-0599-5f1a-96c8-134d33b3bb7d"))
        // Single-digit month/day stay padded — an unpadded "2026-8-5" is a different id.
        assertThat(DeterministicUuid.v5("$RULE:${java.time.LocalDate.of(2026, 1, 2)}"))
            .isNotEqualTo(DeterministicUuid.v5("$RULE:2026-1-2"))
    }

    // ------------------------------------------------------- §1b notification composite ids

    @Test
    fun budgetAlertNotificationId_matchesTheFrozenCompositeForm() {
        // §1b: `budget:{budgetId}:{yyyy-MM}:{slot}` — a plain TEXT key, NOT a v5 UUID.
        assertThat(
            CheckBudgetAlertsUseCase.notificationId("b-1", "2026-08", BudgetAlertSlot.WARN),
        ).isEqualTo("budget:b-1:2026-08:warn")
        assertThat(
            CheckBudgetAlertsUseCase.notificationId("b-1", "2026-08", BudgetAlertSlot.LIMIT),
        ).isEqualTo("budget:b-1:2026-08:limit")
        assertThat(
            CheckBudgetAlertsUseCase.notificationId("b-1", "2026-08", BudgetAlertSlot.OVER),
        ).isEqualTo("budget:b-1:2026-08:over")
    }

    // ------------------------------------------------------------------------ §4 money math

    @Test
    fun accountBalance_isExactAdditionWithNoRoundingStep() {
        // §4 site "derived balance": opening + ledger, exact, scale preserved. No division is
        // involved anywhere in the path, so no rounding mode applies (ADR-0007).
        val txns = listOf(
            txn("t1", TransactionType.INCOME, "1000.00", account = "a"),
            txn("t2", TransactionType.EXPENSE, "249.99", account = "a"),
            txn("t3", TransactionType.TRANSFER, "100.01", account = "a", to = "b"),
        )
        val balances = AccountBalanceCalculator.balances(
            mapOf("a" to BigDecimal("500.00"), "b" to BigDecimal("0.00")),
            txns,
        )
        assertThat(balances.getValue("a")).isEqualTo(BigDecimal("1150.00"))
        assertThat(balances.getValue("b")).isEqualTo(BigDecimal("100.01"))
    }

    @Test
    fun budgetSpent_isExactAdditionOfQualifyingExpensesOnly() {
        // §4 site "budget spent": Σ of EXPENSE rows in the budget's month/category, exact.
        // Settlement and adjustment legs are excluded (they move money but are not spending).
        val budget = Budget(id = "b", categoryId = "food", amount = BigDecimal("1000.00"), yearMonth = "2026-08")
        val txns = listOf(
            txn("t1", TransactionType.EXPENSE, "333.33", category = "food", at = "2026-08-01T02:00:00Z"),
            txn("t2", TransactionType.EXPENSE, "466.67", category = "food", at = "2026-08-20T02:00:00Z"),
            txn("t3", TransactionType.EXPENSE, "999.99", category = "food", at = "2026-07-31T02:00:00Z"),
            txn("t4", TransactionType.INCOME, "500.00", category = "food", at = "2026-08-02T02:00:00Z"),
            txn("t5", TransactionType.EXPENSE, "50.00", category = "food", at = "2026-08-03T02:00:00Z", settlement = true),
        )
        assertThat(BudgetProgressCalculator.spent(budget, txns, ZONE)).isEqualTo(BigDecimal("800.00"))
    }

    @Test
    fun budgetPercent_dividesAtScale4HalfUpThenTruncatesToInt() {
        // §4 site "budget percent". The two-step shape is load-bearing: divide(scale 4, HALF_UP),
        // multiply by 100, then toInt() — which TRUNCATES. 79.99% therefore reads 79, not 80.
        assertThat(percentFor("800.00", "1000.00")).isEqualTo(80)
        assertThat(percentFor("799.90", "1000.00")).isEqualTo(79)
        assertThat(percentFor("1.00", "3.00")).isEqualTo(33)   // 0.3333 → 33.33 → 33
        assertThat(percentFor("2.00", "3.00")).isEqualTo(66)   // 0.6667 → 66.67 → 66 (HALF_UP at s4)
        assertThat(percentFor("1500.00", "1000.00")).isEqualTo(150)
    }

    @Test
    fun flowMetrics_averageAndProjectionAreScale2HalfUp() {
        // §4 site "analysis pace": avg = total / elapsed at scale 2 HALF_UP; projected =
        // avg × bucketCount, re-set to scale 2 HALF_UP (it is NOT total × ratio).
        val result = FlowMetricsCalculator.calculate(
            totalExpense = BigDecimal("1000.00"),
            bucketMode = FlowBucketMode.DAILY,
            bucketCount = 31,
            currentBucketIndex = 6,   // 7 elapsed days
            allowProjection = true,
        )
        assertThat(result.avg).isEqualTo(BigDecimal("142.86"))          // 1000/7 = 142.857… → 142.86
        assertThat(result.projected).isEqualTo(BigDecimal("4428.66"))   // 142.86 × 31, exact at s2
        assertThat(result.perMonth).isFalse()
    }

    @Test
    fun flowComparison_percentChangeIsScale0HalfUp() {
        // §4 site "analysis comparison": (delta × 100) / previous at scale 0 HALF_UP, clamped.
        assertThat(FlowComparisonCalculator.calculate(BigDecimal("150.00"), BigDecimal("100.00"))!!.percentChange)
            .isEqualTo(50)
        assertThat(FlowComparisonCalculator.calculate(BigDecimal("100.50"), BigDecimal("100.00"))!!.percentChange)
            .isEqualTo(1)   // 0.5% → HALF_UP → 1, not 0
        assertThat(FlowComparisonCalculator.calculate(BigDecimal("50.00"), BigDecimal("100.00"))!!.percentChange)
            .isEqualTo(-50)
        // No previous spend at all → no percentage is defined (not "infinite", not 0).
        assertThat(FlowComparisonCalculator.calculate(BigDecimal("50.00"), BigDecimal.ZERO)!!.percentChange)
            .isNull()
        assertThat(FlowComparisonCalculator.calculate(BigDecimal.ZERO, BigDecimal.ZERO)).isNull()
    }

    @Test
    fun analysisTotals_areExactAndTransfersNeverCount() {
        // §4 site "analysis aggregation": exact BigDecimal sums; TRANSFER is ignored entirely.
        val window = AnalysisWindow(
            period = AnalysisPeriod.MONTH,
            startInclusive = Instant.parse("2026-08-01T00:00:00Z"),
            endExclusive = Instant.parse("2026-09-01T00:00:00Z"),
        )
        val result = AnalysisCalculator.analyze(
            listOf(
                txn("t1", TransactionType.INCOME, "20000.00", at = "2026-08-01T02:00:00Z"),
                txn("t2", TransactionType.EXPENSE, "1234.56", category = "food", at = "2026-08-02T02:00:00Z"),
                txn("t3", TransactionType.EXPENSE, "765.44", category = "food", at = "2026-08-03T02:00:00Z"),
                txn("t4", TransactionType.TRANSFER, "5000.00", to = "b", at = "2026-08-04T02:00:00Z"),
            ),
            window,
        )
        assertThat(result.totalIncome).isEqualTo(BigDecimal("20000.00"))
        assertThat(result.totalExpense).isEqualTo(BigDecimal("2000.00"))
        assertThat(result.net).isEqualTo(BigDecimal("18000.00"))
    }

    @Test
    fun savingsGoalProgress_dividesAtScale4HalfUpAndClamps() {
        // §4 site "savings progress": saved / target at scale 4 HALF_UP, then to Float, clamped
        // 0..1. The scale-4 step is what makes the bar identical on both clients.
        val goal = SavingsGoal(
            id = "g",
            name = "Emergency fund",
            targetAmount = BigDecimal("30000.00"),
            targetDate = null,
            icon = null,
            color = null,
            isShared = false,
        )
        val progress = SavingsGoalCalculator.withProgress(
            listOf(goal),
            listOf(contribution("c1", "g", "10000.00"), contribution("c2", "g", "1.00")),
        ).single()
        assertThat(progress.savedAmount).isEqualTo(BigDecimal("10001.00"))
        assertThat(progress.progress).isWithin(1e-6f).of(0.3334f)  // 0.33336… → s4 HALF_UP → 0.3334
        assertThat(progress.reached).isFalse()
    }

    @Test
    fun debtAllocation_isExactAndSumsBackToTheLump() {
        // §4 site "debt allocation" (ADR-0055): fill in tick order, each floored at its own
        // remaining, the last one touched absorbing the rest. Pure subtraction — no rounding
        // is possible, so a split always sums back to exactly the lump.
        val targets = listOf(
            AllocationTarget("d1", BigDecimal("500.00")),
            AllocationTarget("d2", BigDecimal("300.00")),
            AllocationTarget("d3", BigDecimal("200.00")),
        )
        val allocations = DebtAllocationCalculator.allocate(targets, BigDecimal("650.00"))
        assertThat(allocations.map { it.debtId }).containsExactly("d1", "d2").inOrder()
        assertThat(allocations.map { it.amount })
            .containsExactly(BigDecimal("500.00"), BigDecimal("150.00")).inOrder()
        assertThat(allocations.fold(BigDecimal.ZERO) { acc, a -> acc + a.amount })
            .isEqualTo(BigDecimal("650.00"))
        assertThat(DebtAllocationCalculator.ceiling(targets)).isEqualTo(BigDecimal("1000.00"))
    }

    @Test
    fun debtAllocation_blocksAboveTheCeilingRatherThanCapping() {
        // §4: over-ceiling is an error, never a silent cap — a capped settlement would post an
        // expense the debts never absorbed.
        val targets = listOf(AllocationTarget("d1", BigDecimal("100.00")))
        runCatching { DebtAllocationCalculator.allocate(targets, BigDecimal("100.01")) }
            .also { assertThat(it.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java) }
    }

    // ------------------------------------------------------------------------------ helpers

    private fun assertVector(name: String, expected: String) {
        assertThat(DeterministicUuid.v5(name).toString()).isEqualTo(expected)
    }

    /** A local re-implementation of v5 over an explicit namespace byte-array, for the byte-order probe. */
    private fun v5With(namespaceBytes: ByteArray, name: String): UUID {
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(namespaceBytes)
        sha1.update(name.toByteArray(Charsets.UTF_8))
        val bytes = sha1.digest().copyOf(16)
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x50).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
        var msb = 0L
        var lsb = 0L
        for (i in 0 until 8) msb = (msb shl 8) or (bytes[i].toLong() and 0xFF)
        for (i in 8 until 16) lsb = (lsb shl 8) or (bytes[i].toLong() and 0xFF)
        return UUID(msb, lsb)
    }

    private fun percentFor(spent: String, amount: String): Int =
        CheckBudgetAlertsUseCase()(
            budgets = listOf(Budget(id = "b", categoryId = null, amount = BigDecimal(amount), yearMonth = "2026-08")),
            transactions = listOf(txn("t", TransactionType.EXPENSE, spent, at = "2026-08-10T02:00:00Z")),
            alreadyRaisedIds = emptySet(),
            currentMonth = "2026-08",
            zone = ZONE,
            rungs = listOf(BudgetAlertSlot.WARN to 0),
        ).single().spentPercent

    private fun txn(
        id: String,
        type: TransactionType,
        amount: String,
        account: String = "a",
        to: String? = null,
        category: String? = null,
        at: String = "2026-08-10T02:00:00Z",
        settlement: Boolean = false,
    ) = Transaction(
        id = id,
        type = type,
        amount = BigDecimal(amount),
        accountId = account,
        toAccountId = to,
        categoryId = category,
        date = Instant.parse(at),
        isSettlement = settlement,
    )

    private fun contribution(id: String, goalId: String, amount: String) = GoalContribution(
        id = id,
        goalId = goalId,
        amount = BigDecimal(amount),
        note = null,
        date = Instant.parse("2026-08-10T02:00:00Z"),
        byUserId = USER,
        isMine = true,
    )
}
