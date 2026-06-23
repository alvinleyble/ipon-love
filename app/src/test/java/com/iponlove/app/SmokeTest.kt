package com.iponlove.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Proves the JVM unit-test suite is wired and runs (JUnit + Truth).
 * Real domain tests — sync, money math, mappers — replace this as features land.
 */
class SmokeTest {
    @Test
    fun suite_runs() {
        assertThat(1 + 1).isEqualTo(2)
    }
}
