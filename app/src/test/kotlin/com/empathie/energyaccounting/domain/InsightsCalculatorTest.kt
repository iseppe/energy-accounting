package com.empathie.energyaccounting.domain

import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.EnergyDimension
import com.empathie.energyaccounting.domain.model.Entry
import com.empathie.energyaccounting.domain.model.SystemDimensionKey
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.domain.service.InsightsCalculator
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class InsightsCalculatorTest {
    private val physical = EnergyDimension(1L, "Physical", SystemDimensionKey.PHYSICAL, 0)
    private val cognitive = EnergyDimension(2L, "Cognitive", SystemDimensionKey.COGNITIVE, 1)
    private val sensory = EnergyDimension(3L, "Sensory", SystemDimensionKey.SENSORY, 2)
    private val social = EnergyDimension(4L, "Social", SystemDimensionKey.SOCIAL, 3)
    private val emotional = EnergyDimension(5L, "Emotional", SystemDimensionKey.EMOTIONAL, 4)

    private val dimensions = listOf(physical, cognitive, sensory, social, emotional)
    private val start = LocalDate.of(2026, 11, 1)
    private val end = LocalDate.of(2026, 11, 30)

    @Test
    fun `primary dimension carries the full energy value while secondary only counts occurrences`() {
        val entries = listOf(
            Entry(
                1L, LocalDate.of(2026, 11, 5), TimeSlot.AFTERNOON, "Team meeting",
                "Loud room", Direction.SPEND, 60, sensory, listOf(social), 1L
            ),
            Entry(
                2L, LocalDate.of(2026, 11, 5), TimeSlot.EVENING, "Quiet time",
                null, Direction.GAIN, 20, sensory, emptyList(), 2L
            ),
            Entry(
                3L, LocalDate.of(2026, 11, 6), TimeSlot.LATE_MORNING, "Homework",
                null, Direction.SPEND, 40, cognitive, listOf(sensory, social), 3L
            ),
            Entry(
                4L, LocalDate.of(2026, 12, 1), TimeSlot.EARLY_MORNING, "Out of range",
                null, Direction.SPEND, 90, physical, emptyList(), 4L
            ),
        )

        val insights = InsightsCalculator.calculate(start, end, entries, dimensions)

        assertEquals(20, insights.totalGain)
        assertEquals(100, insights.totalSpend)
        assertEquals(-80, insights.netBalance)
        assertEquals(2, insights.activeDaysCount)
        assertEquals(-40, insights.averageDailyNet)

        val sensorySummary = insights.byDimension.first { it.dimension.id == sensory.id }
        assertEquals(60, sensorySummary.primarySpend)
        assertEquals(60, sensorySummary.primarySpendSharePercent)
        assertEquals(20, sensorySummary.primaryGain)
        assertEquals(100, sensorySummary.primaryGainSharePercent)
        assertEquals(1, sensorySummary.secondaryOccurrenceCount)

        val cognitiveSummary = insights.byDimension.first { it.dimension.id == cognitive.id }
        assertEquals(40, cognitiveSummary.primarySpend)
        assertEquals(40, cognitiveSummary.primarySpendSharePercent)
        assertEquals(0, cognitiveSummary.primaryGain)
        assertEquals(0, cognitiveSummary.primaryGainSharePercent)
        assertEquals(0, cognitiveSummary.secondaryOccurrenceCount)

        val socialSummary = insights.byDimension.first { it.dimension.id == social.id }
        assertEquals(0, socialSummary.primarySpend)
        assertEquals(0, socialSummary.primaryGain)
        assertEquals(2, socialSummary.secondaryOccurrenceCount)

        val physicalSummary = insights.byDimension.first { it.dimension.id == physical.id }
        assertEquals(0, physicalSummary.primarySpend)
        assertEquals(0, physicalSummary.secondaryOccurrenceCount)
    }

    @Test
    fun `time slot breakdown sums back to the period totals`() {
        val entries = listOf(
            Entry(1L, LocalDate.of(2026, 11, 5), TimeSlot.EARLY_MORNING, "Routine", null, Direction.SPEND, 30, cognitive, emptyList(), 1L),
            Entry(2L, LocalDate.of(2026, 11, 5), TimeSlot.AFTERNOON, "Walk", null, Direction.GAIN, 40, physical, emptyList(), 2L),
            Entry(3L, LocalDate.of(2026, 11, 6), TimeSlot.AFTERNOON, "Meeting", null, Direction.SPEND, 50, sensory, emptyList(), 3L),
            Entry(4L, LocalDate.of(2026, 11, 6), TimeSlot.EVENING, "Music", null, Direction.GAIN, 10, emotional, emptyList(), 4L),
        )

        val insights = InsightsCalculator.calculate(start, end, entries, dimensions)

        assertEquals(TimeSlot.entries.size, insights.byTimeSlot.size)
        assertEquals(insights.totalGain, insights.byTimeSlot.sumOf { it.totalGain })
        assertEquals(insights.totalSpend, insights.byTimeSlot.sumOf { it.totalSpend })
        assertEquals(insights.netBalance, insights.byTimeSlot.sumOf { it.netBalance })

        val afternoon = insights.byTimeSlot.first { it.timeSlot == TimeSlot.AFTERNOON }
        assertEquals(40, afternoon.totalGain)
        assertEquals(50, afternoon.totalSpend)
        assertEquals(-10, afternoon.netBalance)
    }

    @Test
    fun `empty period returns zero totals and zero shares without dividing by zero`() {
        val insights = InsightsCalculator.calculate(start, end, emptyList(), dimensions)

        assertEquals(0, insights.totalGain)
        assertEquals(0, insights.totalSpend)
        assertEquals(0, insights.netBalance)
        assertEquals(0, insights.activeDaysCount)
        assertEquals(0, insights.averageDailyNet)
        assertTrue(insights.byDimension.all { it.primarySpendSharePercent == 0 })
        assertTrue(insights.byDimension.all { it.primaryGainSharePercent == 0 })
    }
}
