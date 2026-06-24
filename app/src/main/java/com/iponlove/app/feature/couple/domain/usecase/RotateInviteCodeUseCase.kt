package com.iponlove.app.feature.couple.domain.usecase

import com.iponlove.app.feature.couple.domain.repository.CoupleRepository
import javax.inject.Inject

/** Rotate the current couple's invite code (e.g. after sharing the old one). */
class RotateInviteCodeUseCase @Inject constructor(
    private val repository: CoupleRepository,
) {
    suspend operator fun invoke() = repository.rotateInviteCode()
}
