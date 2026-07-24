package com.iponlove.app.feature.auth

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.auth.data.GoogleLinkErrorMapper
import com.iponlove.app.feature.auth.domain.model.AuthError
import org.junit.Test

class GoogleLinkErrorMapperTest {

    @Test
    fun alreadyLinkedMapsToAlreadyLinked() {
        assertThat(
            GoogleLinkErrorMapper.classify("Identity is already linked to another user"),
        ).isEqualTo(AuthError.GOOGLE_ALREADY_LINKED)
    }

    @Test
    fun identityAlreadyExistsMapsToAlreadyLinked() {
        assertThat(
            GoogleLinkErrorMapper.classify("identity_already_exists"),
        ).isEqualTo(AuthError.GOOGLE_ALREADY_LINKED)
    }

    @Test
    fun networkishFailureMapsToNetwork() {
        assertThat(GoogleLinkErrorMapper.classify("Unable to connect to host"))
            .isEqualTo(AuthError.NETWORK)
        assertThat(GoogleLinkErrorMapper.classify("network timeout"))
            .isEqualTo(AuthError.NETWORK)
    }

    @Test
    fun manualLinkingDisabledFoldsIntoGenericLinkFailure() {
        // A config-only case we control — no dedicated message, just the generic link failure.
        assertThat(GoogleLinkErrorMapper.classify("Manual linking is disabled"))
            .isEqualTo(AuthError.GOOGLE_LINK_FAILED)
    }

    @Test
    fun unknownMessageMapsToGenericLinkFailure() {
        assertThat(GoogleLinkErrorMapper.classify("something odd"))
            .isEqualTo(AuthError.GOOGLE_LINK_FAILED)
    }

    @Test
    fun nullMessageMapsToGenericLinkFailure() {
        assertThat(GoogleLinkErrorMapper.classify(null))
            .isEqualTo(AuthError.GOOGLE_LINK_FAILED)
    }
}
