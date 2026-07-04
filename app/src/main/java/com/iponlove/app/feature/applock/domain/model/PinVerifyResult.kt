package com.iponlove.app.feature.applock.domain.model

sealed interface PinVerifyResult {
    data object Success : PinVerifyResult
    data class Failed(val remainingAttempts: Int) : PinVerifyResult
    data class LockedOut(val remainingSeconds: Long) : PinVerifyResult
}
