package com.iponlove.app.feature.transactions.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Compresses a picked gallery or scanned camera [Uri] (max 1080 px, JPEG 85%) and writes it to
 * `filesDir/receipts/{imageId}.jpg`, returning the absolute path. Keyed on the image id so a
 * transaction can hold several receipts (up to [com.iponlove.app.feature.transactions.domain.model.TransactionImage.MAX]).
 *
 * The editor defers persistence to save: the returned path lives in editor state until save,
 * when [SaveTransactionImagesUseCase] creates the transaction_images row. On the next sync,
 * [com.iponlove.app.feature.transactions.data.upload.TransactionImageUploader] uploads the file
 * to Storage and stamps the row's URL.
 *
 * **EXIF rotation (v1.7.3 Item 2, ADR-0062 decision 10):** a live pre-existing bug fixed here as
 * part of scan landing — [BitmapFactory.decodeStream] ignores orientation and [Bitmap.compress]
 * writes none, so a source image carrying an orientation tag (routine for camera captures, rare
 * for gallery picks which usually arrive pre-rotated) was stored sideways with the tag stripped.
 * The source orientation is read and applied as a [Matrix] rotation before scaling/compressing.
 */
class CompressReceiptUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(uri: Uri, imageId: String): String {
        val decoded = context.contentResolver.openInputStream(uri)!!.use { input ->
            BitmapFactory.decodeStream(input)
        }
        val rotationDegrees = readRotationDegrees(uri)
        val oriented = applyRotation(decoded, rotationDegrees)
        val scaled = scaledDown(oriented)
        val dir = File(context.filesDir, "receipts").also { it.mkdirs() }
        val file = File(dir, "$imageId.jpg")
        file.outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        if (scaled !== oriented) scaled.recycle()
        if (oriented !== decoded) oriented.recycle()
        decoded.recycle()
        return file.absolutePath
    }

    private fun readRotationDegrees(uri: Uri): Int {
        val orientation = context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }

    private fun applyRotation(src: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return src
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
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
