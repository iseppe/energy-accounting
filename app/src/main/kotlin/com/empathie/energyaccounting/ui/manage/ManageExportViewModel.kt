package com.empathie.energyaccounting.ui.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.empathie.energyaccounting.data.diagnostics.AdjustableClock
import com.empathie.energyaccounting.data.diagnostics.AppLogger
import com.empathie.energyaccounting.data.diagnostics.DebugLogExporter
import com.empathie.energyaccounting.data.diagnostics.FakeClockState
import com.empathie.energyaccounting.data.export.BackupSerializer
import com.empathie.energyaccounting.data.export.CsvExporter
import com.empathie.energyaccounting.data.export.PdfLabels
import com.empathie.energyaccounting.data.export.PractitionerPdfGenerator
import com.empathie.energyaccounting.data.export.RestoreMode
import com.empathie.energyaccounting.data.local.DatabaseProvider
import com.empathie.energyaccounting.data.repository.EnergyAccountingRepository
import com.empathie.energyaccounting.data.settings.AppLanguage
import com.empathie.energyaccounting.data.settings.AppSettings
import com.empathie.energyaccounting.domain.model.ActivityTemplate
import com.empathie.energyaccounting.domain.model.CheckInReminder
import com.empathie.energyaccounting.domain.model.DailyAccount
import com.empathie.energyaccounting.domain.model.EnergyDimension
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.domain.service.InsightsCalculator
import com.empathie.energyaccounting.reminder.CheckInReminderReceiver
import com.empathie.energyaccounting.reminder.ReminderScheduler
import com.empathie.energyaccounting.reminder.SlotEvaluationResult
import com.empathie.energyaccounting.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate

enum class ManageMessage {
    EXPORT_FAILED,
    RESTORE_COMPLETE,
    RESTORE_FAILED,
    TEMPLATE_NAME_IN_USE,
    DIMENSION_NAME_IN_USE,
    REMINDER_PERMISSION_DENIED,
}

