package com.iponlove.app.feature.user.data.local

import androidx.room.ColumnInfo
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
    // Premium entitlement (dormant paywall infra, D2 / ADR-0044). Defaults let the Room
    // auto-migration (23→24) add these columns without an AutoMigrationSpec.
    @ColumnInfo(defaultValue = "0") val isPremium: Boolean = false,
    val premiumUntil: Instant? = null,
    @ColumnInfo(defaultValue = "NONE") val entitlementSource: String = "NONE",
    val entitlementCheckedAt: Instant? = null,
    val createdAt: Instant,
    override val updatedAt: Instant,
    override val isDeleted: Boolean = false,
    override val serverRev: Long?,
    override val pendingSync: Boolean,
) : SyncMeta
