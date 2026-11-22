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
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.viewModelScope
import com.empathie.energyaccounting.R
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.repository.EnergyAccountingRepository
import com.empathie.energyaccounting.ui.insights.InsightsPeriod
import com.empathie.energyaccounting.ui.insights.InsightsScreen
import com.empathie.energyaccounting.ui.insights.InsightsViewModel
import com.empathie.energyaccounting.ui.theme.EmpathieTheme
import com.empathie.energyaccounting.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class InsightsScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: EnergyAccountingDatabase
    private lateinit var vm: InsightsViewModel
    private lateinit var context: Context
    private var showScreen by mutableStateOf(true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, EnergyAccountingDatabase::class.java).allowMainThreadQueries().build()
        runBlocking { db.ensureSeeded() }
        vm = InsightsViewModel(EnergyAccountingRepository(db), Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC))
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
    fun arrowsNavigateSelectedDates() {
        compose.onNodeWithContentDescription(context.getString(R.string.previous_period)).performClick()
        compose.onNodeWithText("16/11/2026 – 16/11/2026").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.next_period)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.next_period)).performClick()
        compose.onNodeWithText("18/11/2026 – 18/11/2026").assertIsDisplayed()
    }

    @Test
    fun customRangeHasNoNavigationArrows() {
        compose.runOnIdle { vm.selectPeriod(InsightsPeriod.CUSTOM) }
        compose.onNodeWithContentDescription(context.getString(R.string.previous_period)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.next_period)).assertDoesNotExist()
    }

    @Test
    fun multiDayPresetsDisplayAndNavigateTheirWholeRanges() {
        compose.onNodeWithText(context.getString(R.string.last_7_days)).performClick()
        compose.onNodeWithText("11/11/2026 – 17/11/2026").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.previous_period)).performClick()
        compose.onNodeWithText("04/11/2026 – 10/11/2026").assertIsDisplayed()

        compose.onNodeWithText(context.getString(R.string.last_30_days)).performScrollTo().performClick()
        compose.onNodeWithText("19/10/2026 – 17/11/2026").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.next_period)).performClick()
        compose.onNodeWithText("18/11/2026 – 17/12/2026").assertIsDisplayed()
    }

    @Test
    fun selectedDimensionCanBeClearedAfterNavigatingToAnEmptyRange() {
        compose.runOnIdle {
            vm.toggleDimensionFilter(1L)
            vm.goToPreviousPeriod()
        }
        compose.onNodeWithText("Clear filter").assertIsDisplayed().performClick()
        compose.runOnIdle { assertNull(vm.state.value.selectedDimensionId) }
    }

    @Test
    fun previousChartsAreHiddenWhileTheNewRangeLoads() {
        compose.onNodeWithText(context.getString(R.string.energy_trend_chart)).assertExists()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = Thread {
            runBlocking {
                db.withTransaction {
                    started.countDown()
                    release.await()
                }
            }
        }
        worker.start()
        try {
            assertTrue("The test transaction must start", started.await(5, TimeUnit.SECONDS))
            compose.onNodeWithContentDescription(context.getString(R.string.previous_period)).performClick()
            compose.onNodeWithText("Loading insights").assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.energy_trend_chart)).assertDoesNotExist()
        } finally {
            release.countDown()
            worker.join(5_000)
        }
    }
}
