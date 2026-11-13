package com.empathie.energyaccounting.domain.model

import java.time.LocalDate

/**
 * One [EnergyDimension]'s contribution to a [PeriodInsights] report.
 *
 * [spend] / [gain] carry 100% of each entry's energy value, so they sum to the period
 * totals.
 */
data class DimensionImpactSummary(
    val dimension: EnergyDimension,
    val spend: Int,
    val spendSharePercent: Int,
    val gain: Int,
    val gainSharePercent: Int,
)

/**
 * One [TimeSlot]'s gain, spend and net balance across a period.
 *
 * [averageNetPerActiveDay] divides [netBalance] by the number of distinct days on which
 * this slot has entries, and is `0` when the slot has none.
 */
data class TimeSlotImpactSummary(
    val timeSlot: TimeSlot,
    val totalGain: Int,
    val totalSpend: Int,
    val netBalance: Int,
    val averageNetPerActiveDay: Int,
)

/**
 * One bucket of the Insights trend chart.
 *
 * A single-day period emits one point per [TimeSlot] ([timeSlot] set); a multi-day
 * period emits one point per calendar day ([date] set), including days with no entries.
 */
data class EnergyChartPoint(
    val label: String,
    val timeSlot: TimeSlot? = null,
    val date: LocalDate? = null,
    val gain: Int,
    val spend: Int,
    val netBalance: Int,
)

/**
 * Aggregated insights for an inclusive [startDate]..[endDate] range.
 *
 * [averageDailyNet] divides [netBalance] by [activeDaysCount] with integer division and
 * is `0` when nothing was logged.
 */
data class PeriodInsights(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val activeDaysCount: Int,
    val totalGain: Int,
    val totalSpend: Int,
    val netBalance: Int,
    val averageDailyNet: Int,
    val byDimension: List<DimensionImpactSummary>,
    val byTimeSlot: List<TimeSlotImpactSummary>,
    val chartPoints: List<EnergyChartPoint>,
)
