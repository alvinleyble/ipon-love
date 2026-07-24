package com.iponlove.app.core.session

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject

/**
 * Supplies the signed-in user's id, stamped onto every owned row (accounts,
 * transactions, …) so sync and RLS can attribute it.
 *
 * Now session-backed by Supabase Auth ([SupabaseCurrentUserProvider]). Reads are only
 * ever made by repository writes, which only run inside the auth-gated app, so the
 * authenticated user is always present. ADR-0013 keeps the user's own row a normal
 * synced entity, so attribution stays uniform with every other table.
 */
fun interface CurrentUserProvider {
    fun userId(): String

    /**
     * The display name carried on the auth account (`user_metadata.display_name`, set at
     * registration — ADR-0016), or null if absent. Default null keeps this a functional
     * interface so test fakes can supply just [userId] as a lambda.
     */
    fun displayName(): String? = null

    /** The signed-in account's email, or null if unavailable. Shown read-only in Profile. */
    fun email(): String? = null
}

/**
 * The signed-in user's id, or null when there is no session. [userId] itself throws (a write
 * before sign-in is a bug), but cold `observe*` flows are re-collected during the sign-out
 * transition — the auth gate has already flipped to null — so they must degrade to "no user"
 * (emit empty) instead of crashing the process. Resolve the id *inside* a `flow { }` builder
 * with this, never eagerly at flow-construction time.
 */
fun CurrentUserProvider.userIdOrNull(): String? = runCatching { userId() }.getOrNull()

/** The authenticated user's id, read synchronously from the in-memory Supabase session. */
class SupabaseCurrentUserProvider @Inject constructor(
    private val client: SupabaseClient,
) : CurrentUserProvider {
    override fun userId(): String = client.auth.currentUserOrNull()?.id
        ?: error("No authenticated user — a write was attempted before sign-in")

    // Email sign-ups set `display_name` (ADR-0016); a Google identity never does — it carries
    // `full_name`/`name` instead. Onboarding has no name-entry step, so resolve across all three
    // so a Google user's row seeds with their real name, not initials (ADR-0050 decision 3).
    override fun displayName(): String? =
        DisplayNameResolver.resolve(client.auth.currentUserOrNull()?.userMetadata)

    override fun email(): String? = client.auth.currentUserOrNull()?.email
}

/**
 * Resolves the display name from auth `user_metadata`, tolerating both the email sign-up shape
 * (`display_name`, ADR-0016) and the Google identity shape (`full_name`/`name`, ADR-0050). Pure —
 * unit-tested.
 */
internal object DisplayNameResolver {
    fun resolve(metadata: JsonObject?): String? {
        if (metadata == null) return null
        return metadata.stringOrNull("display_name")
            ?: metadata.stringOrNull("full_name")
            ?: metadata.stringOrNull("name")
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}
