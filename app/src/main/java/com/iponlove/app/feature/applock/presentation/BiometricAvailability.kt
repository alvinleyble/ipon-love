package com.iponlove.app.feature.applock.presentation

import android.content.Context
import androidx.biometric.BiometricManager

/**
 * Thin wrapper over [BiometricManager] so the biometric-nudge decision (item 13) stays out of the
 * Composables and out of the domain layer. "Ready" means there is biometric hardware AND an
 * enrolled credential, i.e. turning the app's biometric preference on will actually work — the only
 * case where nudging the user to enable it makes sense.
 */
object BiometricAvailability {

    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun isReady(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS
}
