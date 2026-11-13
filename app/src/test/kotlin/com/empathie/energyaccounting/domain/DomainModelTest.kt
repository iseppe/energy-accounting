package com.empathie.energyaccounting.domain

import com.empathie.energyaccounting.domain.model.*
import com.empathie.energyaccounting.domain.service.EnergyFormatter
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DomainModelTest {
    private val sensory = EnergyDimension(1L, "Sensory", SystemDimensionKey.SENSORY, 0)
    private val social = EnergyDimension(2L, "Social", SystemDimensionKey.SOCIAL, 1)
    private val cognitive = EnergyDimension(3L, "Cognitive", SystemDimensionKey.COGNITIVE, 2)

    @Test
    fun `formatDate and parseDate strictly use dd_MM_yyyy`() {
        val date = LocalDate.of(2026, 11, 12)
        assertEquals("12/11/2026", EnergyFormatter.formatDate(date))
        assertEquals(date, EnergyFormatter.parseDate("12/11/2026"))
    }

    @Test
    fun `TimeSlot defaultForTime maps clock boundaries accurately`() {
        assertEquals(TimeSlot.EARLY_MORNING, TimeSlot.defaultForTime(LocalTime.of(5, 0)))
        assertEquals(TimeSlot.EARLY_MORNING, TimeSlot.defaultForTime(LocalTime.of(9, 59)))
        assertEquals(TimeSlot.LATE_MORNING, TimeSlot.defaultForTime(LocalTime.of(10, 0)))
        assertEquals(TimeSlot.LATE_MORNING, TimeSlot.defaultForTime(LocalTime.of(12, 59)))
        assertEquals(TimeSlot.AFTERNOON, TimeSlot.defaultForTime(LocalTime.of(13, 0)))
        assertEquals(TimeSlot.AFTERNOON, TimeSlot.defaultForTime(LocalTime.of(17, 59)))
        assertEquals(TimeSlot.EVENING, TimeSlot.defaultForTime(LocalTime.of(18, 0)))
        assertEquals(TimeSlot.EVENING, TimeSlot.defaultForTime(LocalTime.of(2, 30)))
    }

    @Test
    fun `Entry holds a single dimension and normalizes activity and situationalNote`() {
        val entry = Entry(
            date = LocalDate.of(2026, 11, 12),
            timeSlot = TimeSlot.LATE_MORNING,
            activity = "  Lunch in cafeteria  ",
            situationalNote = "   ",
            direction = Direction.SPEND,
            energyValue = 50,
            dimension = sensory,
            createdAtEpochMillis = 1000L
        )
        assertEquals("Lunch in cafeteria", entry.activity)
        assertNull(entry.situationalNote)
        assertEquals(sensory, entry.dimension)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Entry rejects energyValue outside 1 to 100`() {
        Entry(
            date = LocalDate.of(2026, 11, 12),
            timeSlot = TimeSlot.EARLY_MORNING,
            activity = "Invalid value",
            situationalNote = null,
            direction = Direction.SPEND,
            energyValue = 0,
            dimension = sensory,
            createdAtEpochMillis = 1000L
        )
    }

    @Test
    fun `ActivityTemplate holds a single default dimension and normalizes its name`() {
        val template = ActivityTemplate(
            activityName = "  Morning routine  ",
            defaultDirection = Direction.SPEND,
            defaultEnergyValue = 30,
            defaultDimension = cognitive,
        )
        assertEquals("Morning routine", template.activityName)
        assertEquals("morning routine", template.normalizedName)
        assertEquals(cognitive, template.defaultDimension)
    }

    @Test
    fun `DailyAccount aggregates zero-based gain, spend, and net balance per slot and day`() {
        val date = LocalDate.of(2026, 11, 12)
        val entries = listOf(
            Entry(1L, date, TimeSlot.EARLY_MORNING, "Morning routine", null, Direction.SPEND, 30, cognitive, 10L),
            Entry(2L, date, TimeSlot.EARLY_MORNING, "Favourite breakfast", null, Direction.GAIN, 10, sensory, 20L),
            Entry(3L, date, TimeSlot.AFTERNOON, "Quiet room", null, Direction.GAIN, 50, sensory, 30L)
        )
        val account = DailyAccount.fromEntries(date, entries)
        assertEquals(60, account.totalGain)
        assertEquals(30, account.totalSpend)
        assertEquals(30, account.netBalance)
        assertEquals(-20, account.slots.getValue(TimeSlot.EARLY_MORNING).netBalance)
        assertEquals(0, account.slots.getValue(TimeSlot.LATE_MORNING).netBalance)
        assertEquals(50, account.slots.getValue(TimeSlot.AFTERNOON).netBalance)
        assertEquals(sensory, account.slots.getValue(TimeSlot.AFTERNOON).entries.first().dimension)
    }

    @Test
    fun `EnergyFormatter formats zero gain and spend without plus or minus signs`() {
        assertEquals("0", EnergyFormatter.formatGainTotal(0))
        assertEquals("+20", EnergyFormatter.formatGainTotal(20))
        assertEquals("0", EnergyFormatter.formatSpendTotal(0))
        assertEquals("-35", EnergyFormatter.formatSpendTotal(35))
    }
}
