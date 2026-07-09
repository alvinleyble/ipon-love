package com.iponlove.app.feature.subscription.presentation

import android.app.Activity
import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.billing.BillingException
import com.iponlove.app.core.billing.BillingGateway
import com.iponlove.app.core.billing.OwnedPurchase
import com.iponlove.app.core.billing.PurchaseResult
import com.iponlove.app.core.entitlement.Entitlement
import com.iponlove.app.core.entitlement.EntitlementRepository
import com.iponlove.app.core.entitlement.EntitlementSource
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The paywall orchestration (S5). The un-testable Play SDK stays behind the [BillingGateway] fake,
 * so this exercises the one rule that matters — **a completed purchase reconciles** (which is what
 * writes + propagates entitlement) — plus the restore found/not-found split and the dormant/pre-S11
 * "product unavailable" path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val activity: Activity = mockk(relaxed = true)

    private class FakeEntitlement(initial: Entitlement) : EntitlementRepository {
        val self = MutableStateFlow(initial)
        var reconcileCount = 0
        /** Simulates what a real reconcile would find (e.g. restore/purchase writing the column). */
        var onReconcile: () -> Unit = {}
        override fun observeSelf(): Flow<Entitlement> = self
        override fun observePartner(): Flow<Entitlement?> = flowOf(null)
        override suspend fun reconcile() {
            reconcileCount++
            onReconcile()
        }
    }

    private class FakeBilling : BillingGateway {
        val emitter = MutableSharedFlow<PurchaseResult>(extraBufferCapacity = 1)
        var launchResult: Result<Unit> = Result.success(Unit)
        var launchCount = 0
        override val purchaseResults: SharedFlow<PurchaseResult> = emitter
        override suspend fun launchPurchaseFlow(activity: Activity): Result<Unit> {
            launchCount++
            return launchResult
        }
        override suspend fun queryOwnedPurchases() = Result.success(emptyList<OwnedPurchase>())
        override suspend fun acknowledge(purchaseToken: String) = Result.success(Unit)
    }

    private fun none() = Entitlement.NONE
    private fun playPremium() =
        Entitlement(isPremium = true, premiumUntil = null, source = EntitlementSource.PLAY)

    private fun vm(entitlement: EntitlementRepository, billing: BillingGateway) =
        SubscriptionViewModel(entitlement, billing)

    @Test
    fun initialState_reflectsOwnedEntitlement() = runTest {
        val state = vm(FakeEntitlement(playPremium()), FakeBilling()).uiState.value
        assertThat(state.loading).isFalse()
        assertThat(state.isPremium).isTrue()
    }

    @Test
    fun initialState_notOwned_showsUpsell() = runTest {
        val state = vm(FakeEntitlement(none()), FakeBilling()).uiState.value
        assertThat(state.loading).isFalse()
        assertThat(state.isPremium).isFalse()
    }

    @Test
    fun purchaseSuccess_reconciles_andUnlocks() = runTest {
        val entitlement = FakeEntitlement(none())
        entitlement.onReconcile = { entitlement.self.value = playPremium() }
        val billing = FakeBilling()
        val viewModel = vm(entitlement, billing)

        billing.emitter.emit(PurchaseResult.Success(
            OwnedPurchase(listOf(BillingGateway.PREMIUM_PRODUCT_ID), "tok", isAcknowledged = true),
        ))

        assertThat(entitlement.reconcileCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.isPremium).isTrue()
        assertThat(viewModel.uiState.value.purchaseInProgress).isFalse()
        assertThat(viewModel.uiState.value.message).isEqualTo("Welcome to Premium!")
    }

    @Test
    fun purchaseCancelled_isSilent_noReconcile() = runTest {
        val entitlement = FakeEntitlement(none())
        val billing = FakeBilling()
        val viewModel = vm(entitlement, billing)

        billing.emitter.emit(PurchaseResult.Cancelled)

        assertThat(entitlement.reconcileCount).isEqualTo(0)
        assertThat(viewModel.uiState.value.message).isNull()
        assertThat(viewModel.uiState.value.purchaseInProgress).isFalse()
    }

    @Test
    fun onBuy_launchFailure_surfacesUnavailable_noStuckSpinner() = runTest {
        val billing = FakeBilling().apply {
            launchResult = Result.failure(BillingException(-2, "item unavailable"))
        }
        val viewModel = vm(FakeEntitlement(none()), billing)

        viewModel.onBuy(activity)

        assertThat(billing.launchCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.purchaseInProgress).isFalse()
        assertThat(viewModel.uiState.value.message).isEqualTo(
            "Premium isn't available yet. Please try again later.",
        )
    }

    @Test
    fun onRestore_found_restoresAndMessages() = runTest {
        val entitlement = FakeEntitlement(none())
        entitlement.onReconcile = { entitlement.self.value = playPremium() }
        val viewModel = vm(entitlement, FakeBilling())

        viewModel.onRestore()

        assertThat(entitlement.reconcileCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.isPremium).isTrue()
        assertThat(viewModel.uiState.value.restoreInProgress).isFalse()
        assertThat(viewModel.uiState.value.message).isEqualTo("Purchases restored.")
    }

    @Test
    fun onRestore_notFound_messagesNoPurchase() = runTest {
        val entitlement = FakeEntitlement(none()) // reconcile finds nothing
        val viewModel = vm(entitlement, FakeBilling())

        viewModel.onRestore()

        assertThat(entitlement.reconcileCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.message).isEqualTo("No previous purchase found.")
    }

    @Test
    fun onMessageShown_clearsBanner() = runTest {
        val viewModel = vm(FakeEntitlement(none()), FakeBilling())
        viewModel.onRestore()
        assertThat(viewModel.uiState.value.message).isNotNull()

        viewModel.onMessageShown()

        assertThat(viewModel.uiState.value.message).isNull()
    }
}
