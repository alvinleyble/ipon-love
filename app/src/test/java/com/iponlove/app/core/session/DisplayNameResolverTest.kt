package com.iponlove.app.core.session

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

class DisplayNameResolverTest {

    @Test
    fun prefersDisplayName() {
        val metadata = buildJsonObject {
            put("display_name", "Alvin")
            put("full_name", "Alvin Ani")
            put("name", "alvin.ani")
        }
        assertThat(DisplayNameResolver.resolve(metadata)).isEqualTo("Alvin")
    }

    @Test
    fun fallsBackToFullNameWhenNoDisplayName() {
        // The Google identity shape: no display_name, carries full_name/name instead.
        val metadata = buildJsonObject {
            put("full_name", "Alvin Ani")
            put("name", "alvin.ani")
        }
        assertThat(DisplayNameResolver.resolve(metadata)).isEqualTo("Alvin Ani")
    }

    @Test
    fun fallsBackToNameWhenOnlyNamePresent() {
        val metadata = buildJsonObject { put("name", "alvin.ani") }
        assertThat(DisplayNameResolver.resolve(metadata)).isEqualTo("alvin.ani")
    }

    @Test
    fun blankValuesAreSkipped() {
        val metadata = buildJsonObject {
            put("display_name", "")
            put("full_name", "   ")
            put("name", "Alvin")
        }
        assertThat(DisplayNameResolver.resolve(metadata)).isEqualTo("Alvin")
    }

    @Test
    fun nullMetadataResolvesToNull() {
        assertThat(DisplayNameResolver.resolve(null)).isNull()
    }

    @Test
    fun emptyMetadataResolvesToNull() {
        assertThat(DisplayNameResolver.resolve(JsonObject(emptyMap()))).isNull()
    }
}
