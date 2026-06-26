package com.iponlove.app.core.session

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
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

/** The authenticated user's id, read synchronously from the in-memory Supabase session. */
class SupabaseCurrentUserProvider @Inject constructor(
    private val client: SupabaseClient,
) : CurrentUserProvider {
    override fun userId(): String = client.auth.currentUserOrNull()?.id
        ?: error("No authenticated user — a write was attempted before sign-in")

    override fun displayName(): String? = client.auth.currentUserOrNull()
        ?.userMetadata?.get("display_name")?.jsonPrimitive?.contentOrNull

    override fun email(): String? = client.auth.currentUserOrNull()?.email
}
