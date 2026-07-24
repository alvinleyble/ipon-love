package com.iponlove.app.feature.export.domain

/**
 * The receipt-photo ceiling on an attachment-bearing export (v1.7.0 Item 6 decision 4): **100
 * photos**, ≈25 MB and roughly a minute of downloading on a mediocre PH mobile connection. Beyond
 * that the export is refused *before* the tap with a narrowing message, rather than starting a
 * download the user will abandon. CSV is uncapped — it carries no photos.
 */
object AttachmentLimits {

    const val MAX_PHOTOS = 100

    fun exceeded(photoCount: Int): Boolean = photoCount > MAX_PHOTOS
}
