package com.empathie.energyaccounting.ui.daily

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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
import com.empathie.energyaccounting.ui.common.LocalAppHaptics
import com.empathie.energyaccounting.ui.theme.LocalEmpathieColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DailyAccountScreen(viewModel: DailyAccountViewModel) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val draft by viewModel.draftState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isCalendarOpen by viewModel.isCalendarOpen.collectAsStateWithLifecycle()
    val pendingFutureSlot by viewModel.pendingFutureSlotWarning.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val dimensions by viewModel.dimensions.collectAsStateWithLifecycle()
    val calendarMonth by viewModel.calendarMonth.collectAsStateWithLifecycle()
    val calendarNetBalances by viewModel.calendarNetBalances.collectAsStateWithLifecycle()
    val today by viewModel.todayDate.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val haptics = LocalAppHaptics.current
    val listState = rememberLazyListState()
    val dragState = remember(selectedDate) { EntryDragState() }
    val latestAccount by rememberUpdatedState(account)
    var listCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val edgeSize = with(LocalDensity.current) { 64.dp.toPx() }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(dragState.draggedEntry?.id) {
        while (dragState.draggedEntry != null) {
            val bounds = listCoordinates?.takeIf { it.isAttached }?.boundsInRoot()
            if (bounds != null) {
                val y = dragState.pointer.y
                val amount = when {
                    y < bounds.top + edgeSize -> -16f * ((bounds.top + edgeSize - y) / edgeSize).coerceIn(0f, 1f)
                    y > bounds.bottom - edgeSize -> 16f * ((y - (bounds.bottom - edgeSize)) / edgeSize).coerceIn(0f, 1f)
                    else -> 0f
                }
                if (amount != 0f) {
                    listState.scrollBy(amount)
                    dragState.update(dragState.pointer, latestAccount)
                }
            }
            delay(16)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DateBar(
                selectedDate = selectedDate,
                today = today,
                isToday = selectedDate == today,
                onPreviousDay = viewModel::goToPreviousDay,
                onNextDay = viewModel::goToNextDay,
                onOpenCalendar = viewModel::openCalendar,
                onGoToToday = viewModel::goToToday,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    haptics.tick()
                    viewModel.requestNewEntrySheet()
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_entry)) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .onGloballyPositioned { listCoordinates = it }
                .pointerInput(dragState) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        val root = listCoordinates?.takeIf { it.isAttached }?.localToRoot(held.position)
                            ?: return@awaitEachGesture
                        if (!dragState.begin(root, latestAccount)) return@awaitEachGesture
                        haptics.tick()
                        try {
                            while (true) {
                                // Intercept the drag before LazyColumn's scroll gesture consumes it.
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (event.type == PointerEventType.Release && !change.isConsumed) {
                                        change.consume()
                                        val entryId = dragState.draggedEntry?.id
                                        val release = listCoordinates?.takeIf { it.isAttached }?.localToRoot(change.position)
                                        if (release != null) dragState.update(release, latestAccount)
                                        val target = if (release != null) dragState.target else null
                                        if (entryId != null && target != null) {
                                            scope.launch { viewModel.moveEntry(entryId, target.slot, target.index) }
                                        }
                                    }
                                    break
                                }
                                if (change.positionChanged()) {
                                    change.consume()
                                    listCoordinates?.takeIf { it.isAttached }?.localToRoot(change.position)?.let {
                                        dragState.update(it, latestAccount)
                                    }
                                }
                            }
                        } finally {
                            dragState.clear()
                        }
                    }
                },
            state = listState,
            contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DailySummaryCard(account)
            }
            items(TimeSlot.entries, key = { it.name }) { timeSlot ->
                TimeSlotCard(
                    ledger = account.ledgerFor(timeSlot),
                    account = account,
                    dragState = dragState,
                    onAddToSlot = { viewModel.requestNewEntrySheet(timeSlot) },
                    onEntryClick = viewModel::openEditEntrySheet,
                    onMove = { entryId, slot, index -> scope.launch { viewModel.moveEntry(entryId, slot, index) } },
                )
            }
        }
    }

    if (isCalendarOpen) {
        CalendarHistorySheet(
            month = calendarMonth,
            selectedDate = selectedDate,
            today = today,
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
            onSelectDimension = viewModel::onSelectDimension,
            onApplyTemplate = viewModel::applyTemplate,
            onUpdateTemplateDefaultsChange = viewModel::onUpdateTemplateDefaultsChange,
            onSave = { scope.launch { viewModel.saveDraft() } },
            isSaving = isSaving,
            onDelete = draftState.editingEntryId?.let { entryId ->
                { pendingDeleteId = entryId }
            },
        )
    }

    pendingDeleteId?.let { entryId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_entry_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteId = null
                        scope.launch { viewModel.deleteEntry(entryId) }
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingFutureSlot?.let { timeSlot ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = viewModel::dismissFutureEntryWarning,
            title = { Text(stringResource(R.string.future_date_warning_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.future_date_warning_body,
                        EnergyFormatter.formatDate(selectedDate),
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmFutureEntryWarning) {
                    Text(stringResource(R.string.continue_action))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissFutureEntryWarning) {
                    Text(stringResource(R.string.go_back))
                }
            },
        )
    }
}

