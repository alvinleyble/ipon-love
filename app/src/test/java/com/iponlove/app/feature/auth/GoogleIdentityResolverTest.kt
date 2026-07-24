package com.iponlove.app.feature.auth

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.auth.data.GoogleIdentityResolver
import com.iponlove.app.feature.auth.data.GoogleIdentityResolver.RawIdentity
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

class GoogleIdentityResolverTest {

    private fun google(email: String?) = RawIdentity(
        provider = "google",
        identityData = buildJsonObject { if (email != null) put("email", email) },
    )

    private val emailIdentity = RawIdentity(
        provider = "email",
        identityData = buildJsonObject { put("email", "alvin@example.com") },
    )

    @Test
    fun googleWithEmailResolvesToThatEmail() {
        val linked = GoogleIdentityResolver.resolve(listOf(emailIdentity, google("gmail@gmail.com")))
        assertThat(linked).isNotNull()
        assertThat(linked!!.email).isEqualTo("gmail@gmail.com")
    }

    @Test
    fun googleWithoutEmailKeyResolvesToNullEmail() {
        val linked = GoogleIdentityResolver.resolve(listOf(google(email = null)))
        assertThat(linked).isNotNull()
        assertThat(linked!!.email).isNull()
    }

    @Test
    fun googleWithBlankEmailResolvesToNullEmail() {
        val linked = GoogleIdentityResolver.resolve(listOf(google("   ")))
        assertThat(linked).isNotNull()
        assertThat(linked!!.email).isNull()
    }

    @Test
    fun googleWithNullIdentityDataResolvesToNullEmail() {
        val linked = GoogleIdentityResolver.resolve(
            listOf(RawIdentity(provider = "google", identityData = null)),
        )
        assertThat(linked).isNotNull()
        assertThat(linked!!.email).isNull()
    }

    @Test
    fun noGoogleIdentityResolvesToNull() {
        assertThat(GoogleIdentityResolver.resolve(listOf(emailIdentity))).isNull()
    }

    @Test
    fun emptyListResolvesToNull() {
        assertThat(GoogleIdentityResolver.resolve(emptyList())).isNull()
    }

    @Test
    fun picksGoogleFromAMixedList() {
        val linked = GoogleIdentityResolver.resolve(
            listOf(emailIdentity, google("picked@gmail.com")),
        )
        assertThat(linked!!.email).isEqualTo("picked@gmail.com")
    }
}