/**
 * Backs the Manage & Export tab: templates, custom dimensions, check-in reminders,
 * exports/restores, diagnostics, and the appearance/language choices.
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
    private val debugLogExporter: DebugLogExporter,
    private val clock: AdjustableClock = AdjustableClock(),
) : ViewModel() {

    val templates: StateFlow<List<ActivityTemplate>> = repo.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dimensions: StateFlow<List<EnergyDimension>> = repo.observeAllDimensions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reminders: StateFlow<List<CheckInReminder>> = repo.observeReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _themeMode = MutableStateFlow(settings.themeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _appLanguage = MutableStateFlow(settings.appLanguage())
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(settings.vibrationEnabled())
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    private val _devModeEnabled = MutableStateFlow(settings.devModeEnabled())
    val devModeEnabled: StateFlow<Boolean> = _devModeEnabled.asStateFlow()

    val fakeClockState: StateFlow<FakeClockState> = clock.state

    private val _lastSlotEvaluation = MutableStateFlow<SlotEvaluationResult?>(null)
    val lastSlotEvaluation: StateFlow<SlotEvaluationResult?> = _lastSlotEvaluation.asStateFlow()

    private var buildFooterTaps = 0

    private val _message = MutableStateFlow<ManageMessage?>(null)
    val message: StateFlow<ManageMessage?> = _message.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        settings.setThemeMode(mode)
    }

    fun setAppLanguage(language: AppLanguage) {
        _appLanguage.value = language
        settings.setAppLanguage(language)
    }

    fun setVibrationEnabled(enabled: Boolean) {
        _vibrationEnabled.value = enabled
        settings.setVibrationEnabled(enabled)
    }

    fun onBuildFooterTapped(): Boolean {
        if (_devModeEnabled.value) return false
        buildFooterTaps += 1
        if (buildFooterTaps < 5) return false

        buildFooterTaps = 0
        _devModeEnabled.value = true
        settings.setDevModeEnabled(true)
        AppLogger.info("DevMode", "Developer mode enabled")
        return true
    }

    fun disableDevMode() {
        _devModeEnabled.value = false
        settings.setDevModeEnabled(false)
        resetFakeClock()
        AppLogger.info("DevMode", "Developer mode disabled; clock restored to system")
    }

    fun setFakeDateTime(date: java.time.LocalDate, time: java.time.LocalTime) {
        clock.setFakeDateTime(date, time)
        AppLogger.info("DevMode", "Fake clock set to $date $time")
    }

    fun advanceFakeClockMinutes(minutes: Long) {
        clock.advanceMinutes(minutes)
    }

    fun resetFakeClock() {
        clock.resetToSystem()
        _lastSlotEvaluation.value = null
    }

    suspend fun evaluateSlotAtCurrentClock(
        context: android.content.Context,
        timeSlot: TimeSlot,
    ): SlotEvaluationResult {
        val state = clock.state.value
        val result = withContext(Dispatchers.IO) {
            CheckInReminderReceiver.evaluateSlotForDiagnostics(
                context = context,
                dao = DatabaseProvider.get(context).dao(),
                date = state.currentDate,
                timeSlot = timeSlot,
            )
        }
        _lastSlotEvaluation.value = result
        return result
    }

    fun scheduleTestAlarmIn5Seconds(timeSlot: TimeSlot) {
        reminderScheduler.scheduleTestAlarmInSeconds(timeSlot, seconds = 5, forcePost = true)
        AppLogger.info("DevMode", "Scheduled OS alarm in 5s for ${timeSlot.name}")
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun reportExportFailure() {
        AppLogger.error("Export", "Export or file operation failed")
        _message.value = ManageMessage.EXPORT_FAILED
    }

    fun reportReminderPermissionDenied() {
        _message.value = ManageMessage.REMINDER_PERMISSION_DENIED
    }

    suspend fun toggleTemplatePin(template: ActivityTemplate) {
        repo.toggleTemplatePin(template.id, !template.isPinned)
    }

    suspend fun deleteTemplate(template: ActivityTemplate) {
        repo.deleteTemplate(template.id)
    }

    suspend fun saveTemplate(template: ActivityTemplate) {
        runCatching { repo.saveTemplate(template) }
            .onSuccess { AppLogger.info("Manage", "Saved template '${template.activityName}'") }
            .onFailure {
                AppLogger.error("Manage", "Template save rejected for '${template.activityName}'", it)
                _message.value = ManageMessage.TEMPLATE_NAME_IN_USE
            }
    }

    suspend fun addCustomDimension(name: String) {
        val colorIndex = dimensions.value.size % 6
        runCatching { repo.createCustomDimension(name, colorIndex) }
            .onFailure { _message.value = ManageMessage.DIMENSION_NAME_IN_USE }
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
        AppLogger.info(
            "Reminder",
            "Updated ${reminder.timeSlot.name} to %02d:%02d enabled=${reminder.isEnabled}"
                .format(reminder.hour, reminder.minute),
        )
    }

    suspend fun buildCsv(
        dimensionLabel: (EnergyDimension) -> String = { it.name },
    ): ByteArray = withContext(Dispatchers.IO) {
        CsvExporter.exportEntries(repo.getAllEntries(), dimensionLabel).toByteArray()
    }

    suspend fun buildJson(): ByteArray = withContext(Dispatchers.IO) {
        backupSerializer.exportJson().toByteArray()
    }

    suspend fun buildPdf(
        startDate: LocalDate,
        endDate: LocalDate,
        labels: PdfLabels,
    ): ByteArray = withContext(Dispatchers.IO) {
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
        pdfGenerator.generateReport(output, startDate, endDate, accounts, insights, labels)
        output.toByteArray()
    }

    suspend fun buildDebugLog(
        buildId: String,
        packageName: String,
        versionName: String,
        versionCode: Int,
        sdkInt: Int,
        deviceModel: String,
        localeTag: String,
        themeName: String,
    ): ByteArray = withContext(Dispatchers.IO) {
        debugLogExporter.buildLogReport(
            buildId = buildId,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            sdkInt = sdkInt,
            deviceModel = deviceModel,
            localeTag = localeTag,
            themeName = themeName,
        ).toByteArray()
    }

    suspend fun restore(json: String, mode: RestoreMode) {
        val result = withContext(Dispatchers.IO) {
            runCatching { backupSerializer.restoreJson(json, mode) }
        }
        result.onSuccess { reminders ->
            reminders.forEach { reminder -> reminderScheduler.scheduleOrCancel(reminder) }
            AppLogger.info("Restore", "Restored ${reminders.size} reminders in $mode mode")
        }.onFailure {
            AppLogger.error("Restore", "Restore failed in $mode mode", it)
        }
        _message.value = if (result.isSuccess) {
            ManageMessage.RESTORE_COMPLETE
        } else {
            ManageMessage.RESTORE_FAILED
        }
    }
}
