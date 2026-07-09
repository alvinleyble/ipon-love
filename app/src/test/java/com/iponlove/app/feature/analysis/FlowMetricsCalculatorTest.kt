package com.iponlove.app.feature.analysis

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.analysis.domain.model.FlowBucketMode
import com.iponlove.app.feature.analysis.domain.usecase.FlowMetricsCalculator
import org.junit.Test
import java.math.BigDecimal

class FlowMetricsCalculatorTest {

    private fun calc(
        expense: String,
        bucketMode: FlowBucketMode = FlowBucketMode.DAILY,
        bucketCount: Int = 30,
        currentBucketIndex: Int? = 9, // elapsed = 10 by default
        allowProjection: Boolean = true,
    ) = FlowMetricsCalculator.calculate(
        totalExpense = BigDecimal(expense),
        bucketMode = bucketMode,
        bucketCount = bucketCount,
        currentBucketIndex = currentBucketIndex,
        allowProjection = allowProjection,
    )

    @Test
    fun avg_dividesTotalByElapsedBuckets() {
        // elapsed = currentBucketIndex + 1 = 10
        val result = calc("3000.00")
        assertThat(result.avg).isEqualTo(BigDecimal("300.00"))
    }

    @Test
    fun avg_pastPeriod_dividesByFullBucketCount() {
        val result = calc("9000.00", currentBucketIndex = null, bucketCount = 30)
        assertThat(result.avg).isEqualTo(BigDecimal("300.00"))
        assertThat(result.projected).isNull()
    }

    @Test
    fun avg_roundsHalfUp() {
        // 100 / 3 = 33.333… → 33.33
        val result = calc("100", currentBucketIndex = 2)
        assertThat(result.avg).isEqualTo(BigDecimal("33.33"))
    }

    @Test
    fun avg_zeroExpense_isZero() {
        val result = calc("0")
        assertThat(result.avg).isEqualTo(BigDecimal("0.00"))
    }

    @Test
    fun projected_currentPeriod_isAvgTimesFullBucketCount() {
        // avg = 1000/10 = 100, projected = 100 * 30 = 3000
        val result = calc("1000.00", bucketCount = 30, currentBucketIndex = 9)
        assertThat(result.projected).isEqualTo(BigDecimal("3000.00"))
    }

    @Test
    fun projected_isNullWhenProjectionDisallowed() {
        // ALL_TIME passes allowProjection = false even though today is "current".
        val result = calc("1000.00", currentBucketIndex = 5, allowProjection = false)
        assertThat(result.projected).isNull()
    }

    @Test
    fun projected_isNullForSingleBucketRange() {
        val result = calc("500.00", bucketCount = 1, currentBucketIndex = 0)
        assertThat(result.projected).isNull()
    }

    @Test
    fun perMonth_reflectsBucketMode() {
        assertThat(calc("100", bucketMode = FlowBucketMode.MONTHLY).perMonth).isTrue()
        assertThat(calc("100", bucketMode = FlowBucketMode.DAILY).perMonth).isFalse()
    }
}
