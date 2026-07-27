package com.iponlove.app.feature.notifications

import com.iponlove.app.feature.notifications.data.local.NotificationDao
import com.iponlove.app.feature.notifications.data.local.NotificationEntity
import com.iponlove.app.feature.notifications.data.remote.NotificationDto
import com.iponlove.app.feature.notifications.data.remote.NotificationRemoteSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/** In-memory [NotificationDao] for fast JVM tests. */
class FakeNotificationDao : NotificationDao {
    val store = linkedMapOf<String, NotificationEntity>()
    private val changes = MutableStateFlow(0)

    override fun observeInbox(): Flow<List<NotificationEntity>> =
        changes.map { store.values.filter { !it.isDeleted }.sortedByDescending { it.createdAt } }

    override fun observeUnreadCount(): Flow<Int> =
        changes.map { store.values.count { !it.isDeleted && !it.isRead } }

    override suspend fun unread(): List<NotificationEntity> =
        store.values.filter { !it.isDeleted && !it.isRead }

    override suspend fun active(): List<NotificationEntity> =
        store.values.filter { !it.isDeleted }

    override suspend fun getById(id: String): NotificationEntity? = store[id]

    override suspend fun idsWithPrefix(prefix: String): List<String> =
        store.keys.filter { it.startsWith(prefix) }

    override suspend fun insertIfAbsent(row: NotificationEntity): Long {
        if (store.containsKey(row.id)) return -1L
        store[row.id] = row
        changes.value++
        return 1L
    }

    override suspend fun upsertAll(rows: List<NotificationEntity>) {
        rows.forEach { store[it.id] = it }
        changes.value++
    }

    override suspend fun expiredBefore(cutoff: Instant): List<NotificationEntity> =
        store.values.filter { it.createdAt < cutoff }

    override suspend fun hardDelete(ids: List<String>) {
        ids.forEach { store.remove(it) }
        changes.value++
    }

    override suspend fun dirtyRows(): List<NotificationEntity> =
        store.values.filter { it.pendingSync }

    override suspend fun clearPending(ids: List<String>) {
        ids.forEach { id -> store[id]?.let { store[id] = it.copy(pendingSync = false) } }
        changes.value++
    }

    override suspend fun applyPullBatch(rows: List<NotificationEntity>) {
        rows.forEach { store[it.id] = it }
        changes.value++
    }
}

/** In-memory [NotificationRemoteSource]; [failDelete] simulates an offline retention sweep. */
class FakeNotificationRemote(var failDelete: Boolean = false) : NotificationRemoteSource {
    val pushed = mutableListOf<NotificationDto>()
    val deleted = mutableListOf<String>()
    val serverRows = mutableListOf<NotificationDto>()

    override suspend fun push(rows: List<NotificationDto>): List<String> {
        pushed += rows
        return rows.map { it.id }
    }

    override suspend fun pull(cursor: Long, limit: Int): List<NotificationDto> =
        serverRows.filter { (it.serverRev ?: 0L) > cursor }.sortedBy { it.serverRev }.take(limit)

    override suspend fun delete(ids: List<String>) {
        if (failDelete) error("offline")
        deleted += ids
    }
}

fun notificationEntity(
    id: String,
    userId: String = "user-1",
    category: String = "budget",
    title: String = "Food at 80%",
    body: String = "You've used 80% of your Food budget.",
    deepLink: String? = "manage",
    isRead: Boolean = false,
    createdAt: Instant = Instant.ofEpochMilli(1_000),
    updatedAt: Instant = Instant.ofEpochMilli(1_000),
    isDeleted: Boolean = false,
    serverRev: Long? = null,
    pendingSync: Boolean = false,
) = NotificationEntity(
    id = id,
    userId = userId,
    category = category,
    title = title,
    body = body,
    deepLink = deepLink,
    isRead = isRead,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = pendingSync,
)

fun notificationDto(
    id: String,
    category: String = "budget",
    isRead: Boolean = false,
    serverRev: Long? = null,
    updatedAt: Instant = Instant.ofEpochMilli(1_000),
    isDeleted: Boolean = false,
) = NotificationDto(
    id = id,
    userId = "user-1",
    category = category,
    title = "Food at 80%",
    body = "You've used 80% of your Food budget.",
    deepLink = "manage",
    isRead = isRead,
    createdAt = Instant.ofEpochMilli(1_000),
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)
