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

    /**
     * Splits a Storage URL back into the `(bucket, objectPath)` pair the Supabase SDK's own
     * download API speaks — the inverse of `BucketApi.authenticatedUrl(path)`.
     *
     * Rows store the *URL*, not the path, so anything wanting to fetch an object outside Coil (the
     * export facility's receipt bundler, v1.7.0 Item 6 Slice 2) has to recover the path. Routing it
     * through [toAuthenticatedUrl] first means legacy public-form URLs are handled for free, and
     * going back through the SDK means auth/refresh stays the SDK's job rather than being
     * hand-rolled a second time alongside [StorageAuthInterceptor].
     *
     * Returns `null` when [url] is not a Storage object URL for [supabaseUrl].
     */
    fun toObjectRef(url: String, supabaseUrl: String): StorageObjectRef? {
        val base = supabaseUrl.trimEnd('/')
        val authenticated = toAuthenticatedUrl(url, supabaseUrl) ?: return null
        val objectPath = authenticated
            .removePrefix(base + AUTHENTICATED_SEGMENT)
            .substringBefore('?')
            .substringBefore('#')
        val bucket = objectPath.substringBefore('/', missingDelimiterValue = "")
        val path = objectPath.substringAfter('/', missingDelimiterValue = "")
        return if (bucket.isEmpty() || path.isEmpty()) null else StorageObjectRef(bucket, path)
    }
}

/** A Storage object addressed the way the Supabase SDK wants it: bucket + path within the bucket. */
data class StorageObjectRef(val bucket: String, val path: String)
