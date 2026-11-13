package com.empathie.energyaccounting.ui.manage

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.empathie.energyaccounting.BuildConfig
import com.empathie.energyaccounting.R
import com.empathie.energyaccounting.data.export.PdfLabels
import com.empathie.energyaccounting.data.export.RestoreMode
import com.empathie.energyaccounting.data.settings.AppLanguage
import com.empathie.energyaccounting.domain.model.ActivityTemplate
import com.empathie.energyaccounting.domain.model.CheckInReminder
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.EnergyDimension
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.domain.service.EnergyFormatter
import com.empathie.energyaccounting.reminder.CheckInReminderReceiver
import com.empathie.energyaccounting.reminder.ReminderScheduler
import com.empathie.energyaccounting.reminder.SlotEvaluationResult
import com.empathie.energyaccounting.ui.common.DateField
import com.empathie.energyaccounting.ui.common.EnergyDirectionAndValueSelector
import com.empathie.energyaccounting.ui.common.LocalAppHaptics
import com.empathie.energyaccounting.ui.common.SelectablePill
import com.empathie.energyaccounting.ui.daily.dimensionLabel
import com.empathie.energyaccounting.ui.daily.timeSlotLabel
import com.empathie.energyaccounting.ui.theme.LocalEmpathieColors
import com.empathie.energyaccounting.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ExportKind { PDF, CSV, JSON, DEBUG_LOG }

private data class ExportRequest(
    val kind: ExportKind,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
)

