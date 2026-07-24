package com.iponlove.app.feature.couple.domain.usecase

import com.iponlove.app.feature.couple.data.upload.CoupleBannerUploader
import com.iponlove.app.feature.couple.domain.repository.CoupleRepository
import javax.inject.Inject

/**
 * Clears the couple's shared banner photo (v1.7.0 Item 10): null out the couple row's `banner_url`
 * via the `set_couple_banner` RPC, then best-effort delete the previous Storage object. Reverts both
 * surfaces to Item 9's derived accent gradient.
 */
class RemoveCoupleBannerUseCase @Inject constructor(
    private val uploader: CoupleBannerUploader,
    private val coupleRepository: CoupleRepository,
) {
    suspend operator fun invoke(currentBannerUrl: String?) {
        coupleRepository.setCoupleBanner(null)
        currentBannerUrl?.let { uploader.deleteObject(it) }
    }
}
