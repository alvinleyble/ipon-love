package com.iponlove.app.feature.applock.domain.usecase

import com.iponlove.app.feature.applock.domain.model.PinLockoutPolicy
import com.iponlove.app.feature.applock.domain.model.PinVerifyResult
import com.iponlove.app.feature.applock.domain.repository.AppLockRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

class VerifyPinUseCase @Inject constructor(
    private val repo: AppLockRepository,
) {
    // Not a constructor param: Dagger/Hilt ignores Kotlin default values and would otherwise
    // demand a binding for `() -> Instant`. Tests override this directly instead.
    internal var now: () -> Instant = Instant::now

    suspend operator fun invoke(rawPin: String): PinVerifyResult {
        val prefs = repo.observe().first()
        val current = now()

        val remainingLockout = PinLockoutPolicy.remainingLockoutSeconds(prefs, current)
        if (remainingLockout > 0) return PinVerifyResult.LockedOut(remainingLockout)

        if (repo.verifyPin(rawPin)) {
            repo.setPinAttemptState(0, null)
            return PinVerifyResult.Success
        }

        val next = PinLockoutPolicy.onFailure(prefs, current)
        repo.setPinAttemptState(next.failedPinAttempts, next.pinLockoutUntil)
        val newLockout = PinLockoutPolicy.remainingLockoutSeconds(next, current)
        return if (newLockout > 0) {
            PinVerifyResult.LockedOut(newLockout)
        } else {
            PinVerifyResult.Failed((PinLockoutPolicy.MAX_ATTEMPTS - next.failedPinAttempts).coerceAtLeast(0))
        }
    }
}
