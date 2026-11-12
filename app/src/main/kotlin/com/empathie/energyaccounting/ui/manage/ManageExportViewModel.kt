package com.empathie.energyaccounting.ui.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.empathie.energyaccounting.data.export.BackupSerializer
import com.empathie.energyaccounting.data.export.CsvExporter
import com.empathie.energyaccounting.data.export.PractitionerPdfGenerator
import com.empathie.energyaccounting.data.export.RestoreMode
import com.empathie.energyaccounting.data.repository.EnergyAccountingRepository
import com.empathie.energyaccounting.data.settings.AppSettings
import com.empathie.energyaccounting.domain.model.ActivityTemplate
import com.empathie.energyaccounting.domain.model.CheckInReminder
import com.empathie.energyaccounting.domain.model.DailyAccount
import com.empathie.energyaccounting.domain.model.EnergyDimension
import com.empathie.energyaccounting.domain.service.InsightsCalculator
import com.empathie.energyaccounting.reminder.ReminderScheduler
import com.empathie.energyaccounting.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.io.ByteArrayOutputStream
import java.time.LocalDate

enum class ManageMessage {
    EXPORT_FAILED,
    RESTORE_COMPLETE,
    RESTORE_FAILED,
}

/**
 * Backs the Manage & Export tab: templates, custom dimensions, check-in reminders,
 * exports/restores, and the appearance choice.
 *
 * The exports are built as bytes here so the Compose layer only needs to hand them to the
 * Storage Access Framework, and the report can be rendered without a Context.
 */
class ManageExportViewModel(
    private val repo: EnergyAccountingRepository,
    private val backupSerializer: BackupSerializer,
    private val pdfGenerator: PractitionerPdfGenerator,
    private val reminderScheduler: ReminderScheduler,
    private val settings: AppSettings,
) : ViewModel() {

    val templates: StateFlow<List<ActivityTemplate>> = repo.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dimensions: StateFlow<List<EnergyDimension>> = repo.observeAllDimensions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reminders: StateFlow<List<CheckInReminder>> = repo.observeReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _themeMode = MutableStateFlow(settings.themeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _message = MutableStateFlow<ManageMessage?>(null)
    val message: StateFlow<ManageMessage?> = _message.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        settings.setThemeMode(mode)
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun reportExportFailure() {
        _message.value = ManageMessage.EXPORT_FAILED
    }

    suspend fun toggleTemplatePin(template: ActivityTemplate) {
        repo.toggleTemplatePin(template.id, !template.isPinned)
    }

    suspend fun deleteTemplate(template: ActivityTemplate) {
        repo.deleteTemplate(template.id)
    }

    suspend fun saveTemplate(template: ActivityTemplate) {
        repo.saveTemplate(template)
    }

    suspend fun addCustomDimension(name: String) {
        val colorIndex = dimensions.value.size % 6
        repo.createCustomDimension(name, colorIndex)
    }

    suspend fun removeCustomDimension(dimensionId: Long) {
        repo.removeOrArchiveCustomDimension(dimensionId)
    }

    suspend fun setDimensionArchived(dimension: EnergyDimension, archived: Boolean) {
        repo.setDimensionArchived(dimension.id, archived)
    }

    suspend fun updateReminder(reminder: CheckInReminder) {
        repo.updateReminder(reminder)
        reminderScheduler.scheduleOrCancel(reminder)
    }

    suspend fun buildCsv(): ByteArray = CsvExporter.exportEntries(repo.getAllEntries()).toByteArray()

    suspend fun buildJson(): ByteArray = backupSerializer.exportJson().toByteArray()

    suspend fun buildPdf(startDate: LocalDate, endDate: LocalDate): ByteArray {
        val allEntries = repo.getAllEntries()
        val inRange = allEntries.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }
        val accounts = inRange
            .groupBy { it.date }
            .toSortedMap()
            .map { (date, entries) -> DailyAccount.fromEntries(date, entries) }
        val insights = InsightsCalculator.calculate(
            startDate = startDate,
            endDate = endDate,
            entries = inRange,
            allDimensions = repo.getAllDimensions(),
        )

        val output = ByteArrayOutputStream()
        pdfGenerator.generateReport(output, startDate, endDate, accounts, insights)
        return output.toByteArray()
    }

    suspend fun restore(json: String, mode: RestoreMode) {
        val result = runCatching { backupSerializer.restoreJson(json, mode) }
        _message.value = if (result.isSuccess) {
            ManageMessage.RESTORE_COMPLETE
        } else {
            ManageMessage.RESTORE_FAILED
        }
    }
}
