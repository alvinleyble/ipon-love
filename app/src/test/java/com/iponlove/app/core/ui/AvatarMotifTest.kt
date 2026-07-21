package com.iponlove.app.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The motif-avatar key resolution (v1.6.7 Item 3 Leg 1). A stored value is a plain string, so a
 * null (fresh account, no backfill — dev phase) or any unrecognized value must fall back to the
 * Heart default rather than crash a render.
 */
class AvatarMotifTest {

    @Test
    fun knownKeys_resolveToTheirMotif() {
        AvatarMotif.entries.forEach { motif ->
            assertThat(AvatarMotif.fromKey(motif.key)).isEqualTo(motif)
        }
    }

    @Test
    fun nullKey_fallsBackToHeartDefault() {
        assertThat(AvatarMotif.fromKey(null)).isEqualTo(AvatarMotif.HEART)
        assertThat(AvatarMotif.Default).isEqualTo(AvatarMotif.HEART)
    }

    @Test
    fun blankOrUnknownKey_fallsBackToHeart() {
        assertThat(AvatarMotif.fromKey("")).isEqualTo(AvatarMotif.HEART)
        assertThat(AvatarMotif.fromKey("   ")).isEqualTo(AvatarMotif.HEART)
        assertThat(AvatarMotif.fromKey("not-a-real-motif")).isEqualTo(AvatarMotif.HEART)
        // A future premium-locked key this build doesn't know must not break an existing render.
        assertThat(AvatarMotif.fromKey("galaxy")).isEqualTo(AvatarMotif.HEART)
    }

    @Test
    fun catalog_hasEightMotifsWithUniqueKeys() {
        assertThat(AvatarMotif.entries).hasSize(8)
        val keys = AvatarMotif.entries.map { it.key }
        assertThat(keys.toSet()).hasSize(8) // no duplicate keys
        assertThat(keys).containsExactly(
            "heart", "leaf", "bloom", "sparkle", "wave", "crescent", "gem", "sprout",
        )
    }
}
