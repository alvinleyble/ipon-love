package com.iponlove.app.core.entitlement

import android.app.Activity
import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.billing.BillingException
import com.iponlove.app.core.billing.BillingGateway
import com.iponlove.app.core.billing.OwnedPurchase
import com.iponlove.app.core.billing.PurchaseResult
import com.iponlove.app.feature.user.domain.model.User
import com.iponlove.app.feature.user.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

/**
 * The reconcile state machine (paywall S4, ADR-0044). The two headline traps the ADR exists to
 * prevent — a `GRANT` comp getting wiped (§10.7 bug #1) and a steady-state foreground churning the
 * users row — are [grantSource_skipped_neverQueriesPlay] and the two `idempotent` cases.
 */
class EntitlementReconcileTest {

    private class FakeBilling(
        var result: Result<List<OwnedPurchase>> = Result.success(emptyList()),
    ) : BillingGateway {
        var queryCount = 0
        val acknowledged = mutableListOf<String>()
        override suspend fun queryOwnedPurchases(): Result<List<OwnedPurchase>> {
            queryCount++
            return result
        }
        override suspend fun acknowledge(purchaseToken: String): Result<Unit> {
            acknowledged += purchaseToken
            return Result.success(Unit)
        }
        // Purchase-launch path (S5) — unused by the reconcile tests.
        override val purchaseResults: SharedFlow<PurchaseResult> = MutableSharedFlow()
        override suspend fun launchPurchaseFlow(activity: Activity): Result<Unit> = Result.success(Unit)
    }

    private class FakeUserRepo(var current: Entitlement?) : UserRepository {
        var written: Entitlement? = null
        var writeCount = 0
        override suspend fun getSelfEntitlement(): Entitlement? = current
        override suspend fun writeSelfEntitlement(entitlement: Entitlement, checkedAt: Instant) {
            written = entitlement
            writeCount++
            current = entitlement
        }
        override fun observeSelfEntitlement(): Flow<Entitlement> = flowOf(current ?: Entitlement.NONE)
        override fun observePartnerEntitlement(): Flow<Entitlement?> = flowOf(null)
        // Unused domain surface for these tests:
        override fun observeCurrentUser(): Flow<User?> = flowOf(null)
        override fun observePartner(coupleId: String): Flow<User?> = flowOf(null)
        override suspend fun ensureLocalRow(userId: String, displayName: String?) = Unit
        override suspend fun updateAccentColor(color: String) = Unit
        override suspend fun updateAvatarMotif(motif: String) = Unit
        override suspend fun updateDisplayName(name: String) = Unit
    }

    private fun premiumPurchase(acked: Boolean = true) = OwnedPurchase(
        productIds = listOf(BillingGateway.PREMIUM_PRODUCT_ID),
        purchaseToken = "tok-premium",
        isAcknowledged = acked,
    )

    private fun repo(user: UserRepository, billing: BillingGateway) =
        EntitlementRepositoryImpl(user, billing)

    private fun ent(isPremium: Boolean, source: EntitlementSource) =
        Entitlement(isPremium = isPremium, premiumUntil = null, source = source)

    @Test
    fun noRow_noOp_neverQueriesPlay() = runTest {
        val user = FakeUserRepo(current = null)
        val billing = FakeBilling()

        repo(user, billing).reconcile()

        assertThat(billing.queryCount).isEqualTo(0)
        assertThat(user.writeCount).isEqualTo(0)
    }

    @Test
    fun grantSource_skipped_neverQueriesPlay_neverWrites() = runTest {
        // G7 / ADR-0044 §4: a beta comp must survive a foreground even when Play says NOT_OWNED.
        val user = FakeUserRepo(current = ent(isPremium = true, source = EntitlementSource.GRANT))
        val billing = FakeBilling(result = Result.success(emptyList()))

        repo(user, billing).reconcile()

        assertThat(billing.queryCount).isEqualTo(0)
        assertThat(user.writeCount).isEqualTo(0)
        assertThat(user.current!!.source).isEqualTo(EntitlementSource.GRANT)
    }

