package com.iponlove.app.feature.user.data

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.user.data.local.UserDao
import com.iponlove.app.feature.user.data.local.UserEntity
import com.iponlove.app.feature.user.domain.model.User
import com.iponlove.app.feature.user.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val dao: UserDao,
    private val clock: SyncClock,
    private val currentUserProvider: CurrentUserProvider,
) : UserRepository {

    override fun observeCurrentUser(): Flow<User?> =
        dao.observeById(currentUserProvider.userId()).map { it?.toDomain() }

    override suspend fun ensureLocalRow(userId: String) {
        if (dao.getById(userId) != null) return
        val now = clock.stamp()
        dao.upsert(
            UserEntity(
                id = userId,
                displayName = null,
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
