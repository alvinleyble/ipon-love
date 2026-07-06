package com.iponlove.app.core.network

import com.iponlove.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches Supabase auth to Coil's image requests so photos in the **private** Storage buckets
 * (`note-images`, `receipts`) actually load. Bucket RLS then decides per-object access — the
 * owner always, the partner only via the shared-note / shared-transaction policies.
 *
 * Also rewrites legacy `/object/public/` URLs (stamped before the buckets were confirmed
 * private — those links always 400) to `/object/authenticated/` at request time, so rows
 * already in the database and partner-replicated copies of them are fixed without a data
 * migration. The rewrite happens below Coil's cache, so cache keys stay stable.
 *
 * Non-Supabase and non-Storage requests pass through untouched.
 */
class StorageAuthInterceptor(
    private val client: SupabaseClient,
    private val supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val authenticated = StorageUrlRewrite.toAuthenticatedUrl(request.url.toString(), supabaseUrl)
            ?: return chain.proceed(request)

        val builder = request.newBuilder()
            .url(authenticated)
            .header("apikey", anonKey)
        client.auth.currentAccessTokenOrNull()?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }
        return chain.proceed(builder.build())
    }
}

/** Pure URL logic, separated from the OkHttp chain so it can be unit-tested directly. */
object StorageUrlRewrite {

    private const val PUBLIC_SEGMENT = "/storage/v1/object/public/"
    private const val AUTHENTICATED_SEGMENT = "/storage/v1/object/authenticated/"

    /**
     * Returns the authenticated-form URL when [url] is a Storage object request against
     * [supabaseUrl] (rewriting a legacy public-form URL if needed), or `null` when the
     * request is not ours to touch.
     */
    fun toAuthenticatedUrl(url: String, supabaseUrl: String): String? {
        val base = supabaseUrl.trimEnd('/')
        return when {
            url.startsWith(base + PUBLIC_SEGMENT) ->
                base + AUTHENTICATED_SEGMENT + url.removePrefix(base + PUBLIC_SEGMENT)
            url.startsWith(base + AUTHENTICATED_SEGMENT) -> url
            else -> null
        }
    }
}
