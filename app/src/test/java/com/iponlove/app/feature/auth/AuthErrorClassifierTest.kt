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
    fun mapsWeakPasswordCharacterClassMessage() {
        // GoTrue's actual character-class-policy message, confirmed via real device/emulator
        // testing (ADR-0027 verification) — ends with the raw error code "weak_password"
        // (underscore), which the substring match on "weak password" (space) used to miss.
        assertThat(
            AuthErrorClassifier.classify(
                "Password should contain at least one character of each: abcdefghijklmnopqrstuvwxyz, " +
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZ, 0123456789, symbols.: weak_password",
            ),
        ).isEqualTo(AuthError.WEAK_PASSWORD)
    }

    @Test
    fun mapsSamePassword() {
        // GoTrue's own updateUser check (raw error code "same_password") — rejects a recovery
        // password change when the new password matches the account's current one.
        assertThat(
            AuthErrorClassifier.classify(
                "New password should be different from the old password.: same_password",
            ),
        ).isEqualTo(AuthError.SAME_AS_OLD_PASSWORD)
    }

    @Test
    fun mapsNetworkFailures() {
        assertThat(AuthErrorClassifier.classify("Failed to connect to host"))
            .isEqualTo(AuthError.NETWORK)
        assertThat(AuthErrorClassifier.classify("Connection timeout"))
            .isEqualTo(AuthError.NETWORK)
    }

    @Test
    fun mapsRateLimit() {
        assertThat(AuthErrorClassifier.classify("For security purposes, you can only request this after 46 seconds."))
            .isEqualTo(AuthError.RATE_LIMITED)
    }

    @Test
    fun unknownAndNullFallBackToUnknown() {
        assertThat(AuthErrorClassifier.classify("some unexpected error"))
            .isEqualTo(AuthError.UNKNOWN)
        assertThat(AuthErrorClassifier.classify(null)).isEqualTo(AuthError.UNKNOWN)
    }
}