@Composable
fun ManageExportScreen(viewModel: ManageExportViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val dimensions by viewModel.dimensions.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsStateWithLifecycle()
    val devModeEnabled by viewModel.devModeEnabled.collectAsStateWithLifecycle()
    val fakeClockState by viewModel.fakeClockState.collectAsStateWithLifecycle()
    val lastSlotEvaluation by viewModel.lastSlotEvaluation.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val haptics = LocalAppHaptics.current

    var editingTemplate by remember { mutableStateOf<ActivityTemplate?>(null) }
    var showCreateTemplate by remember { mutableStateOf(false) }
    var showAddDimension by remember { mutableStateOf(false) }
    var showPdfRange by remember { mutableStateOf(false) }
    var showRestoreMode by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<ExportRequest?>(null) }
    var pendingRestoreMode by remember { mutableStateOf<RestoreMode?>(null) }
    var pendingEnableReminder by remember { mutableStateOf<CheckInReminder?>(null) }
    var selectedDevSlot by remember { mutableStateOf(TimeSlot.EARLY_MORNING) }

    fun postTestNotification() {
        val posted = CheckInReminderReceiver.postTestNotification(
            context,
            TimeSlot.defaultForTime(LocalTime.now()),
        )
        val text = if (posted) {
            context.getString(R.string.test_notification_sent)
        } else {
            context.getString(R.string.reminder_permission_rationale)
        }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun sharePdf(start: LocalDate, end: LocalDate) {
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { viewModel.buildPdf(start, end, PdfLabels.from(context)) }.getOrNull()
            }
            if (bytes == null) {
                viewModel.reportExportFailure()
                return@launch
            }

            val chooser = withContext(Dispatchers.IO) {
                runCatching {
                    val directory = File(context.cacheDir, "reports").apply { mkdirs() }
                    val file = File(directory, "energy-accounting-${start}_${end}.pdf")
                    file.writeBytes(bytes)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    Intent.createChooser(send, context.getString(R.string.share_pdf))
                }.getOrNull()
            }

            if (chooser != null) {
                context.startActivity(chooser)
            } else {
                viewModel.reportExportFailure()
            }
        }
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val request = pendingExport
        pendingExport = null
        if (uri == null || request == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    when (request.kind) {
                        ExportKind.PDF -> viewModel.buildPdf(
                            request.startDate ?: LocalDate.now().minusDays(6),
                            request.endDate ?: LocalDate.now(),
                            PdfLabels.from(context),
                        )

                        ExportKind.CSV -> viewModel.buildCsv { dimensionLabel(context, it) }
                        ExportKind.JSON -> viewModel.buildJson()
                        ExportKind.DEBUG_LOG -> viewModel.buildDebugLog(
                            buildId = BuildConfig.BUILD_ID,
                            packageName = BuildConfig.APPLICATION_ID,
                            versionName = BuildConfig.VERSION_NAME,
                            versionCode = BuildConfig.VERSION_CODE,
                            sdkInt = Build.VERSION.SDK_INT,
                            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                            localeTag = Locale.getDefault().toLanguageTag(),
                            themeName = themeMode.name,
                        )
                    }
                }.getOrNull()
            }

            if (bytes == null) {
                viewModel.reportExportFailure()
                return@launch
            }
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                }.onFailure { viewModel.reportExportFailure() }
            }
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val mode = pendingRestoreMode
        pendingRestoreMode = null
        if (uri == null || mode == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (json == null) {
                viewModel.reportExportFailure()
            } else {
                viewModel.restore(json, mode)
            }
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingEnableReminder
        pendingEnableReminder = null
        if (pending == null) return@rememberLauncherForActivityResult
        if (granted) {
            scope.launch { viewModel.updateReminder(pending.copy(isEnabled = true)) }
        } else {
            viewModel.reportReminderPermissionDenied()
        }
    }

    val testNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) postTestNotification() else viewModel.reportReminderPermissionDenied()
    }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        val text = when (current) {
            ManageMessage.EXPORT_FAILED -> context.getString(R.string.export_failed)
            ManageMessage.RESTORE_COMPLETE -> context.getString(R.string.restore_complete)
            ManageMessage.RESTORE_FAILED -> context.getString(R.string.restore_failed)
            ManageMessage.TEMPLATE_NAME_IN_USE -> context.getString(R.string.template_name_in_use)
            ManageMessage.DIMENSION_NAME_IN_USE -> context.getString(R.string.dimension_name_in_use)
            ManageMessage.REMINDER_PERMISSION_DENIED -> context.getString(R.string.reminder_permission_rationale)
        }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        viewModel.consumeMessage()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = stringResource(R.string.appearance)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        SelectablePill(
                            label = when (mode) {
                                ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                            },
                            selected = themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.language),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalEmpathieColors.current.onSurfaceMuted,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { language ->
                        SelectablePill(
                            label = when (language) {
                                AppLanguage.SYSTEM -> stringResource(R.string.language_system)
                                AppLanguage.ENGLISH -> stringResource(R.string.language_english)
                                AppLanguage.ITALIAN -> stringResource(R.string.language_italian)
                            },
                            selected = appLanguage == language,
                            onClick = {
                                viewModel.setAppLanguage(language)
                                activity?.recreate()
                            },
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.vibration_feedback),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = vibrationEnabled,
                        onCheckedChange = { enabled ->
                            haptics.tick()
                            viewModel.setVibrationEnabled(enabled)
                        },
                    )
                }
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.activity_templates),
                action = {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            showCreateTemplate = true
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.create),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            ) {
                templates.forEach { template ->
                    TemplateRow(
                        template = template,
                        onTogglePin = { scope.launch { viewModel.toggleTemplatePin(template) } },
                        onEdit = { editingTemplate = template },
                        onDelete = { scope.launch { viewModel.deleteTemplate(template) } },
                    )
                }
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.energy_dimensions_management),
                action = {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            showAddDimension = true
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.add_custom_dimension),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            ) {
                dimensions.forEach { dimension ->
                    DimensionRow(
                        dimension = dimension,
                        onToggleArchive = {
                            scope.launch {
                                viewModel.setDimensionArchived(dimension, !dimension.isArchived)
                            }
                        },
                        onDelete = { scope.launch { viewModel.removeCustomDimension(dimension.id) } },
                    )
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.check_in_reminders)) {
                reminders.forEach { reminder ->
                    ReminderRow(
                        reminder = reminder,
                        onToggle = { enabled ->
                            val needsPermission = enabled &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED

                                if (needsPermission) {
                                    haptics.tick()
                                    pendingEnableReminder = reminder
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    haptics.tick()
                                    scope.launch {
                                        viewModel.updateReminder(reminder.copy(isEnabled = enabled))
                                    }
                                }
                        },
                        onTimeChange = { hour, minute ->
                            scope.launch { viewModel.updateReminder(reminder.copy(hour = hour, minute = minute)) }
                        },
                    )
                }

                FilledTonalButton(
                    onClick = {
                        haptics.tick()
                        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        if (needsPermission) {
                            testNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            postTestNotification()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.send_test_notification))
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.export_practitioner_pdf)) {
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        showPdfRange = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.export_practitioner_pdf))
                }
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        pendingExport = ExportRequest(ExportKind.CSV)
                        createDocument.launch("energy-accounting-${LocalDate.now()}.csv")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.export_csv))
                }
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        pendingExport = ExportRequest(ExportKind.JSON)
                        createDocument.launch("energy-accounting-backup-${LocalDate.now()}.json")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.export_json_backup))
                }
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        showRestoreMode = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.restore_json_backup))
                }
            }
        }

        if (devModeEnabled) {
            item {
                SectionCard(title = stringResource(R.string.dev_mode_title)) {
                    TextButton(onClick = {
                        haptics.tick()
                        viewModel.disableDevMode()
                    }) {
                        Text(stringResource(R.string.dev_mode_disable))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.fake_clock_enable),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = fakeClockState.isEnabled,
                            onCheckedChange = { enabled ->
                                haptics.tick()
                                if (enabled) {
                                    viewModel.setFakeDateTime(
                                        fakeClockState.currentDate,
                                        fakeClockState.currentTime,
                                    )
                                } else {
                                    viewModel.resetFakeClock()
                                }
                            },
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DateField(
                            label = "",
                            date = fakeClockState.currentDate,
                            onDateChange = { date ->
                                viewModel.setFakeDateTime(date, fakeClockState.currentTime)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        viewModel.setFakeDateTime(
                                            fakeClockState.currentDate,
                                            LocalTime.of(hour, minute),
                                        )
                                    },
                                    fakeClockState.currentTime.hour,
                                    fakeClockState.currentTime.minute,
                                    DateFormat.is24HourFormat(context),
                                ).show()
                            }
                        ) {
                            Text(fakeClockState.currentTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val presets = listOf(
                            LocalTime.of(9, 29),
                            LocalTime.of(12, 29),
                            LocalTime.of(17, 29),
                            LocalTime.of(20, 59),
                        )
                        items(presets) { time ->
                            AssistChip(
                                onClick = {
                                    haptics.tick()
                                    viewModel.setFakeDateTime(fakeClockState.currentDate, time)
                                },
                                label = { Text(time.format(DateTimeFormatter.ofPattern("HH:mm"))) },
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    haptics.tick()
                                    viewModel.advanceFakeClockMinutes(1)
                                },
                                label = { Text(stringResource(R.string.fake_clock_advance)) },
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    haptics.tick()
                                    viewModel.resetFakeClock()
                                },
                                label = { Text(stringResource(R.string.fake_clock_reset)) },
                            )
                        }
                    }

                    val notificationsEnabled = androidx.core.app.NotificationManagerCompat
                        .from(context)
                        .areNotificationsEnabled()
                    val exactEnabled = ReminderScheduler(context).canScheduleExactAlarms()

                    Text(
                        text = "${stringResource(R.string.dev_status_notifications)}: " +
                            stringResource(
                                if (notificationsEnabled) R.string.dev_status_ok
                                else R.string.dev_status_blocked
                            ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${stringResource(R.string.dev_status_exact_alarms)}: " +
                                stringResource(
                                    if (exactEnabled) R.string.dev_status_ok
                                    else R.string.dev_status_inexact
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (!exactEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                                Uri.parse("package:${context.packageName}"),
                                            )
                                        )
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.dev_grant_exact_alarm))
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.dev_slot_evaluator),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalEmpathieColors.current.onSurfaceMuted,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(TimeSlot.entries) { slot ->
                            SelectablePill(
                                label = timeSlotLabel(context, slot),
                                selected = selectedDevSlot == slot,
                                onClick = { selectedDevSlot = slot },
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                haptics.tick()
                                scope.launch {
                                    viewModel.evaluateSlotAtCurrentClock(context, selectedDevSlot)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.dev_evaluate_slot))
                        }
                        OutlinedButton(
                            onClick = {
                                haptics.tick()
                                viewModel.scheduleTestAlarmIn5Seconds(selectedDevSlot)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.dev_alarm_scheduled_5s),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.dev_fire_alarm_5s))
                        }
                    }

                    lastSlotEvaluation?.let { result ->
                        Text(
                            text = describeSlotResult(context, result),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalEmpathieColors.current.onSurfaceMuted,
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            haptics.tick()
                            pendingExport = ExportRequest(ExportKind.DEBUG_LOG)
                            createDocument.launch("energy-accounting-debug-${LocalDate.now()}.log")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.export_debug_log))
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.build_id_label, BuildConfig.BUILD_ID),
                style = MaterialTheme.typography.labelSmall,
                color = LocalEmpathieColors.current.onSurfaceMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.tick()
                        if (viewModel.onBuildFooterTapped()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.dev_mode_enabled_toast),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    .padding(vertical = 8.dp),
            )
        }
    }

    if (showCreateTemplate || editingTemplate != null) {
        TemplateEditDialog(
            template = editingTemplate,
            dimensions = dimensions,
            onDismiss = {
                showCreateTemplate = false
                editingTemplate = null
            },
            onSave = { template ->
                scope.launch { viewModel.saveTemplate(template) }
                showCreateTemplate = false
                editingTemplate = null
            },
        )
    }

    if (showAddDimension) {
        AddDimensionDialog(
            onDismiss = { showAddDimension = false },
            onSave = { name ->
                scope.launch { viewModel.addCustomDimension(name) }
                showAddDimension = false
            },
        )
    }

    if (showPdfRange) {
        PdfRangeDialog(
            onDismiss = { showPdfRange = false },
            onShare = { start, end ->
                val (normalizedStart, normalizedEnd) = normalizeRange(start, end)
                sharePdf(normalizedStart, normalizedEnd)
                showPdfRange = false
            },
            onSave = { start, end ->
                val (normalizedStart, normalizedEnd) = normalizeRange(start, end)
                pendingExport = ExportRequest(ExportKind.PDF, normalizedStart, normalizedEnd)
                createDocument.launch("energy-accounting-report-${LocalDate.now()}.pdf")
                showPdfRange = false
            },
        )
    }

    if (showRestoreMode) {
        RestoreModeDialog(
            onDismiss = { showRestoreMode = false },
            onChoose = { mode ->
                pendingRestoreMode = mode
                openDocument.launch(
                    arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
                )
                showRestoreMode = false
            },
        )
    }
}

