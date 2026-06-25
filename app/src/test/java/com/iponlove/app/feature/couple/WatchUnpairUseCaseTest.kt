package com.iponlove.app.feature.couple

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.sync.InMemoryCursorStore
import com.iponlove.app.feature.couple.domain.usecase.PurgePartnerReplicaUseCase
import com.iponlove.app.feature.couple.domain.usecase.WatchUnpairUseCase
import com.iponlove.app.feature.user.domain.model.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchUnpairUseCaseTest {

    private val accounts = CountingAccountRepo()
    private val purge = PurgePartnerReplicaUseCase(
        accounts, CountingCategoryRepo(), CountingTransactionRepo(), CountingNoteRepo(),
        CountingBudgetRepo(), CountingPartnerDebtRepo(), InMemoryCursorStore(),
    )

    @Test
    fun purgesOnce_whenCoupleIdGoesNull() = runTest {
        val users = MutableStateFlow<User?>(userRow(coupleId = "c-1"))
        val watch = WatchUnpairUseCase(FakeUserFlowRepository(users), purge)

        val job = launch { watch() }
        runCurrent()
        // Already-paired initial load is recorded, not treated as a dissolution.
        assertThat(accounts.purgeCount).isEqualTo(0)

        users.value = userRow(coupleId = null)   // unpair signal (ADR-0008)
        runCurrent()
        assertThat(accounts.purgeCount).isEqualTo(1)

        job.cancel()
    }

    @Test
    fun doesNotPurge_onInitialUnpairedLoad_norOnPairing() = runTest {
        val users = MutableStateFlow<User?>(userRow(coupleId = null))
        val watch = WatchUnpairUseCase(FakeUserFlowRepository(users), purge)

        val job = launch { watch() }
        runCurrent()
        users.value = userRow(coupleId = "c-1")   // pairing: null -> set, not a dissolution
        runCurrent()

        assertThat(accounts.purgeCount).isEqualTo(0)
        job.cancel()
    }

    @Test
    fun purgesOnEachDissolution_acrossRepairing() = runTest {
        val users = MutableStateFlow<User?>(userRow(coupleId = "c-1"))
        val watch = WatchUnpairUseCase(FakeUserFlowRepository(users), purge)

        val job = launch { watch() }
        runCurrent()
        users.value = userRow(coupleId = null);  runCurrent()   // first unpair → purge
        users.value = userRow(coupleId = "c-2"); runCurrent()   // re-pair
        users.value = userRow(coupleId = null);  runCurrent()   // second unpair → purge

        assertThat(accounts.purgeCount).isEqualTo(2)
        job.cancel()
    }
}
