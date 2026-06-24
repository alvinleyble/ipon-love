package com.iponlove.app.core.session

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
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
}

/** The authenticated user's id, read synchronously from the in-memory Supabase session. */
class SupabaseCurrentUserProvider @Inject constructor(
    private val client: SupabaseClient,
) : CurrentUserProvider {
    override fun userId(): String = client.auth.currentUserOrNull()?.id
        ?: error("No authenticated user — a write was attempted before sign-in")
}
