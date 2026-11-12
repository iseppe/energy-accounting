package com.empathie.energyaccounting.domain.model

import java.time.LocalDate

/**
 * One [EnergyDimension]'s contribution to a [PeriodInsights] report.
 *
 * [primarySpend] / [primaryGain] carry 100% of each entry's energy value, so they sum
 * to the period totals. [secondaryOccurrenceCount] counts entries that tagged this
 * dimension as secondary and never contributes to the totals.
 */
data class DimensionImpactSummary(
    val dimension: EnergyDimension,
    val primarySpend: Int,
    val primarySpendSharePercent: Int,
    val primaryGain: Int,
    val primaryGainSharePercent: Int,
    val secondaryOccurrenceCount: Int,
)

/**
 * One [TimeSlot]'s gain, spend and net balance across a period.
 */
data class TimeSlotImpactSummary(
    val timeSlot: TimeSlot,
    val totalGain: Int,
    val totalSpend: Int,
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
)
