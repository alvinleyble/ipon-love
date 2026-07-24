package com.iponlove.app.core.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The pure URL decision behind [StorageAuthInterceptor]: which requests get auth headers,
 * and how legacy public-form URLs (stamped before the buckets were confirmed private —
 * they always 400) are rewritten to the authenticated form at request time.
 */
class StorageUrlRewriteTest {

    private val supabase = "https://abc123.supabase.co"

    @Test
    fun legacyPublicUrl_isRewrittenToAuthenticatedForm() {
        val rewritten = StorageUrlRewrite.toAuthenticatedUrl(
            "$supabase/storage/v1/object/public/note-images/u1/n1/img1.jpg",
            supabase,
        )
        assertThat(rewritten)
            .isEqualTo("$supabase/storage/v1/object/authenticated/note-images/u1/n1/img1.jpg")
    }

    @Test
    fun authenticatedUrl_passesThroughUnchanged() {
        val url = "$supabase/storage/v1/object/authenticated/receipts/u1/t1.jpg"
        assertThat(StorageUrlRewrite.toAuthenticatedUrl(url, supabase)).isEqualTo(url)
    }

    @Test
    fun trailingSlashOnSupabaseUrl_doesNotBreakTheMatch() {
        val rewritten = StorageUrlRewrite.toAuthenticatedUrl(
            "$supabase/storage/v1/object/public/receipts/u1/t1.jpg",
            "$supabase/",
        )
        assertThat(rewritten)
            .isEqualTo("$supabase/storage/v1/object/authenticated/receipts/u1/t1.jpg")
    }

    @Test
    fun nonSupabaseHost_isNotTouched() {
        val url = "https://example.com/storage/v1/object/public/receipts/u1/t1.jpg"
        assertThat(StorageUrlRewrite.toAuthenticatedUrl(url, supabase)).isNull()
    }

    @Test
    fun supabaseNonStorageRequest_isNotTouched() {
        val url = "$supabase/rest/v1/transactions?select=*"
        assertThat(StorageUrlRewrite.toAuthenticatedUrl(url, supabase)).isNull()
    }

    @Test
    fun signedStorageUrl_isNotTouched() {
        // Signed URLs carry their own token — must not be rewritten or double-authed.
        val url = "$supabase/storage/v1/object/sign/receipts/u1/t1.jpg?token=xyz"
        assertThat(StorageUrlRewrite.toAuthenticatedUrl(url, supabase)).isNull()
    }

    // ---- toObjectRef: the inverse of authenticatedUrl(path), for the export receipt fetcher ----

    @Test
    fun objectRef_splitsBucketFromPath() {
        val ref = StorageUrlRewrite.toObjectRef(
            "$supabase/storage/v1/object/authenticated/receipts/u1/t1/img1.jpg",
            supabase,
        )
        assertThat(ref).isEqualTo(StorageObjectRef(bucket = "receipts", path = "u1/t1/img1.jpg"))
    }

    @Test
    fun objectRef_handlesLegacyPublicUrlsForFree() {
        // Rows stamped before the buckets were confirmed private still have to be fetchable.
        val ref = StorageUrlRewrite.toObjectRef(
            "$supabase/storage/v1/object/public/note-images/u1/n1/img1.jpg",
            supabase,
        )
        assertThat(ref).isEqualTo(StorageObjectRef(bucket = "note-images", path = "u1/n1/img1.jpg"))
    }

    @Test
    fun objectRef_dropsQueryAndFragment() {
        val ref = StorageUrlRewrite.toObjectRef(
            "$supabase/storage/v1/object/authenticated/receipts/u1/t1.jpg?download=1#frag",
            supabase,
        )
        assertThat(ref).isEqualTo(StorageObjectRef(bucket = "receipts", path = "u1/t1.jpg"))
    }

    @Test
    fun objectRef_isNullForANonStorageUrl() {
        assertThat(StorageUrlRewrite.toObjectRef("https://example.com/a/b.jpg", supabase)).isNull()
    }

    @Test
    fun objectRef_isNullWhenThereIsNoPathBeyondTheBucket() {
        val ref = StorageUrlRewrite.toObjectRef(
            "$supabase/storage/v1/object/authenticated/receipts",
            supabase,
        )
        assertThat(ref).isNull()
    }
}
