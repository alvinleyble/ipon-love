package com.iponlove.app.feature.drafts.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iponlove.app.core.sync.SyncMeta
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.Instant

/**
 * Room mirror of a `transaction_drafts` row. Implements [SyncMeta] for the generic sync engine.
 *
 * Own-user-only: there is no partner variant and this table is never replicated (ADR-0066
 * decision 3), the `notifications` shape.
 *
 * No Room foreign keys and no index on the entity ids — matching both the Postgres table (no FK
 * on `category_id`/`account_id`, deliberately) and `TransactionEntity`'s own reasoning: a pulled
 * row can arrive before its parent, and a parked draft must survive its category being archived.
 *
 * [localImageIds] is **local-only** — never in the DTO, never pushed — the same treatment
 * [pendingSync] gets and `TransactionImageEntity.localPath` gets today. Its only consumer is the
 * orphaned-receipt sweep (ADR-0066 decision 6).
 */
@Entity(tableName = "transaction_drafts")
data class TransactionDraftEntity(
    @PrimaryKey override val id: String,
    val userId: String,
    // Every content column is nullable: a draft is a partial form.
    val type: TransactionType?,
    val amount: BigDecimal?,
    val categoryId: String?,
    val accountId: String?,
    val toAccountId: String?,
    val note: String?,
    val date: Instant?,
    val isPrivate: Boolean,
    val receiptCount: Int,
    /** Local-only (see class doc); stored via [com.iponlove.app.core.database.converters.IponConverters]. */
    val localImageIds: List<String>,
    val createdAt: Instant,
    override val updatedAt: Instant,
    override val isDeleted: Boolean,
    override val serverRev: Long?,
    override val pendingSync: Boolean,
) : SyncMeta
