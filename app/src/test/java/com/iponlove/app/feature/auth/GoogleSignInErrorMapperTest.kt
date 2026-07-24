package com.iponlove.app.feature.auth

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.auth.data.GoogleSignInErrorMapper
import com.iponlove.app.feature.auth.domain.model.AuthError
import org.junit.Test

class GoogleSignInErrorMapperTest {

    @Test
    fun cancellationIsSilent() {
        assertThat(
            GoogleSignInErrorMapper.classify("GetCredentialCancellationException", "User cancelled"),
        ).isNull()
    }

    @Test
    fun noCredentialMapsToGoogleNoAccount() {
        assertThat(
            GoogleSignInErrorMapper.classify("NoCredentialException", "No credential available"),
        ).isEqualTo(AuthError.GOOGLE_NO_ACCOUNT)
    }

    @Test
    fun networkishFailureMapsToNetwork() {
        assertThat(
            GoogleSignInErrorMapper.classify("GetCredentialException", "Unable to connect to host"),
        ).isEqualTo(AuthError.NETWORK)
        assertThat(
            GoogleSignInErrorMapper.classify("GetCredentialUnknownException", "network timeout"),
        ).isEqualTo(AuthError.NETWORK)
    }

    @Test
    fun otherFailureMapsToGenericGoogleFailure() {
        assertThat(
            GoogleSignInErrorMapper.classify("GetCredentialUnknownException", "something odd"),
        ).isEqualTo(AuthError.GOOGLE_SIGN_IN_FAILED)
    }

    @Test
    fun nullInputsMapToGenericFailure() {
        assertThat(GoogleSignInErrorMapper.classify(null, null))
            .isEqualTo(AuthError.GOOGLE_SIGN_IN_FAILED)
    }

    @Test
    fun cancellationTakesPrecedenceOverMessage() {
        // Even if the message mentions the network, a cancellation stays silent.
        assertThat(
            GoogleSignInErrorMapper.classify("GetCredentialCancellationException", "network hiccup"),
        ).isNull()
    }
}
