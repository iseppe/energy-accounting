package com.empathie.energyaccounting.ui

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.viewModelScope
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.repository.EnergyAccountingRepository
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.Entry
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.ui.insights.InsightsViewModel
import com.empathie.energyaccounting.ui.insights.InsightsPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class InsightsViewModelTest {
    private lateinit var db: EnergyAccountingDatabase
    private lateinit var repo: EnergyAccountingRepository
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC)
    private val viewModels = mutableListOf<InsightsViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        runTest {
            db = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(), EnergyAccountingDatabase::class.java
            ).allowMainThreadQueries().build()
            db.ensureSeeded()
            repo = EnergyAccountingRepository(db)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            viewModels.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
            db.withTransaction { }
        }
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(clock: Clock): InsightsViewModel =
        InsightsViewModel(repo, clock).also { viewModels += it }

    @Test
    fun `dimension-filtered entries follow the manually selected slot order`() = runTest {
        val date = LocalDate.of(2026, 11, 12)
        val dimension = repo.observeActiveDimensions().first().first()
        suspend fun insert(name: String, createdAt: Long) = repo.saveEntry(
            Entry(
                date = date, timeSlot = TimeSlot.LATE_MORNING, activity = name,
                situationalNote = null, direction = Direction.SPEND, energyValue = 10,
                dimension = dimension, createdAtEpochMillis = createdAt,
            )
        )
        insert("A", 100L)
        insert("B", 200L)
        val c = insert("C", 300L)
        assertTrue(repo.moveEntry(date, c, TimeSlot.LATE_MORNING, 0))

        val vm = viewModel(clock)
        vm.toggleDimensionFilter(dimension.id)
        val entries = withContext(Dispatchers.Default) {
            withTimeout(5_000) { vm.filteredEntries.first { it.size == 3 } }
        }
        assertEquals(listOf("C", "A", "B"), entries.map { it.activity })
    }

    @Test
    fun `daily navigation includes past and future dates while retaining the selected dimension`() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC)
        val vm = viewModel(clock)
        vm.toggleDimensionFilter(42L)
        vm.goToPreviousPeriod()
        assertEquals(LocalDate.of(2026, 11, 16), vm.state.value.startDate)
        assertEquals(LocalDate.of(2026, 11, 16), vm.state.value.endDate)
        assertEquals(42L, vm.state.value.selectedDimensionId)
        vm.goToNextPeriod()
        vm.goToNextPeriod()
        assertEquals(LocalDate.of(2026, 11, 18), vm.state.value.startDate)
        assertEquals(LocalDate.of(2026, 11, 18), vm.state.value.endDate)
    }

    @Test
    fun `weekly and thirty-day navigation shifts whole ranges and changing presets resets today`() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC)
        val vm = viewModel(clock)
        vm.selectPeriod(InsightsPeriod.LAST_7_DAYS)
        assertEquals(LocalDate.of(2026, 11, 11), vm.state.value.startDate)
        vm.goToPreviousPeriod()
        assertEquals(LocalDate.of(2026, 11, 4), vm.state.value.startDate)
        assertEquals(LocalDate.of(2026, 11, 10), vm.state.value.endDate)
        vm.goToPreviousPeriod()
        assertEquals(LocalDate.of(2026, 10, 28), vm.state.value.startDate)
        assertEquals(LocalDate.of(2026, 11, 3), vm.state.value.endDate)

        vm.selectPeriod(InsightsPeriod.LAST_30_DAYS)
        assertEquals(LocalDate.of(2026, 10, 19), vm.state.value.startDate)
        assertEquals(LocalDate.of(2026, 11, 17), vm.state.value.endDate)
        vm.goToNextPeriod()
        assertEquals(LocalDate.of(2026, 11, 18), vm.state.value.startDate)
        assertEquals(LocalDate.of(2026, 12, 17), vm.state.value.endDate)
        vm.selectPeriod(InsightsPeriod.LAST_30_DAYS)
        assertEquals(LocalDate.of(2026, 11, 17), vm.state.value.endDate)
    }

    @Test
    fun `navigation crosses years and leaves a custom range unchanged`() = runTest {
        val clock = Clock.fixed(Instant.parse("2027-01-01T12:00:00Z"), ZoneOffset.UTC)
        val vm = viewModel(clock)
        vm.selectPeriod(InsightsPeriod.LAST_7_DAYS)
        vm.goToPreviousPeriod()
        assertEquals(LocalDate.of(2026, 12, 19), vm.state.value.startDate)
        assertEquals(LocalDate.of(2026, 12, 25), vm.state.value.endDate)
        vm.selectPeriod(InsightsPeriod.CUSTOM)
        val custom = vm.state.value
        vm.goToPreviousPeriod()
        vm.goToNextPeriod()
        assertEquals(custom, vm.state.value)
    }
}