    @Test
    fun owned_notPreviouslyPremium_writesPlayPremium() = runTest {
        val user = FakeUserRepo(current = ent(isPremium = false, source = EntitlementSource.NONE))
        val billing = FakeBilling(result = Result.success(listOf(premiumPurchase())))

        repo(user, billing).reconcile()

        assertThat(user.writeCount).isEqualTo(1)
        assertThat(user.written!!.isPremium).isTrue()
        assertThat(user.written!!.source).isEqualTo(EntitlementSource.PLAY)
        assertThat(user.written!!.premiumUntil).isNull()
    }

    @Test
    fun notOwned_previouslyPlayPremium_writesNone_onRefund() = runTest {
        // The one-time-billing lapse path: premium is lost only when a refund drops the purchase.
        val user = FakeUserRepo(current = ent(isPremium = true, source = EntitlementSource.PLAY))
        val billing = FakeBilling(result = Result.success(emptyList()))

        repo(user, billing).reconcile()

        assertThat(user.writeCount).isEqualTo(1)
        assertThat(user.written!!.isPremium).isFalse()
        assertThat(user.written!!.source).isEqualTo(EntitlementSource.NONE)
    }

    @Test
    fun owned_alreadyPlayPremium_idempotent_noWrite() = runTest {
        val user = FakeUserRepo(current = ent(isPremium = true, source = EntitlementSource.PLAY))
        val billing = FakeBilling(result = Result.success(listOf(premiumPurchase())))

        repo(user, billing).reconcile()

        assertThat(billing.queryCount).isEqualTo(1)
        assertThat(user.writeCount).isEqualTo(0)
    }

    @Test
    fun notOwned_alreadyNone_idempotent_noWrite() = runTest {
        val user = FakeUserRepo(current = ent(isPremium = false, source = EntitlementSource.NONE))
        val billing = FakeBilling(result = Result.success(emptyList()))

        repo(user, billing).reconcile()

        assertThat(user.writeCount).isEqualTo(0)
    }

    @Test
    fun billingFailure_leavesCache_noWrite() = runTest {
        // Offline tolerance (ADR-0044 §5): a transient query failure never re-locks / never writes.
        val user = FakeUserRepo(current = ent(isPremium = true, source = EntitlementSource.PLAY))
        val billing = FakeBilling(result = Result.failure(BillingException(-1, "disconnected")))

        repo(user, billing).reconcile()

        assertThat(user.writeCount).isEqualTo(0)
        assertThat(user.current!!.isPremium).isTrue()
    }

    @Test
    fun unacknowledgedOwned_isAcknowledged_thenWrites() = runTest {
        val user = FakeUserRepo(current = ent(isPremium = false, source = EntitlementSource.NONE))
        val billing = FakeBilling(result = Result.success(listOf(premiumPurchase(acked = false))))

        repo(user, billing).reconcile()

        assertThat(billing.acknowledged).containsExactly("tok-premium")
        assertThat(user.written!!.isPremium).isTrue()
    }

    @Test
    fun acknowledgedOwned_notReAcknowledged() = runTest {
        val user = FakeUserRepo(current = ent(isPremium = false, source = EntitlementSource.NONE))
        val billing = FakeBilling(result = Result.success(listOf(premiumPurchase(acked = true))))

        repo(user, billing).reconcile()

        assertThat(billing.acknowledged).isEmpty()
    }

    @Test
    fun purchaseOfOtherProduct_isIgnored_treatedAsNotOwned() = runTest {
        val user = FakeUserRepo(current = ent(isPremium = false, source = EntitlementSource.NONE))
        val otherProduct = OwnedPurchase(
            productIds = listOf("some_other_sku"),
            purchaseToken = "tok-other",
            isAcknowledged = false,
        )
        val billing = FakeBilling(result = Result.success(listOf(otherProduct)))

        repo(user, billing).reconcile()

        // Not our premium product → not owned → no ack of the foreign purchase, no premium write.
        assertThat(billing.acknowledged).isEmpty()
        assertThat(user.writeCount).isEqualTo(0)
    }
}
