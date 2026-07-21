package com.iponlove.app.feature.partnerdebt

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.partnerdebt.domain.model.DebtPayment
import com.iponlove.app.feature.partnerdebt.domain.model.NetDirection
import com.iponlove.app.feature.partnerdebt.domain.model.PartnerDebt
import com.iponlove.app.feature.partnerdebt.domain.repository.PartnerDebtRepository
import com.iponlove.app.feature.partnerdebt.domain.usecase.ObservePartnerDebtBoardUseCase
import com.iponlove.app.core.entitlement.Entitlement
import com.iponlove.app.feature.user.domain.model.User
import com.iponlove.app.feature.user.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class ObservePartnerDebtBoardUseCaseTest {

    private val currentUser = MutableStateFlow<User?>(null)
    private val partner = MutableStateFlow<User?>(
        User(id = "you", displayName = "Patty", accentColor = "#00FF00", coupleId = "c-1"),
    )
    private val debts = MutableStateFlow<List<PartnerDebt>>(emptyList())
    private val payments = MutableStateFlow<List<DebtPayment>>(emptyList())

    private val userRepo = object : UserRepository {
        override fun observeCurrentUser(): Flow<User?> = currentUser
        override fun observePartner(coupleId: String): Flow<User?> = partner
        override suspend fun ensureLocalRow(userId: String, displayName: String?) = Unit
        override suspend fun updateAccentColor(color: String) = Unit
        override suspend fun updateAvatarMotif(motif: String) = Unit
        override suspend fun updateDisplayName(name: String) = Unit
        override suspend fun getSelfEntitlement(): Entitlement? = null
        override fun observeSelfEntitlement(): Flow<Entitlement> = emptyFlow()
        override fun observePartnerEntitlement(): Flow<Entitlement?> = emptyFlow()
        override suspend fun writeSelfEntitlement(entitlement: Entitlement, checkedAt: Instant) = Unit
    }
    private val debtRepo = object : PartnerDebtRepository {
        override fun observeDebts(coupleId: String): Flow<List<PartnerDebt>> = debts
        override fun observePayments(): Flow<List<DebtPayment>> = payments
        override suspend fun getDebt(id: String): PartnerDebt? = null
        override suspend fun getActiveDebts(coupleId: String): List<PartnerDebt> = emptyList()
        override suspend fun getActivePayments(): List<DebtPayment> = emptyList()
        override suspend fun upsertDebt(debt: PartnerDebt, coupleId: String) = Unit
        override suspend fun deleteDebt(id: String) = Unit
        override suspend fun upsertPayment(payment: DebtPayment) = Unit
        override suspend fun stampReceiverTxn(paymentId: String, receiverTxnId: String) = Unit
        override suspend fun purgeCoupleDebts() = Unit
    }
    private val useCase = ObservePartnerDebtBoardUseCase(userRepo, debtRepo)

    @Test
    fun emitsNull_whenNotPaired() = runTest {
        currentUser.value = User(id = "me", displayName = "Alvin", accentColor = null, coupleId = null)

        useCase().test {
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun emitsBoard_whenPaired_derivingNetFromDebts() = runTest {
        currentUser.value = User(id = "me", displayName = "Alvin", accentColor = null, coupleId = "c-1")
        debts.value = listOf(partnerDebt("d", borrowerId = "me", lenderId = "you", amount = "1000.00"))
        payments.value = listOf(debtPayment("p", debtId = "d", amount = "250.00"))

        useCase().test {
            val board = awaitItem()
            assertThat(board).isNotNull()
            assertThat(board!!.net.direction).isEqualTo(NetDirection.I_OWE)
            assertThat(board.net.amount.toPlainString()).isEqualTo("750.00")
            assertThat(board.net.counterpartName).isEqualTo("Patty")
        }
    }
}
