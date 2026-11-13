package com.empathie.energyaccounting.ui.manage

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import com.empathie.energyaccounting.ui.common.DateField
import com.empathie.energyaccounting.ui.daily.dimensionLabel
import com.empathie.energyaccounting.ui.daily.timeSlotLabel
import com.empathie.energyaccounting.ui.theme.LocalEmpathieColors
import com.empathie.energyaccounting.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

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
    val message by viewModel.message.collectAsStateWithLifecycle()

    var editingTemplate by remember { mutableStateOf<ActivityTemplate?>(null) }
    var showCreateTemplate by remember { mutableStateOf(false) }
    var showAddDimension by remember { mutableStateOf(false) }
    var showPdfRange by remember { mutableStateOf(false) }
    var showRestoreMode by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<ExportRequest?>(null) }
    var pendingRestoreMode by remember { mutableStateOf<RestoreMode?>(null) }
    var pendingEnableReminder by remember { mutableStateOf<CheckInReminder?>(null) }

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
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        item {
            SectionCard(title = stringResource(R.string.appearance)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                    }
                                )
                            },
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
                        FilterChip(
                            selected = appLanguage == language,
                            onClick = {
                                viewModel.setAppLanguage(language)
                                activity?.recreate()
                            },
                            label = {
                                Text(
                                    when (language) {
                                        AppLanguage.SYSTEM -> stringResource(R.string.language_system)
                                        AppLanguage.ENGLISH -> stringResource(R.string.language_english)
                                        AppLanguage.ITALIAN -> stringResource(R.string.language_italian)
                                    }
                                )
                            },
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.activity_templates)) {
                TextButton(onClick = { showCreateTemplate = true }) {
                    Text(stringResource(R.string.create))
                }
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
            SectionCard(title = stringResource(R.string.energy_dimensions_management)) {
                TextButton(onClick = { showAddDimension = true }) {
                    Text(stringResource(R.string.add_custom_dimension))
                }
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
                                pendingEnableReminder = reminder
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
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
                    onClick = { showPdfRange = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.export_practitioner_pdf))
                }
                OutlinedButton(
                    onClick = {
                        pendingExport = ExportRequest(ExportKind.CSV)
                        createDocument.launch("energy-accounting-${LocalDate.now()}.csv")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.export_csv))
                }
                OutlinedButton(
                    onClick = {
                        pendingExport = ExportRequest(ExportKind.JSON)
                        createDocument.launch("energy-accounting-backup-${LocalDate.now()}.json")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.export_json_backup))
                }
                OutlinedButton(
                    onClick = {
                        pendingExport = ExportRequest(ExportKind.DEBUG_LOG)
                        createDocument.launch("energy-accounting-debug-${LocalDate.now()}.log")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.export_debug_log))
                }
                OutlinedButton(
                    onClick = { showRestoreMode = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.restore_json_backup))
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
            onExport = { start, end ->
                val (normalizedStart, normalizedEnd) =
                    if (start.isAfter(end)) end to start else start to end
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
        IconButton(onClick = onTogglePin) {
            Icon(
                imageVector = if (template.isPinned) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = stringResource(
                    if (template.isPinned) R.string.unpin else R.string.pin
                ),
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
        }
        IconButton(onClick = onDelete) {
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
            TextButton(onClick = onToggleArchive) {
                Text(
                    stringResource(
                        if (dimension.isArchived) R.string.unarchive else R.string.archive
                    )
                )
            }
            IconButton(onClick = onDelete) {
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
        Switch(checked = reminder.isEnabled, onCheckedChange = onToggle)
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = direction == Direction.SPEND,
                        onClick = { direction = Direction.SPEND },
                        label = { Text(stringResource(R.string.direction_spend)) },
                    )
                    FilterChip(
                        selected = direction == Direction.GAIN,
                        onClick = { direction = Direction.GAIN },
                        label = { Text(stringResource(R.string.direction_gain)) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = value.toFloat().coerceIn(5f, 100f),
                        onValueChange = { value = ((it / 5f).roundToInt() * 5).coerceIn(5, 100) },
                        valueRange = 5f..100f,
                        steps = 18,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = EnergyFormatter.formatSignedValue(direction, value),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = stringResource(R.string.default_dimension),
                    style = MaterialTheme.typography.labelSmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectableDimensions.forEach { dimension ->
                        FilterChip(
                            selected = dimensionId == dimension.id,
                            onClick = { dimensionId = dimension.id },
                            label = { Text(dimensionChipLabel(context, dimension)) },
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
            TextButton(onClick = onDismiss) {
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
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name) }) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun PdfRangeDialog(
    onDismiss: () -> Unit,
    onExport: (LocalDate, LocalDate) -> Unit,
) {
    var start by remember { mutableStateOf(LocalDate.now().minusDays(6)) }
    var end by remember { mutableStateOf(LocalDate.now()) }
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
            TextButton(onClick = { onExport(start, end) }) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun RestoreModeDialog(
    onDismiss: () -> Unit,
    onChoose: (RestoreMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_json_backup)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onChoose(RestoreMode.MERGE) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.restore_mode_merge))
                }
                OutlinedButton(
                    onClick = { onChoose(RestoreMode.REPLACE) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.restore_mode_replace))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}
