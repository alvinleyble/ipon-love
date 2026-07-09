package com.iponlove.app.feature.couple

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.entitlement.Entitlement
import com.iponlove.app.feature.couple.domain.model.Couple
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.repository.CoupleRepository
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import com.iponlove.app.feature.user.domain.model.User
import com.iponlove.app.feature.user.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class ObservePairingStateUseCaseTest {

    private val userRepo = FakeUserRepository()
    private val coupleRepo = FakeCoupleRepository()
    private val useCase = ObservePairingStateUseCase(userRepo, coupleRepo)

    @Test
    fun noUserRowYet_isLoading() = runTest {
        useCase().test {
            assertThat(awaitItem()).isEqualTo(PairingState.Loading)
        }
    }

    @Test
    fun userWithoutCouple_isNotPaired() = runTest {
        userRepo.currentUser.value = user(coupleId = null)

        useCase().test {
            assertThat(awaitItem()).isEqualTo(PairingState.NotPaired)
        }
    }

    @Test
    fun pairedWithPartner_emitsPairedWithBoth() = runTest {
        userRepo.currentUser.value = user(coupleId = "c-1")
        coupleRepo.couple.value = couple(user2Id = "user-2")
        userRepo.partner.value = user(id = "user-2", coupleId = "c-1")

        useCase().test {
            val state = awaitItem()
            assertThat(state).isInstanceOf(PairingState.Paired::class.java)
            state as PairingState.Paired
            assertThat(state.couple.user2Id).isEqualTo("user-2")
            assertThat(state.partner?.id).isEqualTo("user-2")
        }
    }

    @Test
    fun awaitingPartner_emitsPairedWithNullPartner() = runTest {
        userRepo.currentUser.value = user(coupleId = "c-1")
        coupleRepo.couple.value = couple(user2Id = null)
        userRepo.partner.value = null

        useCase().test {
            val state = awaitItem()
            assertThat(state).isInstanceOf(PairingState.Paired::class.java)
            state as PairingState.Paired
            assertThat(state.couple.isAwaitingPartner).isTrue()
            assertThat(state.partner).isNull()
        }
    }

    @Test
    fun softDeletedCouple_isNotPaired() = runTest {
        userRepo.currentUser.value = user(coupleId = "c-1")
        coupleRepo.couple.value = couple(user2Id = "user-2", isDeleted = true)

        useCase().test {
            assertThat(awaitItem()).isEqualTo(PairingState.NotPaired)
        }
    }

    @Test
    fun unpairing_transitionsBackToNotPaired() = runTest {
        userRepo.currentUser.value = user(coupleId = "c-1")
        coupleRepo.couple.value = couple(user2Id = "user-2")
        userRepo.partner.value = user(id = "user-2", coupleId = "c-1")

        useCase().test {
            assertThat(awaitItem()).isInstanceOf(PairingState.Paired::class.java)
            // Unpair: own couple_id clears (users pulled before couples, ADR-0009).
            userRepo.currentUser.value = user(coupleId = null)
            assertThat(awaitItem()).isEqualTo(PairingState.NotPaired)
        }
    }
}

private fun user(id: String = "user-1", coupleId: String?) =
    User(id = id, displayName = "Pat", accentColor = "#FF0000", coupleId = coupleId)

private fun couple(user2Id: String?, isDeleted: Boolean = false) = Couple(
    id = "c-1",
    name = "Us",
    inviteCode = "ABCD23",
    user1Id = "user-1",
    user2Id = user2Id,
    isDeleted = isDeleted,
)

private class FakeUserRepository : UserRepository {
    val currentUser = MutableStateFlow<User?>(null)
    val partner = MutableStateFlow<User?>(null)
    override fun observeCurrentUser(): Flow<User?> = currentUser
    override fun observePartner(coupleId: String): Flow<User?> = partner
    override suspend fun ensureLocalRow(userId: String, displayName: String?) = Unit
    override suspend fun updateAccentColor(color: String) = Unit
    override suspend fun updateDisplayName(name: String) = Unit
    override suspend fun getSelfEntitlement(): Entitlement? = null
    override fun observeSelfEntitlement(): Flow<Entitlement> = emptyFlow()
    override fun observePartnerEntitlement(): Flow<Entitlement?> = emptyFlow()
    override suspend fun writeSelfEntitlement(entitlement: Entitlement, checkedAt: Instant) = Unit
}

private class FakeCoupleRepository : CoupleRepository {
    val couple = MutableStateFlow<Couple?>(null)
    override fun observeCouple(coupleId: String): Flow<Couple?> = couple
    override suspend fun createCouple(name: String) = Unit
    override suspend fun redeemInvite(code: String) = Unit
    override suspend fun rotateInviteCode() = Unit
    override suspend fun unpair() = Unit
}
