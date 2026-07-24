package com.iponlove.app.feature.auth.data

import com.iponlove.app.feature.auth.domain.model.LinkedIdentity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Picks the Google identity out of the account's linked-identity list and reads its email
 * (ADR-0051). Kept a pure function over [RawIdentity] — a trivial adapter of the SDK's
 * `Identity` (provider + `identityData` claims) — so the selection + email extraction is
 * JVM-unit-testable with no Supabase types on the classpath, mirroring [DisplayNameResolver].
 */
internal object GoogleIdentityResolver {

    /** A linked identity reduced to what this resolver needs: its provider and raw claim bag. */
    data class RawIdentity(val provider: String, val identityData: JsonObject?)

    /** `null` when no Google identity is linked; otherwise the Google identity (email may be null). */
    fun resolve(identities: List<RawIdentity>): LinkedIdentity? {
        val google = identities.firstOrNull { it.provider == "google" } ?: return null
        return LinkedIdentity(email = google.identityData.stringOrNull("email"))
    }

    private fun JsonObject?.stringOrNull(key: String): String? =
        this?.get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}
