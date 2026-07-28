package com.iponlove.app.feature.analysis.domain.usecase

import com.iponlove.app.feature.analysis.domain.model.AnalysisPeriod
import com.iponlove.app.feature.analysis.domain.model.AnalysisWindow
import com.iponlove.app.feature.analysis.domain.model.ExpenseFlowData
import com.iponlove.app.feature.analysis.domain.model.FlowBucketMode
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Builds the cumulative expense curve for any Analysis range (Item 3A, grilled 2026-07-09).
 *
 * Short ranges (DAY/WEEK/MONTH/QUARTER) bucket per day; long ranges (SEMI_ANNUAL/ANNUAL/
 * ALL_TIME) bucket per month. Only EXPENSE transactions inside the bucket range count; INCOME,
 * TRANSFER, debt-settlement legs (ADR-0019 #14) and balance corrections (ADR-0057) are ignored.
 * Each entry is the running sum
 * from the first bucket through that one, so entry[N] >= entry[N-1].
 *
 * ALL_TIME is special: its window end is unbounded ([Instant.MAX]), so the bucket range runs
 * from the user's [registrationDate] month (not the 2000-01-01 sentinel — else the curve would
 * be ~300 empty months) through the current month.
 */
object ExpenseFlowCalculator {

    fun calculate(
        transactions: List<Transaction>,
        window: AnalysisWindow,
        zone: ZoneId,
        registrationDate: LocalDate?,
        today: LocalDate,
    ): ExpenseFlowData {
        val mode = bucketModeFor(window.period)

        // The bucket range as LocalDates. Non-ALL ranges use the window; ALL runs from the
        // registration month through today (the window's Instant.MAX end is unusable).
        val rangeStart: LocalDate
        val rangeEndExclusive: LocalDate
        if (window.period == AnalysisPeriod.ALL_TIME) {
            val start = (registrationDate ?: today)
            rangeStart = if (mode == FlowBucketMode.MONTHLY) start.withDayOfMonth(1) else start
            rangeEndExclusive =
                if (mode == FlowBucketMode.MONTHLY) today.withDayOfMonth(1).plusMonths(1) else today.plusDays(1)
        } else {
            rangeStart = window.startInclusive.atZone(zone).toLocalDate()
            rangeEndExclusive = window.endExclusive.atZone(zone).toLocalDate()
        }

        val bucketCount = when (mode) {
            FlowBucketMode.DAILY -> ChronoUnit.DAYS.between(rangeStart, rangeEndExclusive).toInt()
            FlowBucketMode.MONTHLY ->
                ChronoUnit.MONTHS.between(YearMonth.from(rangeStart), YearMonth.from(rangeEndExclusive)).toInt()
        }.coerceAtLeast(1)

        val daily = Array(bucketCount) { BigDecimal.ZERO }
        for (t in transactions) {
            if (t.type != TransactionType.EXPENSE) continue
            if (t.isSettlement || t.isAdjustment) continue
            val date = t.date.atZone(zone).toLocalDate()
            if (date < rangeStart || date >= rangeEndExclusive) continue
            val idx = when (mode) {
                FlowBucketMode.DAILY -> ChronoUnit.DAYS.between(rangeStart, date).toInt()
                FlowBucketMode.MONTHLY ->
                    ChronoUnit.MONTHS.between(YearMonth.from(rangeStart), YearMonth.from(date)).toInt()
            }
            if (idx in 0 until bucketCount) daily[idx] = daily[idx] + t.amount
        }

        val cumulative = ArrayList<BigDecimal>(bucketCount)
        var running = BigDecimal.ZERO
        for (d in daily) {
            running += d
            cumulative += running
        }

        val bucketStartDates = (0 until bucketCount).map { i ->
            when (mode) {
                FlowBucketMode.DAILY -> rangeStart.plusDays(i.toLong())
                FlowBucketMode.MONTHLY -> rangeStart.withDayOfMonth(1).plusMonths(i.toLong())
            }
        }

        val currentBucketIndex: Int? =
            if (today >= rangeStart && today < rangeEndExclusive) {
                when (mode) {
                    FlowBucketMode.DAILY -> ChronoUnit.DAYS.between(rangeStart, today).toInt()
                    FlowBucketMode.MONTHLY ->
                        ChronoUnit.MONTHS.between(YearMonth.from(rangeStart), YearMonth.from(today)).toInt()
                }
            } else null

        return ExpenseFlowData(
            cumulativeByBucket = cumulative,
            bucketStartDates = bucketStartDates,
            bucketMode = mode,
            currentBucketIndex = currentBucketIndex,
        )
    }

    fun bucketModeFor(period: AnalysisPeriod): FlowBucketMode = when (period) {
        AnalysisPeriod.DAY,
        AnalysisPeriod.WEEK,
        AnalysisPeriod.MONTH,
        AnalysisPeriod.QUARTER -> FlowBucketMode.DAILY
        AnalysisPeriod.SEMI_ANNUAL,
        AnalysisPeriod.ANNUAL,
        AnalysisPeriod.ALL_TIME -> FlowBucketMode.MONTHLY
    }
}
