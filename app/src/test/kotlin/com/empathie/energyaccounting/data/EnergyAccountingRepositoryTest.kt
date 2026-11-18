package com.empathie.energyaccounting.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.repository.EnergyAccountingRepository
import com.empathie.energyaccounting.domain.model.ActivityTemplate
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.Entry
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

@RunWith(RobolectricTestRunner::class)
class EnergyAccountingRepositoryTest {
    private lateinit var db: EnergyAccountingDatabase
    private lateinit var repo: EnergyAccountingRepository

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EnergyAccountingDatabase::class.java
        ).allowMainThreadQueries().build()
        db.ensureSeeded()
        repo = EnergyAccountingRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertEntry(date: LocalDate, name: String, createdAt: Long, slot: TimeSlot = TimeSlot.LATE_MORNING): Long {
        val dimension = repo.observeActiveDimensions().first().first()
        return repo.saveEntry(
            Entry(
                date = date,
                timeSlot = slot,
                activity = name,
                situationalNote = "note $name",
                direction = Direction.SPEND,
                energyValue = 10,
                dimension = dimension,
                createdAtEpochMillis = createdAt,
            )
        )
    }

    private suspend fun move(date: LocalDate, id: Long, slot: TimeSlot, index: Int): Boolean =
        repo.moveEntry(date, id, slot, index)

    @Test
    fun `moving entries inserts within and across slots without changing their content or templates`() = runTest {
        val date = LocalDate.of(2026, 11, 12)
        val a = insertEntry(date, "A", 100L)
        insertEntry(date, "B", 200L)
        val c = insertEntry(date, "C", 300L)
        val before = repo.observeDailyAccount(date).first()
        val originalA = before.ledgerFor(TimeSlot.LATE_MORNING).entries.first { it.id == a }
        val templates = repo.observeTemplates().first()

        assertTrue(move(date, c, TimeSlot.LATE_MORNING, 0))
        assertEquals(listOf("C", "A", "B"), repo.observeDailyAccount(date).first()
            .ledgerFor(TimeSlot.LATE_MORNING).entries.map { it.activity })
        assertTrue(move(date, a, TimeSlot.AFTERNOON, 0))

        val after = repo.observeDailyAccount(date).first()
        assertEquals(listOf("C", "B"), after.ledgerFor(TimeSlot.LATE_MORNING).entries.map { it.activity })
        assertEquals(listOf("A"), after.ledgerFor(TimeSlot.AFTERNOON).entries.map { it.activity })
        assertEquals(originalA.copy(timeSlot = TimeSlot.AFTERNOON, positionInSlot = 0),
            after.ledgerFor(TimeSlot.AFTERNOON).entries.single())
        assertEquals(-20, after.ledgerFor(TimeSlot.LATE_MORNING).netBalance)
        assertEquals(-10, after.ledgerFor(TimeSlot.AFTERNOON).netBalance)
        assertEquals(templates, repo.observeTemplates().first())
    }

    @Test
    fun `invalid and no-op moves do not change the ledger`() = runTest {
        val date = LocalDate.of(2026, 11, 12)
        val a = insertEntry(date, "A", 100L)
        insertEntry(date, "B", 200L)
        val before = repo.observeDailyAccount(date).first()

        assertFalse(move(date, 9999L, TimeSlot.AFTERNOON, 0))
        assertFalse(move(date.plusDays(1), a, TimeSlot.AFTERNOON, 0))
        assertFalse(move(date, a, TimeSlot.AFTERNOON, 1))
        assertFalse(move(date, a, TimeSlot.LATE_MORNING, -1))
        assertTrue(move(date, a, TimeSlot.LATE_MORNING, 0))
        assertEquals(before, repo.observeDailyAccount(date).first())
    }

    @Test
    fun `editing a reordered entry preserves its position and changing its slot appends it`() = runTest {
        val date = LocalDate.of(2026, 11, 12)
        val a = insertEntry(date, "A", 100L)
        val b = insertEntry(date, "B", 200L)
        insertEntry(date, "D", 50L, TimeSlot.AFTERNOON)
        assertTrue(move(date, b, TimeSlot.LATE_MORNING, 0))

        val reordered = repo.observeDailyAccount(date).first().ledgerFor(TimeSlot.LATE_MORNING).entries
        repo.saveEntry(reordered.first { it.id == b }.copy(situationalNote = "edited"))
        assertEquals(listOf("B", "A"), repo.observeDailyAccount(date).first()
            .ledgerFor(TimeSlot.LATE_MORNING).entries.map { it.activity })

        val entryA = repo.observeDailyAccount(date).first().ledgerFor(TimeSlot.LATE_MORNING).entries.first { it.id == a }
        repo.saveEntry(entryA.copy(timeSlot = TimeSlot.AFTERNOON))
        val afternoon = repo.observeDailyAccount(date).first().ledgerFor(TimeSlot.AFTERNOON).entries
        assertEquals(listOf("D", "A"), afternoon.map { it.activity })
        assertEquals(100L, afternoon.last().createdAtEpochMillis)
    }

    @Test
    fun `new entries append within a slot even when their creation timestamps are out of order`() = runTest {
        val date = LocalDate.of(2026, 11, 12)
        val dimension = repo.observeActiveDimensions().first().first()
        suspend fun insert(activity: String, createdAt: Long) = repo.saveEntry(
            Entry(
                date = date,
                timeSlot = TimeSlot.LATE_MORNING,
                activity = activity,
                situationalNote = null,
                direction = Direction.SPEND,
                energyValue = 20,
                dimension = dimension,
                createdAtEpochMillis = createdAt,
            )
        )

        insert("A", 2000L)
        insert("B", 1000L)

        val entries = repo.observeDailyAccount(date).first().ledgerFor(TimeSlot.LATE_MORNING).entries
        assertEquals(listOf("A", "B"), entries.map { it.activity })
    }

    @Test
    fun `saveEntry auto-creates template and preserves baseline on case-insensitive reuse unless updateTemplateDefaults is true`() = runTest {
        val dims = repo.observeActiveDimensions().first()
        val cognitive = dims.first { it.name == "Cognitive" }
        val sensory = dims.first { it.name == "Sensory" }
        val today = LocalDate.of(2026, 11, 12)

        repo.saveEntry(
            Entry(
                date = today,
                timeSlot = TimeSlot.EARLY_MORNING,
                activity = "Morning routine",
                situationalNote = null,
                direction = Direction.SPEND,
                energyValue = 30,
                dimension = cognitive,
                createdAtEpochMillis = 1000L
            ),
            updateTemplateDefaults = false
        )

        repo.saveEntry(
            Entry(
                date = today.plusDays(1),
                timeSlot = TimeSlot.EARLY_MORNING,
                activity = "  MORNING ROUTINE ",
                situationalNote = "Poor sleep",
                direction = Direction.SPEND,
                energyValue = 60,
                dimension = sensory,
                createdAtEpochMillis = 2000L
            ),
            updateTemplateDefaults = false
        )

        val templatesAfterIsolatedTweaks = repo.observeTemplates().first()
        assertEquals(1, templatesAfterIsolatedTweaks.size)
        val tpl = templatesAfterIsolatedTweaks.first()
        assertEquals("Morning routine", tpl.activityName)
        assertEquals(30, tpl.defaultEnergyValue)
        assertEquals(cognitive.id, tpl.defaultDimension.id)
        assertEquals(2, tpl.usageCount)

        repo.saveEntry(
            Entry(
                date = today.plusDays(2),
                timeSlot = TimeSlot.EARLY_MORNING,
                activity = "morning routine",
                situationalNote = null,
                direction = Direction.SPEND,
                energyValue = 50,
                dimension = sensory,
                createdAtEpochMillis = 3000L
            ),
            updateTemplateDefaults = true
        )

        val updatedTpl = repo.observeTemplates().first().first()
        assertEquals(50, updatedTpl.defaultEnergyValue)
        assertEquals(sensory.id, updatedTpl.defaultDimension.id)
        assertEquals(3, updatedTpl.usageCount)
    }

    @Test
    fun `removeOrArchiveCustomDimension soft-archives used custom dimension and hard-deletes unused custom dimension`() = runTest {
        val usedId = repo.createCustomDimension("Executive Function", 3)
        val unusedId = repo.createCustomDimension("Temporary Tag", 4)
        val usedDim = repo.observeAllDimensions().first().first { it.id == usedId }

        repo.saveEntry(
            Entry(
                date = LocalDate.of(2026, 11, 12),
                timeSlot = TimeSlot.AFTERNOON,
                activity = "Tax paperwork",
                situationalNote = null,
                direction = Direction.SPEND,
                energyValue = 80,
                dimension = usedDim,
                createdAtEpochMillis = 1000L
            )
        )

        repo.removeOrArchiveCustomDimension(usedId)
        repo.removeOrArchiveCustomDimension(unusedId)

        val activeDims = repo.observeActiveDimensions().first()
        val allDims = repo.observeAllDimensions().first()
        assertFalse(activeDims.any { it.id == usedId })
        assertTrue(allDims.any { it.id == usedId && it.isArchived })
        assertFalse(allDims.any { it.id == unusedId })

        val account = repo.observeDailyAccount(LocalDate.of(2026, 11, 12)).first()
        assertEquals(80, account.totalSpend)
        assertEquals(
            "Executive Function",
            account.slots.getValue(TimeSlot.AFTERNOON).entries.first().dimension.name
        )

        val template = repo.observeTemplates().first().first { it.activityName == "Tax paperwork" }
        assertEquals("Executive Function", template.defaultDimension.name)
    }

    @Test
    fun `saveTemplate rejects a duplicate normalized name with a domain error`() = runTest {
        val dims = repo.observeActiveDimensions().first()
        val cognitive = dims.first { it.name == "Cognitive" }
        val sensory = dims.first { it.name == "Sensory" }

        repo.saveEntry(
            Entry(
                date = LocalDate.of(2026, 11, 12),
                timeSlot = TimeSlot.EARLY_MORNING,
                activity = "Morning routine",
                situationalNote = null,
                direction = Direction.SPEND,
                energyValue = 30,
                dimension = cognitive,
                createdAtEpochMillis = 1000L,
            )
        )

        val duplicate = ActivityTemplate(
            activityName = "  MORNING ROUTINE ",
            defaultDirection = Direction.GAIN,
            defaultEnergyValue = 10,
            defaultDimension = sensory,
        )

        val result = runCatching { repo.saveTemplate(duplicate) }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        val templates = repo.observeTemplates().first()
        assertEquals(1, templates.size)
        assertEquals(30, templates.first().defaultEnergyValue)
        assertEquals(cognitive.id, templates.first().defaultDimension.id)
    }

    @Test
    fun `createCustomDimension rejects duplicate names case-insensitively`() = runTest {
        val resultSeeded = runCatching { repo.createCustomDimension("  physical ", 1) }
        assertTrue(resultSeeded.exceptionOrNull() is IllegalArgumentException)

        repo.createCustomDimension("Focus", 2)
        val resultCustom = runCatching { repo.createCustomDimension("FOCUS", 3) }
        assertTrue(resultCustom.exceptionOrNull() is IllegalArgumentException)

        val focusCount = repo.observeAllDimensions().first().count { it.name.equals("Focus", ignoreCase = true) }
        assertEquals(1, focusCount)
    }

}
