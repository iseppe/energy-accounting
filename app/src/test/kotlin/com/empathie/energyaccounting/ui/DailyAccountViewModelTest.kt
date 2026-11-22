package com.empathie.energyaccounting.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.empathie.energyaccounting.data.diagnostics.AdjustableClock
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.repository.EnergyAccountingRepository
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.Entry
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.ui.daily.DailyAccountViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class DailyAccountViewModelTest {
    private lateinit var db: EnergyAccountingDatabase
    private lateinit var repo: EnergyAccountingRepository
    private val fixedClock = Clock.fixed(Instant.parse("2026-11-12T14:15:00Z"), ZoneId.of("UTC"))

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

    @Test
    fun `openNewEntrySheet on Today preselects TimeSlot from clock and saving updates DailyAccount`() = runTest {
        val vm = DailyAccountViewModel(repo, fixedClock)
        val dims = repo.observeActiveDimensions().first()
        vm.openNewEntrySheet(preferredSlot = null)

        val draft = vm.draftState.value!!
        assertEquals(TimeSlot.AFTERNOON, draft.timeSlot)

        vm.onActivityChange("Quiet walk")
        vm.onDirectionChange(Direction.GAIN)
        vm.onEnergyValueChange(40)
        vm.onSelectDimension(dims.first().id)
        vm.saveDraft()

        assertNull(vm.draftState.value)
        val account = repo.observeDailyAccount(LocalDate.of(2026, 11, 12)).first()
        assertEquals(40, account.totalGain)
        assertEquals(40, account.netBalance)
    }

    @Test
    fun `onSelectDimension switches the single selected dimension in one tap`() = runTest {
        val vm = DailyAccountViewModel(repo, fixedClock)
        val dims = repo.observeActiveDimensions().first()
        vm.openNewEntrySheet(TimeSlot.EARLY_MORNING)

        vm.onSelectDimension(dims[0].id)
        assertEquals(dims[0].id, vm.draftState.value!!.selectedDimensionId)

        vm.onSelectDimension(dims[1].id)
        assertEquals(dims[1].id, vm.draftState.value!!.selectedDimensionId)
    }

    @Test
    fun `saving without a dimension keeps the draft open and inserts nothing`() = runTest {
        val vm = DailyAccountViewModel(repo, fixedClock)
        vm.openNewEntrySheet(preferredSlot = TimeSlot.EVENING)
        vm.onActivityChange("Unknown activity")

        vm.saveDraft()

        assertNotNull(vm.draftState.value)
        val account = repo.observeDailyAccount(LocalDate.of(2026, 11, 12)).first()
        assertEquals(0, account.slots.getValue(TimeSlot.EVENING).entries.size)
    }

    @Test
    fun `requestNewEntrySheet on a future date asks for confirmation before opening the sheet`() = runTest {
        val vm = DailyAccountViewModel(repo, fixedClock)
        vm.selectDate(LocalDate.of(2026, 11, 14))

        vm.requestNewEntrySheet(TimeSlot.AFTERNOON)

        assertNull(vm.draftState.value)
        assertEquals(TimeSlot.AFTERNOON, vm.pendingFutureSlotWarning.value)

        vm.confirmFutureEntryWarning()

        assertNull(vm.pendingFutureSlotWarning.value)
        assertEquals(TimeSlot.AFTERNOON, vm.draftState.value!!.timeSlot)
    }

    @Test
    fun `editing an entry preserves its archived dimension`() = runTest {
        val date = LocalDate.of(2026, 11, 12)
        val customId = repo.createCustomDimension("Focus", 3)
        val custom = repo.observeAllDimensions().first().first { it.id == customId }
        repo.saveEntry(
            Entry(
                date = date,
                timeSlot = TimeSlot.AFTERNOON,
                activity = "Deep work",
                situationalNote = null,
                direction = Direction.SPEND,
                energyValue = 60,
                dimension = custom,
                createdAtEpochMillis = 1000L,
            )
        )
        repo.removeOrArchiveCustomDimension(customId)

        val vm = DailyAccountViewModel(repo, fixedClock)
        vm.selectDate(date)
        val entry = repo.observeDailyAccount(date).first()
            .slots.getValue(TimeSlot.AFTERNOON).entries.first()

        vm.openEditEntrySheet(entry)
        vm.onSituationalNoteChange("Note only")
        vm.saveDraft()

        val saved = repo.observeDailyAccount(date).first()
            .slots.getValue(TimeSlot.AFTERNOON).entries.first()
        assertEquals(customId, saved.dimension.id)
        assertTrue(saved.dimension.isArchived)
        assertEquals("Note only", saved.situationalNote)
    }

    @Test
    fun `editing reordered entries preserves their position or appends to the changed slot`() = runTest {
        val date = LocalDate.of(2026, 11, 12)
        val dimension = repo.observeActiveDimensions().first().first()
        suspend fun insert(name: String, slot: TimeSlot, createdAt: Long) = repo.saveEntry(
            Entry(
                date = date,
                timeSlot = slot,
                activity = name,
                situationalNote = null,
                direction = Direction.SPEND,
                energyValue = 10,
                dimension = dimension,
                createdAtEpochMillis = createdAt,
            )
        )
        val a = insert("A", TimeSlot.LATE_MORNING, 100L)
        val b = insert("B", TimeSlot.LATE_MORNING, 200L)
        insert("D", TimeSlot.AFTERNOON, 50L)
        assertTrue(repo.moveEntry(date, b, TimeSlot.LATE_MORNING, 0))

        val vm = DailyAccountViewModel(repo, fixedClock)
        val reordered = repo.observeDailyAccount(date).first().ledgerFor(TimeSlot.LATE_MORNING).entries
        vm.openEditEntrySheet(reordered.first { it.id == b })
        vm.onSituationalNoteChange("edited")
        vm.saveDraft()
        assertEquals(listOf("B", "A"), repo.observeDailyAccount(date).first()
            .ledgerFor(TimeSlot.LATE_MORNING).entries.map { it.activity })

        val entryA = repo.observeDailyAccount(date).first().ledgerFor(TimeSlot.LATE_MORNING).entries.first { it.id == a }
        vm.openEditEntrySheet(entryA)
        vm.onTimeSlotChange(TimeSlot.AFTERNOON)
        vm.saveDraft()
        val afternoon = repo.observeDailyAccount(date).first().ledgerFor(TimeSlot.AFTERNOON).entries
        assertEquals(listOf("D", "A"), afternoon.map { it.activity })
        assertEquals(100L, afternoon.last().createdAtEpochMillis)
    }

    @Test
    fun `moving entries is restricted to the selected date`() = runTest {
        val today = LocalDate.of(2026, 11, 12)
        val otherDay = today.plusDays(1)
        val dimension = repo.observeActiveDimensions().first().first()
        suspend fun insert(date: LocalDate, name: String) = repo.saveEntry(
            Entry(
                date = date, timeSlot = TimeSlot.LATE_MORNING, activity = name,
                situationalNote = null, direction = Direction.SPEND, energyValue = 10,
                dimension = dimension, createdAtEpochMillis = 100L,
            )
        )
        val todayId = insert(today, "Today")
        val otherId = insert(otherDay, "Tomorrow")
        val vm = DailyAccountViewModel(repo, fixedClock)
        vm.selectDate(otherDay)
        assertFalse(vm.moveEntry(todayId, TimeSlot.AFTERNOON, 0))
        assertTrue(vm.moveEntry(otherId, TimeSlot.AFTERNOON, 0))
        assertEquals(listOf("Today"), repo.observeDailyAccount(today).first()
            .ledgerFor(TimeSlot.LATE_MORNING).entries.map { it.activity })
        assertEquals(listOf("Tomorrow"), repo.observeDailyAccount(otherDay).first()
            .ledgerFor(TimeSlot.AFTERNOON).entries.map { it.activity })
    }

    @Test
    fun `openNewEntrySheet closes an open calendar sheet`() {
        val vm = DailyAccountViewModel(repo, fixedClock)
        vm.openCalendar()
        assertTrue(vm.isCalendarOpen.value)

        vm.openNewEntrySheet()

        assertFalse(vm.isCalendarOpen.value)
    }

    @Test
    fun `concurrent saveDraft calls insert only one entry`() = runTest {
        val vm = DailyAccountViewModel(repo, fixedClock)
        val dimensions = repo.observeActiveDimensions().first()
        vm.openNewEntrySheet(preferredSlot = TimeSlot.AFTERNOON)
        vm.onActivityChange("Quiet walk")
        vm.onEnergyValueChange(40)
        vm.onSelectDimension(dimensions.first().id)

        val first = launch { vm.saveDraft() }
        val second = launch { vm.saveDraft() }
        first.join()
        second.join()

        val entries = repo.observeDailyAccount(LocalDate.of(2026, 11, 12)).first()
            .slots.getValue(TimeSlot.AFTERNOON).entries
        assertEquals(1, entries.size)
    }

    @Test
    fun `fake clock moves the selected day and today`() = runTest {
        val adjustable = AdjustableClock(
            Clock.fixed(Instant.parse("2026-11-13T08:00:00Z"), ZoneId.of("UTC"))
        )
        val vm = DailyAccountViewModel(repo, adjustable)
        assertEquals(LocalDate.of(2026, 11, 13), vm.selectedDate.value)

        adjustable.setFakeDateTime(LocalDate.of(2026, 11, 20), LocalTime.of(9, 29))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(LocalDate.of(2026, 11, 20), vm.selectedDate.value)
        assertEquals(LocalDate.of(2026, 11, 20), vm.todayDate.value)

        adjustable.resetToSystem()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(LocalDate.of(2026, 11, 13), vm.selectedDate.value)
    }
}
