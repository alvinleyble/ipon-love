package com.iponlove.app.feature.user.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iponlove.app.core.sync.SyncMeta
import java.time.Instant

/**
 * Room mirror of a `users` row. Implements [SyncMeta] for the generic sync engine.
 *
 * [isDeleted] is always false — users are never soft-deleted (not a schema column), but
 * [SyncMeta] requires it for the uniform sync algorithm. [coupleId] starts null and is
 * updated when the user pairs (written by the sync pull of the updated users row).
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey override val id: String,
    val displayName: String?,
    val avatarUrl: String?,
    val accentColor: String?,
    val coupleId: String?,
    val createdAt: Instant,
    override val updatedAt: Instant,
    override val isDeleted: Boolean = false,
    override val serverRev: Long?,
    override val pendingSync: Boolean,
) : SyncMeta