@Composable
private fun DateBar(
    selectedDate: LocalDate,
    today: LocalDate,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onOpenCalendar: () -> Unit,
    onGoToToday: () -> Unit,
) {
    val haptics = LocalAppHaptics.current
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            IconButton(
                onClick = {
                    haptics.tick()
                    onPreviousDay()
                },
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.previous_day))
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        haptics.tick()
                        onOpenCalendar()
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = EnergyFormatter.formatDate(selectedDate),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalEmpathieColors.current.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                )
            }

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TodayDayIcon(
                    dayOfMonth = today.dayOfMonth,
                    isToday = isToday,
                    onClick = {
                        haptics.tick()
                        onGoToToday()
                    },
                )
                IconButton(onClick = {
                    haptics.tick()
                    onNextDay()
                }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.next_day))
                }
            }
        }
    }
}

/**
 * A calendar-style badge showing today's day number; tapping it jumps back to today.
 * It is always present so the centered date never shifts when navigating days.
 */
@Composable
private fun TodayDayIcon(
    dayOfMonth: Int,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalEmpathieColors.current
    val accent = if (isToday) colors.onSurfaceMuted else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(enabled = !isToday, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .size(width = 28.dp, height = 30.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.5.dp, accent, RoundedCornerShape(8.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(accent.copy(alpha = 0.20f))
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
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
        ) {
            SummaryPill(
                label = stringResource(R.string.total_gain),
                value = EnergyFormatter.formatGainTotal(account.totalGain),
                color = colors.gain,
                modifier = Modifier.weight(1f),
            )
            SummaryPill(
                label = stringResource(R.string.total_spend),
                value = EnergyFormatter.formatSpendTotal(account.totalSpend),
                color = colors.spend,
                modifier = Modifier.weight(1f),
            )
            SummaryPill(
                label = stringResource(R.string.net_balance),
                value = EnergyFormatter.formatNetBalance(account.netBalance),
                color = colors.net,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryPill(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalEmpathieColors.current.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TimeSlotCard(
    ledger: TimeSlotLedger,
    account: DailyAccount,
    dragState: EntryDragState,
    onAddToSlot: () -> Unit,
    onEntryClick: (Entry) -> Unit,
    onMove: (Long, TimeSlot, Int) -> Unit,
) {
    val colors = LocalEmpathieColors.current
    val context = LocalContext.current
    val haptics = LocalAppHaptics.current
    DisposableEffect(dragState, ledger.timeSlot) {
        onDispose { dragState.slotBounds.remove(ledger.timeSlot) }
    }
    val destinationEntries = ledger.entries.filterNot { it.id == dragState.draggedEntry?.id }
    val target = dragState.target?.takeIf { it.slot == ledger.timeSlot }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().onGloballyPositioned {
            dragState.slotBounds[ledger.timeSlot] = it.boundsInRoot()
        },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = timeSlotLabel(context, ledger.timeSlot),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = EnergyFormatter.formatNetBalance(ledger.netBalance),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = when {
                        ledger.netBalance > 0 -> colors.gain
                        ledger.netBalance < 0 -> colors.spend
                        else -> colors.net
                    },
                    textAlign = TextAlign.End,
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        haptics.tick()
                        onAddToSlot()
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(
                            R.string.add_entry_to_slot,
                            timeSlotLabel(context, ledger.timeSlot),
                        ),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (ledger.entries.isEmpty()) {
                if (target?.index == 0) DropIndicator()
                Text(
                    text = stringResource(R.string.no_entries),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ledger.entries.forEachIndexed { index, entry ->
                        DisposableEffect(dragState, entry.id) {
                            onDispose { dragState.entryBounds.remove(entry.id) }
                        }
                        val destinationIndex = destinationEntries.indexOfFirst { it.id == entry.id }
                        if (destinationIndex >= 0 && target?.index == destinationIndex) DropIndicator()
                        val actions = buildList {
                            if (index > 0) add(CustomAccessibilityAction(context.getString(R.string.move_entry_up)) {
                                onMove(entry.id, ledger.timeSlot, index - 1); true
                            })
                            if (index < ledger.entries.lastIndex) add(CustomAccessibilityAction(context.getString(R.string.move_entry_down)) {
                                onMove(entry.id, ledger.timeSlot, index + 1); true
                            })
                            TimeSlot.entries.filterNot { it == ledger.timeSlot }.forEach { slot ->
                                add(CustomAccessibilityAction(context.getString(R.string.move_entry_to_slot, timeSlotLabel(context, slot))) {
                                    onMove(entry.id, slot, account.ledgerFor(slot).entries.size); true
                                })
                            }
                        }
                        EntryRow(
                            entry = entry,
                            modifier = Modifier
                                .onGloballyPositioned { dragState.entryBounds[entry.id] = it.boundsInRoot() }
                                .alpha(if (dragState.draggedEntry?.id == entry.id) 0.55f else 1f)
                                .semantics { customActions = actions },
                            onClick = {
                                haptics.tick()
                                onEntryClick(entry)
                            },
                        )
                    }
                    if (target?.index == destinationEntries.size) DropIndicator()
                }
            }
        }
    }
}

@Composable
private fun DropIndicator() {
    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
}

@Composable
private fun EntryRow(entry: Entry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalEmpathieColors.current
    val context = LocalContext.current
    val directionColor = if (entry.direction == Direction.GAIN) colors.gain else colors.spend

    Row(
        modifier = modifier
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
                text = dimensionLabel(context, entry.dimension),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceMuted,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = EnergyFormatter.formatSignedValue(entry.direction, entry.energyValue),
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = directionColor,
            textAlign = TextAlign.End,
        )
        // The slot subtotal sits 36.dp from the card content edge (4.dp gap + 32.dp add
        // button); this row is inset by its own 10.dp padding, so 26.dp lines the value up.
        Spacer(Modifier.width(26.dp))
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
