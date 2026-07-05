package com.iponlove.app.feature.notes.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iponlove.app.core.sync.SyncMeta
import java.time.Instant

/**
 * Room mirror of a `notes` row. Implements [SyncMeta] so the generic sync engine reads its
 * bookkeeping uniformly. Carries the columns the domain
 * [com.iponlove.app.feature.notes.domain.model.Note] hides: `userId`, the sharing columns
 * and the sync columns.
 *
 * [title]/[content] are nullable to mirror the schema (an empty editor field maps to null).
 * [isShared]/[coupleId] are always false/null in V1 — partner sharing lands with the
 * Couples slice — but the columns exist now so that feature is a pure addition.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey override val id: String,
    val userId: String,
    val title: String?,
    val content: String?,
    val isShared: Boolean,
    @ColumnInfo(defaultValue = "0") val isPinned: Boolean,
    val coupleId: String?,
    @ColumnInfo(defaultValue = "0") val isConflictCopy: Boolean,
    val createdAt: Instant,
    override val updatedAt: Instant,
    override val isDeleted: Boolean,
    override val serverRev: Long?,
    override val pendingSync: Boolean,
) : SyncMeta
