package com.empathie.energyaccounting.ui.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.empathie.energyaccounting.data.repository.EnergyAccountingRepository
import com.empathie.energyaccounting.domain.model.ActivityTemplate
import com.empathie.energyaccounting.domain.model.DailyAccount
import com.empathie.energyaccounting.domain.model.DimensionChoiceState
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.EnergyDimension
import com.empathie.energyaccounting.domain.model.Entry
import com.empathie.energyaccounting.domain.model.TimeSlot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale

/**
 * Everything the Add/Edit Entry sheet holds while it is open.
 */
data class EntryDraftState(
    val editingEntryId: Long? = null,
    val timeSlot: TimeSlot,
    val activity: String = "",
    val situationalNote: String = "",
    val direction: Direction = Direction.SPEND,
    val energyValue: Int = 30,
    val dimensionChoice: DimensionChoiceState = DimensionChoiceState(null),
    val matchedTemplate: ActivityTemplate? = null,
    val hasModifiedTemplateDefaults: Boolean = false,
    val updateTemplateDefaults: Boolean = false,
)

/**
 * State for the Daily Account tab: the selected day, its ledger, the calendar's per-day
 * net balances, and the draft of the entry sheet.
 *
 * The sheet's draft lives here (not in Compose state) so a configuration change cannot
 * lose a half-typed entry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyAccountViewModel(
    private val repo: EnergyAccountingRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val today: LocalDate
        get() = LocalDate.now(clock)

    private val _selectedDate = MutableStateFlow(today)
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _draftState = MutableStateFlow<EntryDraftState?>(null)
    val draftState: StateFlow<EntryDraftState?> = _draftState.asStateFlow()

    private val _isCalendarOpen = MutableStateFlow(false)
    val isCalendarOpen: StateFlow<Boolean> = _isCalendarOpen.asStateFlow()

    private val _calendarMonth = MutableStateFlow(YearMonth.from(today))
    val calendarMonth: StateFlow<YearMonth> = _calendarMonth.asStateFlow()

    val account: StateFlow<DailyAccount> = _selectedDate
        .flatMapLatest { date -> repo.observeDailyAccount(date) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DailyAccount.fromEntries(today, emptyList()),
        )

    val dimensions: StateFlow<List<EnergyDimension>> = repo.observeActiveDimensions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val templates: StateFlow<List<ActivityTemplate>> = repo.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val calendarNetBalances: StateFlow<Map<LocalDate, Int>> = _calendarMonth
        .flatMapLatest { month -> repo.observeDateNetBalances(month.atDay(1), month.atEndOfMonth()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var editingEntry: Entry? = null

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _calendarMonth.value = YearMonth.from(date)
        _isCalendarOpen.value = false
    }

    fun goToPreviousDay() = selectDate(_selectedDate.value.minusDays(1))

    fun goToNextDay() = selectDate(_selectedDate.value.plusDays(1))

    fun goToToday() = selectDate(today)

    fun openCalendar() {
        _calendarMonth.value = YearMonth.from(_selectedDate.value)
        _isCalendarOpen.value = true
    }

    fun closeCalendar() {
        _isCalendarOpen.value = false
    }

    fun changeCalendarMonth(months: Long) {
        _calendarMonth.value = _calendarMonth.value.plusMonths(months)
    }

    fun openNewEntrySheet(preferredSlot: TimeSlot? = null) {
        editingEntry = null
        val isToday = _selectedDate.value == today
        val timeSlot = preferredSlot ?: if (isToday) {
            TimeSlot.defaultForTime(LocalTime.now(clock))
        } else {
            TimeSlot.EARLY_MORNING
        }
        _draftState.value = EntryDraftState(timeSlot = timeSlot)
    }

    fun openEditEntrySheet(entry: Entry) {
        editingEntry = entry
        _draftState.value = EntryDraftState(
            editingEntryId = entry.id,
            timeSlot = entry.timeSlot,
            activity = entry.activity,
            situationalNote = entry.situationalNote.orEmpty(),
            direction = entry.direction,
            energyValue = entry.energyValue,
            dimensionChoice = DimensionChoiceState(
                primaryId = entry.primaryDimension.id,
                secondaryIds = entry.secondaryDimensions.map { it.id }.toSet(),
            ),
        )
    }

    fun dismissDraft() {
        editingEntry = null
        _draftState.value = null
    }

    fun applyTemplate(template: ActivityTemplate) {
        _draftState.update { current ->
            current?.copy(
                activity = template.activityName,
                direction = template.defaultDirection,
                energyValue = template.defaultEnergyValue,
                dimensionChoice = DimensionChoiceState(
                    primaryId = template.defaultPrimaryDimension.id,
                    secondaryIds = template.defaultSecondaryDimensions.map { it.id }.toSet(),
                ),
                matchedTemplate = template,
                hasModifiedTemplateDefaults = false,
                updateTemplateDefaults = false,
            )
        }
    }

    fun onActivityChange(text: String) {
        _draftState.update { current ->
            current?.copy(activity = text)?.withMatchedTemplate(text)?.recomputeTemplateModification()
        }
    }

    fun onSituationalNoteChange(text: String) {
        _draftState.update { it?.copy(situationalNote = text) }
    }

    fun onTimeSlotChange(timeSlot: TimeSlot) {
        _draftState.update { it?.copy(timeSlot = timeSlot) }
    }

    fun onDirectionChange(direction: Direction) {
        _draftState.update { it?.copy(direction = direction)?.recomputeTemplateModification() }
    }

    fun onEnergyValueChange(value: Int) {
        _draftState.update {
            it?.copy(energyValue = value.coerceIn(Entry.MIN_ENERGY_VALUE, Entry.MAX_ENERGY_VALUE))
                ?.recomputeTemplateModification()
        }
    }

    fun onTapDimension(dimensionId: Long) {
        _draftState.update {
            it?.copy(dimensionChoice = it.dimensionChoice.onTap(dimensionId))
                ?.recomputeTemplateModification()
        }
    }

    fun onLongPressDimension(dimensionId: Long) {
        _draftState.update {
            it?.copy(dimensionChoice = it.dimensionChoice.onLongPress(dimensionId))
                ?.recomputeTemplateModification()
        }
    }

    fun onUpdateTemplateDefaultsChange(checked: Boolean) {
        _draftState.update { it?.copy(updateTemplateDefaults = checked) }
    }

    /**
     * Persists the draft and closes the sheet. A blank activity is a no-op, so the Save
     * button and the caller disagreeing never crashes.
     */
    suspend fun saveDraft() {
        val draft = _draftState.value ?: return
        val activity = draft.activity.trim()
        if (activity.isEmpty()) return

        val availableDimensions = repo.observeActiveDimensions().first()
        val primaryDimension = draft.dimensionChoice.primaryId
            ?.let { id -> availableDimensions.firstOrNull { it.id == id } }
            ?: availableDimensions.firstOrNull()
            ?: return
        val secondaryDimensions = draft.dimensionChoice.secondaryIds.mapNotNull { id ->
            availableDimensions.firstOrNull { it.id == id }
        }

        val entry = Entry(
            id = draft.editingEntryId ?: 0L,
            date = _selectedDate.value,
            timeSlot = draft.timeSlot,
            activity = activity,
            situationalNote = draft.situationalNote.takeIf { it.isNotBlank() },
            direction = draft.direction,
            energyValue = draft.energyValue.coerceIn(Entry.MIN_ENERGY_VALUE, Entry.MAX_ENERGY_VALUE),
            primaryDimension = primaryDimension,
            secondaryDimensions = secondaryDimensions,
            createdAtEpochMillis = editingEntry?.createdAtEpochMillis ?: clock.millis(),
        )

        repo.saveEntry(entry, updateTemplateDefaults = draft.updateTemplateDefaults)
        editingEntry = null
        _draftState.value = null
    }

    suspend fun deleteEntry(entryId: Long) {
        repo.deleteEntry(entryId)
        editingEntry = null
        _draftState.value = null
    }

    private fun EntryDraftState.withMatchedTemplate(activityName: String): EntryDraftState {
        val normalized = activityName.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return copy(matchedTemplate = null)
        val match = templates.value.firstOrNull { it.normalizedName == normalized }
        return copy(matchedTemplate = match)
    }

    private fun EntryDraftState.recomputeTemplateModification(): EntryDraftState {
        val template = matchedTemplate
            ?: return copy(hasModifiedTemplateDefaults = false, updateTemplateDefaults = false)

        val modified = direction != template.defaultDirection ||
            energyValue != template.defaultEnergyValue ||
            dimensionChoice.primaryId != template.defaultPrimaryDimension.id ||
            dimensionChoice.secondaryIds != template.defaultSecondaryDimensions.map { it.id }.toSet()

        return copy(
            hasModifiedTemplateDefaults = modified,
            updateTemplateDefaults = if (modified) updateTemplateDefaults else false,
        )
    }
}
