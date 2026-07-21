package com.iponlove.app.feature.user.data

import com.iponlove.app.core.entitlement.Entitlement
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.sync.SyncTrigger
import com.iponlove.app.feature.user.data.local.UserDao
import com.iponlove.app.feature.user.data.local.UserEntity
import com.iponlove.app.feature.user.data.remote.UserRemoteSource
import com.iponlove.app.feature.user.domain.model.User
import com.iponlove.app.feature.user.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val dao: UserDao,
    private val clock: SyncClock,
    private val currentUserProvider: CurrentUserProvider,
    private val remote: UserRemoteSource,
    private val syncTrigger: SyncTrigger = SyncTrigger.NONE,
) : UserRepository {

    // The user id is resolved inside the flow, not at construction: these builders run during
    // the sign-out window (session already null) where an eager userId() throws and crashes
    // whichever collector rebuilt the flow. Unauthenticated → emit null and stay quiet.
    override fun observeCurrentUser(): Flow<User?> = flow {
        val userId = runCatching { currentUserProvider.userId() }.getOrNull()
        if (userId == null) emit(null)
        else emitAll(dao.observeById(userId).map { it?.toDomain() })
    }

    override fun observePartner(coupleId: String): Flow<User?> = flow {
        val userId = runCatching { currentUserProvider.userId() }.getOrNull()
        if (userId == null) emit(null)
        else emitAll(dao.observePartner(coupleId, userId).map { it?.toDomain() })
    }

    override suspend fun updateAccentColor(color: String) {
        val userId = currentUserProvider.userId()
        val existing = dao.getById(userId) ?: return
        val now = clock.stamp()
        dao.upsert(existing.copy(accentColor = color, updatedAt = now, pendingSync = true))
        syncTrigger.requestPush()
    }

    override suspend fun updateAvatarMotif(motif: String) {
        val userId = currentUserProvider.userId()
        val existing = dao.getById(userId) ?: return
        val now = clock.stamp()
        dao.upsert(existing.copy(avatarMotif = motif, updatedAt = now, pendingSync = true))
        syncTrigger.requestPush()
    }

    override suspend fun updateDisplayName(name: String) {
        val userId = currentUserProvider.userId()
        val existing = dao.getById(userId) ?: return
        val now = clock.stamp()
        dao.upsert(existing.copy(displayName = name, updatedAt = now, pendingSync = true))
        syncTrigger.requestPush()
    }

    override suspend fun getSelfEntitlement(): Entitlement? {
        val userId = runCatching { currentUserProvider.userId() }.getOrNull() ?: return null
        return dao.getById(userId)?.toEntitlement()
    }

    override fun observeSelfEntitlement(): Flow<Entitlement> = flow {
        val userId = runCatching { currentUserProvider.userId() }.getOrNull()
        if (userId == null) emit(Entitlement.NONE)
        else emitAll(dao.observeById(userId).map { it?.toEntitlement() ?: Entitlement.NONE })
    }

    // The partner's coupleId is only known from our own synced row, so re-resolve the partner
    // flow whenever our row changes (e.g. pairing lands, or coupleId clears on unpair).
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePartnerEntitlement(): Flow<Entitlement?> = flow {
        val userId = runCatching { currentUserProvider.userId() }.getOrNull()
        if (userId == null) {
            emit(null)
        } else {
            emitAll(
                dao.observeById(userId).flatMapLatest { self ->
                    val coupleId = self?.coupleId
                    if (coupleId == null) flowOf(null)
                    else dao.observePartner(coupleId, userId).map { it?.toEntitlement() }
                },
            )
        }
    }

    override suspend fun writeSelfEntitlement(entitlement: Entitlement, checkedAt: Instant) {
        val userId = currentUserProvider.userId()
        val existing = dao.getById(userId) ?: return
        // Pass the row's prior updated_at so a backward wall-clock jump can't make this genuinely
        // newer write lose to the row's own previous version (ADR-0001 monotonic LWW key).
        val now = clock.stamp(existing.updatedAt)
        dao.upsert(
            existing.copy(
                isPremium = entitlement.isPremium,
                premiumUntil = entitlement.premiumUntil,
                entitlementSource = entitlement.source.name,
                entitlementCheckedAt = checkedAt,
                updatedAt = now,
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun ensureLocalRow(userId: String, displayName: String?) {
        if (dao.getById(userId) != null) return

        // Reinstall: Room is empty but the server already has a row (e.g. with couple_id set).
        // Adopt it clean so the outbox never pushes a stale NULL over real server data.
        val serverRow = runCatching { remote.fetchSelf(userId) }.getOrNull()
        if (serverRow != null) {
            dao.upsert(serverRow.toEntity())
            return
        }

        // Genuine new signup: no server row yet — create a dirty stub for the outbox.
        val now = clock.stamp()
        dao.upsert(
            UserEntity(
                id = userId,
                displayName = displayName,
                avatarUrl = null,
                accentColor = null,
                coupleId = null,
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                serverRev = null,
                pendingSync = true,
            )
        )
    }
}
