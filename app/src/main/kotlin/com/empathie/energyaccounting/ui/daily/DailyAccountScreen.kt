package com.empathie.energyaccounting.ui.daily

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.empathie.energyaccounting.R
import com.empathie.energyaccounting.domain.model.DailyAccount
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.EnergyDimension
import com.empathie.energyaccounting.domain.model.Entry
import com.empathie.energyaccounting.domain.model.SystemDimensionKey
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.domain.model.TimeSlotLedger
import com.empathie.energyaccounting.domain.service.EnergyFormatter
import com.empathie.energyaccounting.ui.theme.LocalEmpathieColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DailyAccountScreen(viewModel: DailyAccountViewModel) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val draft by viewModel.draftState.collectAsStateWithLifecycle()
    val isCalendarOpen by viewModel.isCalendarOpen.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val dimensions by viewModel.dimensions.collectAsStateWithLifecycle()
    val calendarMonth by viewModel.calendarMonth.collectAsStateWithLifecycle()
    val calendarNetBalances by viewModel.calendarNetBalances.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            DateBar(
                selectedDate = selectedDate,
                isToday = selectedDate == today,
                onPreviousDay = viewModel::goToPreviousDay,
                onNextDay = viewModel::goToNextDay,
                onOpenCalendar = viewModel::openCalendar,
                onGoToToday = viewModel::goToToday,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openNewEntrySheet() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_entry)) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DailySummaryCard(account)
            }
            items(TimeSlot.entries) { timeSlot ->
                TimeSlotCard(
                    ledger = account.ledgerFor(timeSlot),
                    onAddToSlot = { viewModel.openNewEntrySheet(timeSlot) },
                    onEntryClick = viewModel::openEditEntrySheet,
                )
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (isCalendarOpen) {
        CalendarHistorySheet(
            month = calendarMonth,
            netBalances = calendarNetBalances,
            onMonthChange = viewModel::changeCalendarMonth,
            onSelectDate = viewModel::selectDate,
            onDismiss = viewModel::closeCalendar,
        )
    }

    draft?.let { draftState ->
        AddEditEntrySheet(
            draft = draftState,
            templates = templates,
            dimensions = dimensions,
            onDismiss = viewModel::dismissDraft,
            onActivityChange = viewModel::onActivityChange,
            onSituationalNoteChange = viewModel::onSituationalNoteChange,
            onTimeSlotChange = viewModel::onTimeSlotChange,
            onDirectionChange = viewModel::onDirectionChange,
            onEnergyValueChange = viewModel::onEnergyValueChange,
            onTapDimension = viewModel::onTapDimension,
            onLongPressDimension = viewModel::onLongPressDimension,
            onApplyTemplate = viewModel::applyTemplate,
            onUpdateTemplateDefaultsChange = viewModel::onUpdateTemplateDefaultsChange,
            onSave = { scope.launch { viewModel.saveDraft() } },
            onDelete = draftState.editingEntryId?.let { entryId ->
                { scope.launch { viewModel.deleteEntry(entryId) } }
            },
        )
    }
}

@Composable
private fun DateBar(
    selectedDate: LocalDate,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onOpenCalendar: () -> Unit,
    onGoToToday: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPreviousDay) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.previous_day))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenCalendar)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = EnergyFormatter.formatDate(selectedDate),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalEmpathieColors.current.onSurfaceMuted,
                )
            }
            if (isToday) {
                TextButton(onClick = onOpenCalendar) {
                    Text(stringResource(R.string.today))
                }
            } else {
                TextButton(onClick = onGoToToday) {
                    Text(stringResource(R.string.today))
                }
            }
            IconButton(onClick = onNextDay) {
                Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.next_day))
            }
        }
    }
}

@Composable
private fun DailySummaryCard(account: DailyAccount) {
    val colors = LocalEmpathieColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SummaryPill(
                label = stringResource(R.string.total_gain),
                value = "+${account.totalGain}",
                color = colors.gain,
            )
            SummaryPill(
                label = stringResource(R.string.total_spend),
                value = "-${account.totalSpend}",
                color = colors.spend,
            )
            SummaryPill(
                label = stringResource(R.string.net_balance),
                value = EnergyFormatter.formatNetBalance(account.netBalance),
                color = colors.net,
            )
        }
    }
}

@Composable
private fun SummaryPill(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalEmpathieColors.current.onSurfaceMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun TimeSlotCard(
    ledger: TimeSlotLedger,
    onAddToSlot: () -> Unit,
    onEntryClick: (Entry) -> Unit,
) {
    val colors = LocalEmpathieColors.current
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeSlotLabel(context, ledger.timeSlot),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = EnergyFormatter.formatNetBalance(ledger.netBalance),
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        ledger.netBalance > 0 -> colors.gain
                        ledger.netBalance < 0 -> colors.spend
                        else -> colors.net
                    },
                )
                IconButton(onClick = onAddToSlot) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(
                            R.string.add_entry_to_slot,
                            timeSlotLabel(context, ledger.timeSlot),
                        ),
                    )
                }
            }

            if (ledger.entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_entries),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ledger.entries.forEach { entry ->
                        EntryRow(entry = entry, onClick = { onEntryClick(entry) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: Entry, onClick: () -> Unit) {
    val colors = LocalEmpathieColors.current
    val context = LocalContext.current
    val directionColor = if (entry.direction == Direction.GAIN) colors.gain else colors.spend
    val primaryLabel = dimensionLabel(context, entry.primaryDimension)
    val secondaryLabels = entry.secondaryDimensions.map { dimensionLabel(context, it) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceSunken)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(directionColor),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.activity,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            entry.situationalNote?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
            }
            Text(
                text = buildDimensionSummary(primaryLabel, secondaryLabels),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceMuted,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = EnergyFormatter.formatSignedValue(entry.direction, entry.energyValue),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = directionColor,
            textAlign = TextAlign.End,
        )
    }
}

private fun buildDimensionSummary(primary: String, secondaries: List<String>): String = buildString {
    append("1° ").append(primary)
    secondaries.forEach { secondary ->
        append("   ·   2° ").append(secondary)
    }
}

internal fun timeSlotLabel(context: Context, timeSlot: TimeSlot): String = context.getString(
    when (timeSlot) {
        TimeSlot.EARLY_MORNING -> R.string.time_slot_early_morning
        TimeSlot.LATE_MORNING -> R.string.time_slot_late_morning
        TimeSlot.AFTERNOON -> R.string.time_slot_afternoon
        TimeSlot.EVENING -> R.string.time_slot_evening
    }
)

internal fun dimensionLabel(context: Context, dimension: EnergyDimension): String = when (dimension.systemKey) {
    SystemDimensionKey.PHYSICAL -> context.getString(R.string.dimension_physical)
    SystemDimensionKey.COGNITIVE -> context.getString(R.string.dimension_cognitive)
    SystemDimensionKey.SENSORY -> context.getString(R.string.dimension_sensory)
    SystemDimensionKey.SOCIAL -> context.getString(R.string.dimension_social)
    SystemDimensionKey.EMOTIONAL -> context.getString(R.string.dimension_emotional)
    null -> dimension.name
}
