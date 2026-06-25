package com.iponlove.app.feature.notes.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.iponlove.app.feature.notes.domain.model.NoteAttachment
import com.iponlove.app.feature.notes.domain.repository.NoteAttachmentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * Picks an image from a [Uri] (gallery or camera), compresses it (max 1080 px, JPEG 85%),
 * writes it to `filesDir/note_attachments/{uuid}.jpg`, and saves a [NoteAttachmentEntity]
 * with `localPath` set and `url` null — the [NoteAttachmentUploader] will upload it on the
 * next sync and stamp the Storage URL.
 */
class AddNoteImageUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: NoteAttachmentRepository,
) {
    suspend operator fun invoke(noteId: String, uri: Uri): NoteAttachment {
        val compressed = compress(uri)
        return repository.addAttachment(noteId, compressed.absolutePath)
    }

    private fun compress(uri: Uri): File {
        val bitmap = context.contentResolver.openInputStream(uri)!!.use { input ->
            BitmapFactory.decodeStream(input)
        }
        val scaled = scaledDown(bitmap)
        val dir = File(context.filesDir, "note_attachments").also { it.mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return file
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
