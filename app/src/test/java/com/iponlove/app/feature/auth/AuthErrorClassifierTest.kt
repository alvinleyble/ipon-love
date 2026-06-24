package com.iponlove.app.feature.auth

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.auth.data.AuthErrorClassifier
import com.iponlove.app.feature.auth.domain.model.AuthError
import org.junit.Test

class AuthErrorClassifierTest {

    @Test
    fun mapsEmailNotConfirmed() {
        assertThat(AuthErrorClassifier.classify("Email not confirmed"))
            .isEqualTo(AuthError.EMAIL_NOT_CONFIRMED)
    }

    @Test
    fun mapsAlreadyRegistered() {
        assertThat(AuthErrorClassifier.classify("User already registered"))
            .isEqualTo(AuthError.EMAIL_ALREADY_REGISTERED)
        assertThat(AuthErrorClassifier.classify("email_exists"))
            .isEqualTo(AuthError.EMAIL_ALREADY_REGISTERED)
    }

    @Test
    fun mapsInvalidCredentials() {
        assertThat(AuthErrorClassifier.classify("Invalid login credentials"))
            .isEqualTo(AuthError.INVALID_CREDENTIALS)
    }

    @Test
    fun mapsWeakPassword() {
        assertThat(AuthErrorClassifier.classify("Password should be at least 6 characters"))
            .isEqualTo(AuthError.WEAK_PASSWORD)
    }

    @Test
    fun mapsNetworkFailures() {
        assertThat(AuthErrorClassifier.classify("Failed to connect to host"))
            .isEqualTo(AuthError.NETWORK)
        assertThat(AuthErrorClassifier.classify("Connection timeout"))
            .isEqualTo(AuthError.NETWORK)
    }

    @Test
    fun unknownAndNullFallBackToUnknown() {
        assertThat(AuthErrorClassifier.classify("some unexpected error"))
            .isEqualTo(AuthError.UNKNOWN)
        assertThat(AuthErrorClassifier.classify(null)).isEqualTo(AuthError.UNKNOWN)
    }
}
