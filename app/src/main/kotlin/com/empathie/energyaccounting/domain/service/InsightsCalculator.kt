package com.empathie.energyaccounting.domain.service

import com.empathie.energyaccounting.domain.model.DimensionImpactSummary
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.EnergyDimension
import com.empathie.energyaccounting.domain.model.Entry
import com.empathie.energyaccounting.domain.model.PeriodInsights
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.domain.model.TimeSlotImpactSummary
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Pure math behind the Insights tab.
 *
 * Only each entry's primary dimension receives its energy value, so the dimension
 * breakdown always adds up to the period's gain and spend. Secondary dimensions are
 * counted as occurrences for context and filtering, never as extra energy.
 */
object InsightsCalculator {

    fun calculate(
        startDate: LocalDate,
        endDate: LocalDate,
        entries: List<Entry>,
        allDimensions: List<EnergyDimension>,
    ): PeriodInsights {
        val inRange = entries.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }

        val totalGain = inRange.filter { it.direction == Direction.GAIN }.sumOf { it.energyValue }
        val totalSpend = inRange.filter { it.direction == Direction.SPEND }.sumOf { it.energyValue }
        val netBalance = totalGain - totalSpend
        val activeDaysCount = inRange.map { it.date }.distinct().size
        val averageDailyNet = if (activeDaysCount == 0) 0 else netBalance / activeDaysCount

        val byDimension = allDimensions.map { dimension ->
            val primarySpend = inRange
                .filter { it.primaryDimension.id == dimension.id && it.direction == Direction.SPEND }
                .sumOf { it.energyValue }
            val primaryGain = inRange
                .filter { it.primaryDimension.id == dimension.id && it.direction == Direction.GAIN }
                .sumOf { it.energyValue }

            DimensionImpactSummary(
                dimension = dimension,
                primarySpend = primarySpend,
                primarySpendSharePercent = sharePercent(primarySpend, totalSpend),
                primaryGain = primaryGain,
                primaryGainSharePercent = sharePercent(primaryGain, totalGain),
                secondaryOccurrenceCount = inRange.count { entry ->
                    entry.secondaryDimensions.any { it.id == dimension.id }
                },
            )
        }

        val byTimeSlot = TimeSlot.entries.map { timeSlot ->
            val slotEntries = inRange.filter { it.timeSlot == timeSlot }
            val gain = slotEntries.filter { it.direction == Direction.GAIN }.sumOf { it.energyValue }
            val spend = slotEntries.filter { it.direction == Direction.SPEND }.sumOf { it.energyValue }
            TimeSlotImpactSummary(
                timeSlot = timeSlot,
                totalGain = gain,
                totalSpend = spend,
                netBalance = gain - spend,
            )
        }

        return PeriodInsights(
            startDate = startDate,
            endDate = endDate,
            activeDaysCount = activeDaysCount,
            totalGain = totalGain,
            totalSpend = totalSpend,
            netBalance = netBalance,
            averageDailyNet = averageDailyNet,
            byDimension = byDimension,
            byTimeSlot = byTimeSlot,
        )
    }

    private fun sharePercent(part: Int, total: Int): Int =
        if (total == 0 || part == 0) 0 else (part * 100.0 / total).roundToInt()
}
