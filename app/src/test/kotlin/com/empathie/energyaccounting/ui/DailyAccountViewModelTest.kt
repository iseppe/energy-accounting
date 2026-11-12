package com.empathie.energyaccounting.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.repository.EnergyAccountingRepository
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.ui.daily.DailyAccountViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
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
        assertEquals(TimeSlot.AFTERNOON, draft.timeSlot) // 14:15 UTC -> AFTERNOON

        vm.onActivityChange("Quiet walk")
        vm.onDirectionChange(Direction.GAIN)
        vm.onEnergyValueChange(40)
        vm.onTapDimension(dims.first().id)
        vm.saveDraft()

        assertNull(vm.draftState.value)
        val account = repo.observeDailyAccount(LocalDate.of(2026, 11, 12)).first()
        assertEquals(40, account.totalGain)
        assertEquals(40, account.netBalance)
    }
}
