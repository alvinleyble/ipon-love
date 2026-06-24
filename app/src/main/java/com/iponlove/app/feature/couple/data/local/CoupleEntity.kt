package com.iponlove.app.feature.couple.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iponlove.app.core.sync.SyncMeta
import java.time.Instant

/**
 * Room mirror of a `couples` row. Implements [SyncMeta] for the generic sync engine.
 *
 * Couples are **read-only on the client**: every mutation (create, redeem invite, rotate
 * code, unpair) goes through a SECURITY DEFINER RPC server-side (ADR-0006, ADR-0008), so a
 * couple row only ever lands here via pull and [pendingSync] is always false. The fields
 * still implement the full [SyncMeta] contract so the row syncs uniformly with every other
 * table — [isDeleted] flips true when the couple is dissolved on unpair.
 */
@Entity(tableName = "couples")
data class CoupleEntity(
    @PrimaryKey override val id: String,
    val coupleName: String,
    val inviteCode: String,
    val user1Id: String,
    val user2Id: String?,
    val createdAt: Instant,
    override val updatedAt: Instant,
    override val isDeleted: Boolean,
    override val serverRev: Long?,
    override val pendingSync: Boolean,
) : SyncMeta
