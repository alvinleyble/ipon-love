package com.iponlove.app.feature.transactions.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Compresses a picked gallery [Uri] (max 1080 px, JPEG 85%) and writes it to
 * `filesDir/receipts/{imageId}.jpg`, returning the absolute path. Keyed on the image id so a
 * transaction can hold several receipts (up to [com.iponlove.app.feature.transactions.domain.model.TransactionImage.MAX]).
 *
 * The editor defers persistence to save: the returned path lives in editor state until save,
 * when [SaveTransactionImagesUseCase] creates the transaction_images row. On the next sync,
 * [com.iponlove.app.feature.transactions.data.upload.TransactionImageUploader] uploads the file
 * to Storage and stamps the row's URL.
 */
class CompressReceiptUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(uri: Uri, imageId: String): String {
        val bitmap = context.contentResolver.openInputStream(uri)!!.use { input ->
            BitmapFactory.decodeStream(input)
        }
        val scaled = scaledDown(bitmap)
        val dir = File(context.filesDir, "receipts").also { it.mkdirs() }
        val file = File(dir, "$imageId.jpg")
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
