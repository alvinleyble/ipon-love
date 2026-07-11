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
    fun shortPasswordRejected() {
        assertThat(errorFrom { AuthCredentials.validatePassword("Ab1!") }).isEqualTo(AuthError.WEAK_PASSWORD)
    }

    @Test
    fun passwordMissingACharacterClassRejected() {
        assertThat(errorFrom { AuthCredentials.validatePassword("alllowercase1!") })
            .isEqualTo(AuthError.WEAK_PASSWORD) // no uppercase
        assertThat(errorFrom { AuthCredentials.validatePassword("ALLUPPERCASE1!") })
            .isEqualTo(AuthError.WEAK_PASSWORD) // no lowercase
        assertThat(errorFrom { AuthCredentials.validatePassword("NoDigitsHere!") })
            .isEqualTo(AuthError.WEAK_PASSWORD) // no digit
        assertThat(errorFrom { AuthCredentials.validatePassword("NoSymbols123") })
            .isEqualTo(AuthError.WEAK_PASSWORD) // no symbol
    }

    @Test
    fun passwordMeetingAllRulesPasses() {
        AuthCredentials.validatePassword("Abcdef1!") // no throw
    }

    @Test
    fun validNamePasses() {
        AuthCredentials.validateName("Patty") // no throw
        AuthCredentials.validateName("  Alvin  ") // trimmed, still valid
        AuthCredentials.validateName("a".repeat(10)) // exactly the max — no throw
        AuthCredentials.validateName("Anne Marie") // spaces allowed, still within 10
    }

    @Test
    fun blankOrWhitespaceNameRejected() {
        assertThat(errorFrom { AuthCredentials.validateName("") }).isEqualTo(AuthError.INVALID_NAME)
        assertThat(errorFrom { AuthCredentials.validateName("   ") }).isEqualTo(AuthError.INVALID_NAME)
    }

    @Test
    fun overLongNameRejected() {
        assertThat(errorFrom { AuthCredentials.validateName("a".repeat(11)) })
            .isEqualTo(AuthError.INVALID_NAME)
    }

    @Test
    fun nameWithDigitsOrSymbolsRejected() {
        assertThat(errorFrom { AuthCredentials.validateName("Alvin1") }).isEqualTo(AuthError.INVALID_NAME)
        assertThat(errorFrom { AuthCredentials.validateName("Al-vin") }).isEqualTo(AuthError.INVALID_NAME)
        assertThat(errorFrom { AuthCredentials.validateName("Alvin!") }).isEqualTo(AuthError.INVALID_NAME)
    }

    @Test
    fun filterNameInputTruncatesAndStripsDisallowedChars() {
        assertThat(AuthCredentials.filterNameInput("Alvin123")).isEqualTo("Alvin")
        assertThat(AuthCredentials.filterNameInput("a".repeat(15))).isEqualTo("a".repeat(10))
        assertThat(AuthCredentials.filterNameInput("Anne Marie Extra")).isEqualTo("Anne Marie")
        assertThat(AuthCredentials.filterNameInput("Al-vin!")).isEqualTo("Alvin")
    }

    @Test
    fun matchingPasswordsPass() {
        AuthCredentials.validatePasswordsMatch("secret1", "secret1") // no throw
    }

    @Test
    fun mismatchedPasswordsRejected() {
        assertThat(errorFrom { AuthCredentials.validatePasswordsMatch("secret1", "secret2") })
            .isEqualTo(AuthError.PASSWORD_MISMATCH)
    }
}
