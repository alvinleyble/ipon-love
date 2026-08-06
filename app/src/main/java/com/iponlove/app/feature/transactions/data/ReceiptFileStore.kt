package com.iponlove.app.feature.transactions.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one owner of the `filesDir/receipts/{imageId}.jpg` convention — where a compressed receipt
 * lives from pick/scan time until its `transaction_images` row is uploaded.
 *
 * Three call sites now depend on that layout: [com.iponlove.app.feature.transactions.domain.usecase.CompressReceiptUseCase]
 * writes it, [com.iponlove.app.feature.transactions.domain.usecase.CleanupOrphanedReceiptsUseCase]
 * sweeps it, and a parked draft resolves its photos back out of it (ADR-0066) — so the naming
 * lives here rather than being spelled out in each. Distinct from
 * [ReceiptScanFileStore], which owns the short-lived full-resolution `cacheDir/scans` captures.
 */
@Singleton
class ReceiptFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** The receipts directory, created on demand. */
    fun dir(): File = File(context.filesDir, "receipts").also { it.mkdirs() }

    /** Where [imageId]'s compressed JPEG lives. The file may or may not exist. */
    fun fileFor(imageId: String): File = File(dir(), "$imageId.jpg")

    /** Absolute path of [imageId]'s file if it is actually on disk, else null. */
    fun pathIfPresent(imageId: String): String? = fileFor(imageId).takeIf { it.exists() }?.absolutePath

    /** Deletes [imageIds]' files. Missing files are simply skipped. */
    fun delete(imageIds: Collection<String>) {
        imageIds.forEach { fileFor(it).delete() }
    }
}
