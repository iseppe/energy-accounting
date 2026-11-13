package com.empathie.energyaccounting.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.empathie.energyaccounting.data.repository.EnergyAccountingRepository
import com.empathie.energyaccounting.domain.model.Entry
import com.empathie.energyaccounting.domain.model.PeriodInsights
import com.empathie.energyaccounting.domain.service.InsightsCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

enum class InsightsPeriod {
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
    CUSTOM,
}

data class InsightsUiState(
    val period: InsightsPeriod,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val selectedDimensionId: Long? = null,
)

/**
 * Period selection and dimension filtering for the Insights tab.
 *
 * A selected dimension matches entries attributed to it, which is how a user checks
 * "what did sensory load look like?". `today` is recomputed on every use so a session
 * open past midnight starts its `Today` range correctly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(
    private val repo: EnergyAccountingRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val today: LocalDate
        get() = LocalDate.now(clock)

    private val _state = MutableStateFlow(
        InsightsUiState(
            period = InsightsPeriod.TODAY,
            startDate = today,
            endDate = today,
        )
    )
    val state: StateFlow<InsightsUiState> = _state.asStateFlow()

    val insights: StateFlow<PeriodInsights> = _state
        .map { it.startDate to it.endDate }
        .distinctUntilChanged()
        .flatMapLatest { (startDate, endDate) -> repo.observePeriodInsights(startDate, endDate) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            InsightsCalculator.calculate(today, today, emptyList(), emptyList()),
        )

    val filteredEntries: StateFlow<List<Entry>> = _state
        .flatMapLatest { current ->
            repo.observeEntries(current.startDate, current.endDate).map { entries ->
                val dimensionId = current.selectedDimensionId ?: return@map emptyList()
                entries.filter { entry -> entry.dimension.id == dimensionId }
                    .sortedWith(compareBy({ it.date }, { it.timeSlot.ordinal }, { it.createdAtEpochMillis }))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Keep relative periods anchored to Developer Mode's simulated "today".
        (clock as? com.empathie.energyaccounting.data.diagnostics.AdjustableClock)?.let { adjustable ->
            viewModelScope.launch {
                adjustable.state.drop(1).collect {
                    val current = _state.value.period
                    if (current != InsightsPeriod.CUSTOM) {
                        selectPeriod(current)
                    }
                }
            }
        }
    }

    fun selectPeriod(period: InsightsPeriod) {
        _state.update { current ->
            when (period) {
                InsightsPeriod.TODAY ->
                    current.copy(period = period, startDate = today, endDate = today)

                InsightsPeriod.LAST_7_DAYS ->
                    current.copy(period = period, startDate = today.minusDays(6), endDate = today)

                InsightsPeriod.LAST_30_DAYS ->
                    current.copy(period = period, startDate = today.minusDays(29), endDate = today)

                InsightsPeriod.CUSTOM -> current.copy(period = period)
            }
        }
    }

    fun setStartDate(date: LocalDate) {
        _state.update { current ->
            current.copy(period = InsightsPeriod.CUSTOM, startDate = date).normalized()
        }
    }

    fun setEndDate(date: LocalDate) {
        _state.update { current ->
            current.copy(period = InsightsPeriod.CUSTOM, endDate = date).normalized()
        }
    }

    fun toggleDimensionFilter(dimensionId: Long) {
        _state.update { current ->
            current.copy(
                selectedDimensionId = if (current.selectedDimensionId == dimensionId) null else dimensionId
            )
        }
    }

    private fun InsightsUiState.normalized(): InsightsUiState =
        if (startDate.isAfter(endDate)) copy(startDate = endDate, endDate = startDate) else this
}
