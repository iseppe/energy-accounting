package com.empathie.energyaccounting.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.local.entity.EntryEntity
import com.empathie.energyaccounting.data.local.entity.EntrySecondaryDimensionCrossRef
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

@RunWith(RobolectricTestRunner::class)
class EnergyAccountingDaoTest {
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
    fun `ensureSeeded inserts 5 default dimensions and 4 reminder schedules idempotently`() = runTest {
        db.ensureSeeded()
        val dimensions = db.dao().observeAllDimensions().first()
        assertEquals(5, dimensions.size)
        val reminders = db.dao().observeReminderSchedules().first()
        assertEquals(4, reminders.size)
    }

    @Test
    fun `inserting entry with secondary dimensions loads EntryWithDimensions and counts slot entries`() = runTest {
        val dims = db.dao().observeAllDimensions().first()
        val sensory = dims.first { it.systemKey == "SENSORY" }
        val social = dims.first { it.systemKey == "SOCIAL" }

        val entryId = db.dao().insertEntryWithSecondaries(
            entry = EntryEntity(
                dateIso = "2026-11-12",
                timeSlot = TimeSlot.LATE_MORNING.name,
                activity = "Team meeting",
                situationalNote = "Loud room",
                direction = Direction.SPEND.name,
                energyValue = 70,
                primaryDimensionId = sensory.id,
                createdAtEpochMillis = 100L
            ),
            secondaryDimensionIds = listOf(social.id)
        )

        val loaded = db.dao().observeEntriesForDate("2026-11-12").first()
        assertEquals(1, loaded.size)
        assertEquals(entryId, loaded.first().entry.id)
        assertEquals("SENSORY", loaded.first().primaryDimension.systemKey)
        assertEquals(listOf("SOCIAL"), loaded.first().secondaryDimensions.map { it.systemKey })
        assertEquals(1, db.dao().countEntriesForSlot("2026-11-12", TimeSlot.LATE_MORNING.name))
        assertEquals(0, db.dao().countEntriesForSlot("2026-11-12", TimeSlot.EVENING.name))
    }
}
