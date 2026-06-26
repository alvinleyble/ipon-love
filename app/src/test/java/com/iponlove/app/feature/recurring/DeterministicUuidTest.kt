package com.iponlove.app.feature.recurring

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.util.DeterministicUuid
import org.junit.Test

class DeterministicUuidTest {

    @Test
    fun sameName_yieldsSameUuid() {
        assertThat(DeterministicUuid.v5("rule-1:2026-06-25"))
            .isEqualTo(DeterministicUuid.v5("rule-1:2026-06-25"))
    }

    @Test
    fun differentName_yieldsDifferentUuid() {
        assertThat(DeterministicUuid.v5("rule-1:2026-06-25"))
            .isNotEqualTo(DeterministicUuid.v5("rule-1:2026-06-26"))
    }

    @Test
    fun isVersion5_withRfc4122Variant() {
        val uuid = DeterministicUuid.v5("rule-1:2026-06-25")
        assertThat(uuid.version()).isEqualTo(5)
        assertThat(uuid.variant()).isEqualTo(2) // RFC 4122 (Leach-Salz)
    }
}
