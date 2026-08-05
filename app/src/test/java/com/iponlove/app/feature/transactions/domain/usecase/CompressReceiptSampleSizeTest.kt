package com.iponlove.app.feature.transactions.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.domain.usecase.CompressReceiptUseCase.Companion.MAX_EDGE
import com.iponlove.app.feature.transactions.domain.usecase.CompressReceiptUseCase.Companion.sampleSizeFor
import org.junit.Test

/**
 * The pure subsampling predicate behind receipt compression (v1.7.3 Item 2). Every scan now feeds
 * a full-resolution camera frame through this path, so the decode must never materialise the
 * whole 12 MP bitmap — and must still leave enough pixels for the exact resize to 1080 px.
 */
class CompressReceiptSampleSizeTest {

    @Test
    fun `an image already under the ceiling is not subsampled`() {
        assertThat(sampleSizeFor(800, 600)).isEqualTo(1)
    }

    @Test
    fun `a 12 megapixel camera frame is subsampled to a fraction of its pixels`() {
        // 4000x3000: /2 = 2000 (still >= 1080), /4 = 1000 (below), so 2.
        assertThat(sampleSizeFor(4000, 3000)).isEqualTo(2)
    }

    @Test
    fun `a very large scan subsamples further`() {
        // 9000 halves to 4500, 2250, 1125 — the last step still clears the 1080 ceiling.
        assertThat(sampleSizeFor(9000, 6000)).isEqualTo(8)
    }

    @Test
    fun `the longest edge drives the decision on a portrait frame`() {
        assertThat(sampleSizeFor(3000, 4000)).isEqualTo(2)
    }

    @Test
    fun `subsampling never drops the longest edge below the output ceiling`() {
        for (edge in listOf(1080, 1600, 2048, 3000, 4000, 6000, 9000, 12000)) {
            val decoded = edge / sampleSizeFor(edge, edge / 2)
            assertThat(decoded).isAtLeast(MAX_EDGE)
        }
    }
}
