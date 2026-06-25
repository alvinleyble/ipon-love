package com.iponlove.app.feature.partnerdebt

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.partnerdebt.data.PartnerDebtRepositoryImpl
import com.iponlove.app.feature.partnerdebt.domain.usecase.RecordDebtPaymentUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.UpsertPartnerDebtUseCase
import com.iponlove.app.core.sync.SyncClock
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

/** Guards the write-side invariants the UI relies on. */
class PartnerDebtUseCaseValidationTest {

    private val dao = FakePartnerDebtDao()
    private val repository = PartnerDebtRepositoryImpl(dao, SyncClock(now = { Instant.ofEpochMilli(0) }))
    private val upsertDebt = UpsertPartnerDebtUseCase(repository)
    private val recordPayment = RecordDebtPaymentUseCase(repository)

    @Test
    fun upsertDebt_rejectsNonPositiveAmount() = runTest {
        val zero = partnerDebt("d", amount = "0.00")
        val error = runCatching { upsertDebt(zero, "c-1") }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(dao.debts).isEmpty()
    }

    @Test
    fun upsertDebt_rejectsSelfDebt() = runTest {
        val selfDebt = partnerDebt("d", borrowerId = "me", lenderId = "me", amount = "100.00")
        val error = runCatching { upsertDebt(selfDebt, "c-1") }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun upsertDebt_persistsValidDebt() = runTest {
        upsertDebt(partnerDebt("d", borrowerId = "me", lenderId = "you", amount = "100.00"), "c-1")
        assertThat(dao.debts).containsKey("d")
    }

    @Test
    fun recordPayment_rejectsNonPositiveAmount() = runTest {
        val error = runCatching { recordPayment(debtPayment("p", debtId = "d", amount = "0.00")) }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(dao.payments).isEmpty()
    }

    @Test
    fun recordPayment_persistsValidPayment() = runTest {
        recordPayment(debtPayment("p", debtId = "d", amount = "50.00"))
        assertThat(dao.payments).containsKey("p")
    }
}
