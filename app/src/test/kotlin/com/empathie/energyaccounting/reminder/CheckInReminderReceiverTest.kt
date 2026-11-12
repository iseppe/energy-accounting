package com.empathie.energyaccounting.reminder

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.local.entity.EntryEntity
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.TimeSlot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class CheckInReminderReceiverTest {
    private lateinit var db: EnergyAccountingDatabase

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EnergyAccountingDatabase::class.java
        ).allowMainThreadQueries().build()
        db.ensureSeeded()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `shouldPostNotification returns true when slot is empty and false when slot already has an entry today`() = runTest {
        val today = LocalDate.of(2026, 11, 12)
        assertTrue(CheckInReminderReceiver.shouldPostNotification(db.dao(), today, TimeSlot.EVENING))

        val dim = db.dao().observeAllDimensions().first().first()
        db.dao().insertEntryWithSecondaries(
            EntryEntity(
                dateIso = today.toString(),
                timeSlot = TimeSlot.EVENING.name,
                activity = "Dinner with family",
                situationalNote = null,
                direction = Direction.SPEND.name,
                energyValue = 10,
                primaryDimensionId = dim.id,
                createdAtEpochMillis = 100L
            ),
            emptyList()
        )

        assertFalse(CheckInReminderReceiver.shouldPostNotification(db.dao(), today, TimeSlot.EVENING))
        assertTrue(CheckInReminderReceiver.shouldPostNotification(db.dao(), today, TimeSlot.AFTERNOON))
    }

    @Test
    fun `computeNextTriggerMillis schedules today if time is in future or tomorrow if time already passed`() {
        val scheduler = ReminderScheduler(ApplicationProvider.getApplicationContext())
        val now = ZonedDateTime.of(2026, 11, 12, 14, 0, 0, 0, ZoneId.of("Europe/Rome"))
        val todayAt1730 = scheduler.computeNextTriggerMillis(17, 30, now)
        val tomorrowAt0930 = scheduler.computeNextTriggerMillis(9, 30, now)

        assertEquals(now.withHour(17).withMinute(30).toInstant().toEpochMilli(), todayAt1730)
        assertEquals(now.plusDays(1).withHour(9).withMinute(30).toInstant().toEpochMilli(), tomorrowAt0930)
    }
}
