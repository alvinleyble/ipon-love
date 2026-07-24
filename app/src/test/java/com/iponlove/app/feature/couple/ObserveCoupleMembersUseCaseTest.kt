package com.iponlove.app.feature.couple

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.entitlement.Entitlement
import com.iponlove.app.feature.couple.domain.model.Couple
import com.iponlove.app.feature.couple.domain.repository.CoupleRepository
import com.iponlove.app.feature.couple.domain.usecase.ObserveCoupleMembersUseCase
import com.iponlove.app.feature.user.domain.model.User
import com.iponlove.app.feature.user.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

/**
 * Tier-1 tests for the couple-name plumbing added in v1.7.0 Item 9 Slice B: the use case now
 * threads the couple row's name alongside both members (for the Combined-view identity banner),
 * still emitting null when not paired and tolerating a not-yet-replicated couple row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveCoupleMembersUseCaseTest {

    private val me = User(id = "me", displayName = "Alvin", accentColor = "#1565C0", coupleId = "c-1")
    private val partner = User(id = "you", displayName = "Patty", accentColor = "#C62828", coupleId = "c-1")
    private val couple = Couple(
        id = "c-1", name = "Alvin & Patty", inviteCode = "ABC123",
        user1Id = "me", user2Id = "you", isDeleted = false,
    )

    @Test
    fun `threads couple name alongside both members`() = runTest {
        val useCase = ObserveCoupleMembersUseCase(
            FakeUserRepo(MutableStateFlow(me), MutableStateFlow(partner)),
            FakeCoupleRepo(MutableStateFlow(couple)),
        )

        val result = useCase().first()

        assertThat(result).isNotNull()
        assertThat(result!!.me).isEqualTo(me)
        assertThat(result.partner).isEqualTo(partner)
        assertThat(result.coupleName).isEqualTo("Alvin & Patty")
    }

    @Test
    fun `emits null when not paired`() = runTest {
        val soloMe = me.copy(coupleId = null)
        val useCase = ObserveCoupleMembersUseCase(
            FakeUserRepo(MutableStateFlow(soloMe), MutableStateFlow(null)),
            FakeCoupleRepo(MutableStateFlow(null)),
        )

        assertThat(useCase().first()).isNull()
    }

    @Test
    fun `couple name is null until the couple row replicates`() = runTest {
        val useCase = ObserveCoupleMembersUseCase(
            FakeUserRepo(MutableStateFlow(me), MutableStateFlow(partner)),
            FakeCoupleRepo(MutableStateFlow(null)),
        )

        val result = useCase().first()

        assertThat(result).isNotNull()
        assertThat(result!!.partner).isEqualTo(partner)
        assertThat(result.coupleName).isNull()
    }

    private class FakeUserRepo(
        private val current: MutableStateFlow<User?>,
        private val partner: MutableStateFlow<User?>,
    ) : UserRepository {
        override fun observeCurrentUser(): Flow<User?> = current
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

    private class FakeCoupleRepo(
        private val couple: MutableStateFlow<Couple?>,
    ) : CoupleRepository {
        override fun observeCouple(coupleId: String): Flow<Couple?> = couple
        override suspend fun createCouple(name: String) = Unit
        override suspend fun redeemInvite(code: String) = Unit
        override suspend fun rotateInviteCode() = Unit
        override suspend fun unpair() = Unit
    }
}
