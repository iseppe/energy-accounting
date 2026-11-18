package com.empathie.energyaccounting.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.viewModelScope
import com.empathie.energyaccounting.R
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.repository.EnergyAccountingRepository
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.Entry
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.ui.insights.InsightsPeriod
import com.empathie.energyaccounting.ui.insights.InsightsScreen
import com.empathie.energyaccounting.ui.insights.InsightsViewModel
import com.empathie.energyaccounting.ui.theme.EmpathieTheme
import com.empathie.energyaccounting.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class InsightsNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: EnergyAccountingDatabase
    private lateinit var vm: InsightsViewModel
    private lateinit var context: Context
    private var showScreen by mutableStateOf(true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        assertEquals("com.empathie.energyaccounting.uitest", context.packageName)
        db = Room.inMemoryDatabaseBuilder(context, EnergyAccountingDatabase::class.java).allowMainThreadQueries().build()
        val repo = EnergyAccountingRepository(db)
        runBlocking {
            db.ensureSeeded()
            val dimension = repo.observeActiveDimensions().first().first()
            listOf(16 to 10, 17 to 20, 18 to 30).forEach { (day, value) ->
                repo.saveEntry(
                    Entry(
                        date = LocalDate.of(2026, 11, day), timeSlot = TimeSlot.LATE_MORNING,
                        activity = "Entry $day", situationalNote = null, direction = Direction.SPEND,
                        energyValue = value, dimension = dimension, createdAtEpochMillis = day.toLong(),
                    )
                )
            }
        }
        vm = InsightsViewModel(repo, Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC))
        compose.setContent { if (showScreen) EmpathieTheme(ThemeMode.LIGHT) { InsightsScreen(vm) } }
    }

    @After
    fun tearDown() {
        compose.runOnIdle { showScreen = false }
        compose.waitForIdle()
        runBlocking {
            vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
            db.withTransaction { }
        }
        db.close()
    }

    @Test
    fun arrowsNavigateSelectedDatesAndRecalculateInsights() {
        val previous = context.getString(R.string.previous_period)
        val next = context.getString(R.string.next_period)
        compose.onNodeWithContentDescription(previous).assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { vm.insights.value.startDate == LocalDate.of(2026, 11, 16) }
        compose.onNodeWithText("16/11/2026 – 16/11/2026").assertIsDisplayed()
        assertEquals(10, vm.insights.value.totalSpend)
        compose.onNodeWithContentDescription(next).performClick()
        compose.onNodeWithContentDescription(next).performClick()
        compose.waitUntil(5_000) { vm.insights.value.startDate == LocalDate.of(2026, 11, 18) }
        assertEquals(30, vm.insights.value.totalSpend)
    }

    @Test
    fun customRangeHasNoNavigationArrows() {
        compose.runOnIdle { vm.selectPeriod(InsightsPeriod.CUSTOM) }
        compose.onNodeWithContentDescription(context.getString(R.string.previous_period)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.next_period)).assertDoesNotExist()
    }

    @Test
    fun retainedDimensionCanBeClearedWhenTheNewRangeHasNoEntries() {
        compose.runOnIdle {
            vm.toggleDimensionFilter(1L)
            vm.goToPreviousPeriod()
            vm.goToPreviousPeriod()
        }
        compose.onNodeWithText(context.getString(R.string.clear_dimension_filter)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertNull(vm.state.value.selectedDimensionId) }
    }
}
