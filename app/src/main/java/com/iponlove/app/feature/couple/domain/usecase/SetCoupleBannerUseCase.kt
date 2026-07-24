package com.iponlove.app.feature.couple.domain.usecase

import android.graphics.Bitmap
import com.iponlove.app.feature.couple.data.upload.CoupleBannerUploader
import com.iponlove.app.feature.couple.domain.repository.CoupleRepository
import javax.inject.Inject

/**
 * Sets the couple's shared banner photo (v1.7.0 Item 10): compress + upload the cropped [bitmap] to
 * the `couple-banners` bucket, point the couple row at the new URL via the `set_couple_banner` RPC,
 * then delete the previous object (decision 6 — replace deletes the old). Upload/RPC failures
 * propagate so the ViewModel can surface them and leave the existing banner untouched; the old-object
 * cleanup is best-effort and never fails the operation.
 *
 * Mirrors CompressReceiptUseCase's Android-in-usecase pragmatism (the codebase already accepts a
 * `Bitmap` parameter in this layer for image use cases); the actual bitmap work lives in the uploader.
 */
class SetCoupleBannerUseCase @Inject constructor(
    private val uploader: CoupleBannerUploader,
    private val coupleRepository: CoupleRepository,
) {
    suspend operator fun invoke(coupleId: String, currentBannerUrl: String?, bitmap: Bitmap) {
        val newUrl = uploader.upload(coupleId, bitmap)
        coupleRepository.setCoupleBanner(newUrl)
        currentBannerUrl?.let { uploader.deleteObject(it) }
    }
}
