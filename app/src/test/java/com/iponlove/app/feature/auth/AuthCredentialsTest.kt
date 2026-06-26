package com.iponlove.app.feature.auth

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.auth.domain.model.AuthError
import com.iponlove.app.feature.auth.domain.model.AuthException
import com.iponlove.app.feature.auth.domain.usecase.AuthCredentials
import org.junit.Test

class AuthCredentialsTest {

    private fun errorFrom(block: () -> Unit): AuthError {
        return try {
            block()
            error("expected AuthException")
        } catch (e: AuthException) {
            e.error
        }
    }

    @Test
    fun validEmailPasses() {
        AuthCredentials.validateEmail("alvin@example.com") // no throw
    }

    @Test
    fun blankOrMalformedEmailRejected() {
        assertThat(errorFrom { AuthCredentials.validateEmail("") }).isEqualTo(AuthError.INVALID_EMAIL)
        assertThat(errorFrom { AuthCredentials.validateEmail("nope") }).isEqualTo(AuthError.INVALID_EMAIL)
        assertThat(errorFrom { AuthCredentials.validateEmail("@example.com") }).isEqualTo(AuthError.INVALID_EMAIL)
        assertThat(errorFrom { AuthCredentials.validateEmail("a@") }).isEqualTo(AuthError.INVALID_EMAIL)
        assertThat(errorFrom { AuthCredentials.validateEmail("a b@example.com") }).isEqualTo(AuthError.INVALID_EMAIL)
    }

    @Test
    fun shortPasswordRejected_sixCharsOk() {
        assertThat(errorFrom { AuthCredentials.validatePassword("12345") }).isEqualTo(AuthError.WEAK_PASSWORD)
        AuthCredentials.validatePassword("123456") // exactly the minimum — no throw
    }

    @Test
    fun validNamePasses() {
        AuthCredentials.validateName("Patty") // no throw
        AuthCredentials.validateName("  Alvin  ") // trimmed, still valid
        AuthCredentials.validateName("a".repeat(50)) // exactly the max — no throw
    }

    @Test
    fun blankOrWhitespaceNameRejected() {
        assertThat(errorFrom { AuthCredentials.validateName("") }).isEqualTo(AuthError.INVALID_NAME)
        assertThat(errorFrom { AuthCredentials.validateName("   ") }).isEqualTo(AuthError.INVALID_NAME)
    }

    @Test
    fun overLongNameRejected() {
        assertThat(errorFrom { AuthCredentials.validateName("a".repeat(51)) })
            .isEqualTo(AuthError.INVALID_NAME)
    }
}