@Composable
private fun TemplateRow(
    template: ActivityTemplate,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalEmpathieColors.current
    val haptics = LocalAppHaptics.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(template.activityName, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${EnergyFormatter.formatSignedValue(template.defaultDirection, template.defaultEnergyValue)}" +
                    " · ${dimensionLabel(context, template.defaultDimension)}" +
                    " · ${template.usageCount}×",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceMuted,
            )
        }
        IconButton(onClick = {
            haptics.tick()
            onTogglePin()
        }) {
            Icon(
                imageVector = if (template.isPinned) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = stringResource(
                    if (template.isPinned) R.string.unpin else R.string.pin
                ),
            )
        }
        IconButton(onClick = {
            haptics.tick()
            onEdit()
        }) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
        }
        IconButton(onClick = {
            haptics.tick()
            onDelete()
        }) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
        }
    }
}

@Composable
private fun DimensionRow(
    dimension: EnergyDimension,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalEmpathieColors.current
    val haptics = LocalAppHaptics.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(dimensionLabel(context, dimension), style = MaterialTheme.typography.bodyMedium)
            if (dimension.isArchived) {
                Text(
                    text = stringResource(R.string.dimension_archived),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceMuted,
                )
            }
        }
        if (dimension.systemKey == null) {
            TextButton(onClick = {
                haptics.tick()
                onToggleArchive()
            }) {
                Text(
                    stringResource(
                        if (dimension.isArchived) R.string.unarchive else R.string.archive
                    )
                )
            }
            IconButton(onClick = {
                haptics.tick()
                onDelete()
            }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: CheckInReminder,
    onToggle: (Boolean) -> Unit,
    onTimeChange: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalAppHaptics.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = timeSlotLabel(context, reminder.timeSlot),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.reminder_slot_description,
                    timeSlotLabel(context, reminder.timeSlot),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = LocalEmpathieColors.current.onSurfaceMuted,
            )
        }
        TextButton(
            onClick = {
                haptics.tick()
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onTimeChange(hour, minute) },
                    reminder.hour,
                    reminder.minute,
                    DateFormat.is24HourFormat(context),
                ).show()
            }
        ) {
            Text(String.format("%02d:%02d", reminder.hour, reminder.minute))
        }
        Switch(
            checked = reminder.isEnabled,
            onCheckedChange = { enabled ->
                haptics.tick()
                onToggle(enabled)
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateEditDialog(
    template: ActivityTemplate?,
    dimensions: List<EnergyDimension>,
    onDismiss: () -> Unit,
    onSave: (ActivityTemplate) -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalAppHaptics.current
    var name by remember { mutableStateOf(template?.activityName ?: "") }
    var direction by remember { mutableStateOf(template?.defaultDirection ?: Direction.SPEND) }
    var value by remember { mutableStateOf(template?.defaultEnergyValue ?: 30) }
    var dimensionId by remember {
        mutableStateOf(template?.defaultDimension?.id ?: dimensions.firstOrNull()?.id)
    }

    // Archived dimensions stay visible only where this template already references them,
    // so the editor can never create a new template the Add sheet cannot represent.
    val selectableDimensions = dimensions.filter { dimension ->
        !dimension.isArchived || template?.defaultDimension?.id == dimension.id
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (template == null) R.string.create else R.string.edit
                ) + " · " + stringResource(R.string.activity_templates)
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.activity)) },
                    singleLine = true,
                )
                EnergyDirectionAndValueSelector(
                    direction = direction,
                    energyValue = value,
                    onDirectionChange = { direction = it },
                    onEnergyValueChange = { value = it },
                )
                Text(
                    text = stringResource(R.string.default_dimension),
                    style = MaterialTheme.typography.labelSmall,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selectableDimensions, key = { it.id }) { dimension ->
                        SelectablePill(
                            label = dimensionChipLabel(context, dimension),
                            selected = dimensionId == dimension.id,
                            onClick = { dimensionId = dimension.id },
                        )
                    }
                }
            }
        },
        confirmButton = {
            val dimension = selectableDimensions.firstOrNull { it.id == dimensionId }
            TextButton(
                enabled = name.isNotBlank() && dimension != null,
                onClick = {
                    haptics.tick()
                    if (dimension != null) {
                        onSave(
                            ActivityTemplate(
                                id = template?.id ?: 0L,
                                activityName = name,
                                defaultDirection = direction,
                                defaultEnergyValue = value,
                                defaultDimension = dimension,
                                isPinned = template?.isPinned ?: false,
                                usageCount = template?.usageCount ?: 0,
                                lastUsedEpochMillis = template?.lastUsedEpochMillis ?: 0L,
                            )
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptics.tick()
                onDismiss()
            }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun dimensionChipLabel(context: Context, dimension: EnergyDimension): String {
    val name = dimensionLabel(context, dimension)
    return if (dimension.isArchived) {
        "$name (${context.getString(R.string.dimension_archived)})"
    } else {
        name
    }
}

@Composable
private fun AddDimensionDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val haptics = LocalAppHaptics.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_custom_dimension)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.dimension_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                haptics.tick()
                onSave(name)
            }) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptics.tick()
                onDismiss()
            }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun PdfRangeDialog(
    onDismiss: () -> Unit,
    onShare: (LocalDate, LocalDate) -> Unit,
    onSave: (LocalDate, LocalDate) -> Unit,
) {
    var start by remember { mutableStateOf(LocalDate.now().minusDays(6)) }
    var end by remember { mutableStateOf(LocalDate.now()) }
    val haptics = LocalAppHaptics.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_practitioner_pdf)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField(
                    label = stringResource(R.string.start_date),
                    date = start,
                    onDateChange = { start = it },
                )
                DateField(
                    label = stringResource(R.string.end_date),
                    date = end,
                    onDateChange = { end = it },
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    haptics.tick()
                    onShare(start, end)
                }) {
                    Text(stringResource(R.string.share_pdf))
                }
                TextButton(onClick = {
                    haptics.tick()
                    onSave(start, end)
                }) {
                    Text(stringResource(R.string.save_pdf))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptics.tick()
                onDismiss()
            }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun normalizeRange(start: LocalDate, end: LocalDate): Pair<LocalDate, LocalDate> =
    if (start.isAfter(end)) end to start else start to end

private fun describeSlotResult(context: Context, result: SlotEvaluationResult): String = when (result) {
    is SlotEvaluationResult.Posted -> context.getString(
        R.string.dev_result_posted,
        timeSlotLabel(context, result.timeSlot),
        EnergyFormatter.formatDate(result.date),
    )

    is SlotEvaluationResult.SuppressedEntriesExist -> context.getString(
        R.string.dev_result_suppressed,
        result.entryCount,
        timeSlotLabel(context, result.timeSlot),
        EnergyFormatter.formatDate(result.date),
    )

    SlotEvaluationResult.BlockedNotificationsDisabled -> context.getString(R.string.dev_result_blocked)
}

@Composable
private fun RestoreModeDialog(
    onDismiss: () -> Unit,
    onChoose: (RestoreMode) -> Unit,
) {
    val haptics = LocalAppHaptics.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_json_backup)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        onChoose(RestoreMode.MERGE)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.restore_mode_merge))
                }
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        onChoose(RestoreMode.REPLACE)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.restore_mode_replace))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                haptics.tick()
                onDismiss()
            }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SectionCard(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                action?.invoke()
            }
            content()
        }
    }
}
