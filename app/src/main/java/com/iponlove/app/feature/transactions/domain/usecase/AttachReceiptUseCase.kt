package com.iponlove.app.feature.transactions.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Compresses a gallery [Uri] (max 1080 px, JPEG 85%) and writes it to
 * `filesDir/receipts/{transactionId}.jpg`. Returns the absolute path.
 *
 * The path is stored on [TransactionEntity.attachmentLocalPath]. On the next
 * sync cycle, [ReceiptUploader] (PreSyncStep) uploads it to Supabase Storage,
 * stamps [TransactionEntity.attachmentUrl], and clears the local path.
 */
class AttachReceiptUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(uri: Uri, transactionId: String): String {
        val bitmap = context.contentResolver.openInputStream(uri)!!.use { input ->
            BitmapFactory.decodeStream(input)
        }
        val scaled = scaledDown(bitmap)
        val dir = File(context.filesDir, "receipts").also { it.mkdirs() }
        val file = File(dir, "$transactionId.jpg")
        file.outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return file.absolutePath
    }

    private fun scaledDown(src: Bitmap): Bitmap {
        val max = 1080
        if (src.width <= max && src.height <= max) return src
        val ratio = max.toFloat() / maxOf(src.width, src.height)
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt(),
            (src.height * ratio).toInt(),
            true,
        )
    }
}
