package com.empathie.energyaccounting.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.EnergyDimension
import com.empathie.energyaccounting.domain.model.Entry
import com.empathie.energyaccounting.domain.model.SystemDimensionKey
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.domain.service.InsightsCalculator
import com.empathie.energyaccounting.ui.insights.InsightsPeriod
import com.empathie.energyaccounting.ui.insights.InsightsUiState
import com.empathie.energyaccounting.ui.insights.PeriodSummaryCard
import com.empathie.energyaccounting.ui.theme.EmpathieTheme
import com.empathie.energyaccounting.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class InsightsPeriodSummaryTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun previousRangeTotalsAreNotDisplayedUnderNewDates() {
        val oldDate = LocalDate.of(2026, 11, 17)
        val dimension = EnergyDimension(1L, "Physical", SystemDimensionKey.PHYSICAL, 0)
        val entry = Entry(
            date = oldDate, timeSlot = TimeSlot.LATE_MORNING, activity = "Old entry",
            situationalNote = null, direction = Direction.SPEND, energyValue = 70,
            dimension = dimension, createdAtEpochMillis = 100L,
        )
        val oldInsights = InsightsCalculator.calculate(oldDate, oldDate, listOf(entry), listOf(dimension))
        val selected = LocalDate.of(2026, 11, 16)
        val state = InsightsUiState(InsightsPeriod.TODAY, selected, selected)

        compose.setContent {
            EmpathieTheme(ThemeMode.LIGHT) {
                PeriodSummaryCard(oldInsights, state, onPrevious = {}, onNext = {}, onClearFilter = {})
            }
        }

        compose.onNodeWithText("16/11/2026 – 16/11/2026").assertIsDisplayed()
        compose.onNodeWithText("-70").assertDoesNotExist()
        compose.onNodeWithText("Loading insights").assertIsDisplayed()
    }
}
