package com.iponlove.app.core.database

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec

/**
 * v22 → v23: receipts became multi-image (a `transaction_images` child table auto-created by
 * Room), so the single-photo columns on `transactions` are dropped. Existing receipts are
 * backfilled server-side into transaction_images and pulled down on the new table's cursor-0,
 * so no local data migration is needed here — just the column removal.
 */
@DeleteColumn(tableName = "transactions", columnName = "attachmentUrl")
@DeleteColumn(tableName = "transactions", columnName = "attachmentLocalPath")
class DeleteReceiptColumnsMigration : AutoMigrationSpec
