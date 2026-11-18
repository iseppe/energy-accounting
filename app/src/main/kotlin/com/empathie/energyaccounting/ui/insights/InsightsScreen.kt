package com.empathie.energyaccounting.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.empathie.energyaccounting.R
import com.empathie.energyaccounting.domain.model.DimensionImpactSummary
import com.empathie.energyaccounting.domain.model.PeriodInsights
import com.empathie.energyaccounting.domain.model.TimeSlotImpactSummary
import com.empathie.energyaccounting.domain.service.EnergyFormatter
import com.empathie.energyaccounting.ui.common.DateField
import com.empathie.energyaccounting.ui.common.LocalAppHaptics
import com.empathie.energyaccounting.ui.daily.dimensionLabel
import com.empathie.energyaccounting.ui.daily.timeSlotLabel
import com.empathie.energyaccounting.ui.theme.LocalEmpathieColors

@Composable
fun InsightsScreen(viewModel: InsightsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val filteredEntries by viewModel.filteredEntries.collectAsStateWithLifecycle()
    val colors = LocalEmpathieColors.current
    val haptics = LocalAppHaptics.current
    val hasCurrentInsights = insights.startDate == state.startDate && insights.endDate == state.endDate

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = state.period == InsightsPeriod.TODAY,
                        onClick = {
                            haptics.tick()
                            viewModel.selectPeriod(InsightsPeriod.TODAY)
                        },
                        label = { Text(stringResource(R.string.today_period)) },
                    )
                }
                item {
                    FilterChip(
                        selected = state.period == InsightsPeriod.LAST_7_DAYS,
                        onClick = {
                            haptics.tick()
                            viewModel.selectPeriod(InsightsPeriod.LAST_7_DAYS)
                        },
                        label = { Text(stringResource(R.string.last_7_days)) },
                    )
                }
                item {
                    FilterChip(
                        selected = state.period == InsightsPeriod.LAST_30_DAYS,
                        onClick = {
                            haptics.tick()
                            viewModel.selectPeriod(InsightsPeriod.LAST_30_DAYS)
                        },
                        label = { Text(stringResource(R.string.last_30_days)) },
                    )
                }
                item {
                    FilterChip(
                        selected = state.period == InsightsPeriod.CUSTOM,
                        onClick = {
                            haptics.tick()
                            viewModel.selectPeriod(InsightsPeriod.CUSTOM)
                        },
                        label = { Text(stringResource(R.string.custom_range)) },
                    )
                }
            }
        }

        if (state.period == InsightsPeriod.CUSTOM) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField(
                        label = stringResource(R.string.start_date),
                        date = state.startDate,
                        onDateChange = viewModel::setStartDate,
                        modifier = Modifier.weight(1f),
                    )
                    DateField(
                        label = stringResource(R.string.end_date),
                        date = state.endDate,
                        onDateChange = viewModel::setEndDate,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            PeriodSummaryCard(
                insights = insights,
                state = state,
                onPrevious = viewModel::goToPreviousPeriod,
                onNext = viewModel::goToNextPeriod,
                onClearFilter = { state.selectedDimensionId?.let(viewModel::toggleDimensionFilter) },
            )
        }

        if (hasCurrentInsights) item { EnergyTrendChartCard(insights) }

        if (hasCurrentInsights) item {
            SectionCard(title = stringResource(R.string.spend_by_dimension)) {
                val spendDimensions = insights.byDimension.filter { it.spend > 0 }
                if (spendDimensions.isEmpty()) {
                    EmptyNote()
                } else {
                    spendDimensions.forEach { summary ->
                        DimensionBar(
                            summary = summary,
                            spend = true,
                            color = colors.spend,
                            selected = summary.dimension.id == state.selectedDimensionId,
                            onClick = {
                                haptics.tick()
                                viewModel.toggleDimensionFilter(summary.dimension.id)
                            },
                        )
                    }
                }
            }
        }

        if (hasCurrentInsights) item {
            SectionCard(title = stringResource(R.string.gain_by_dimension)) {
                val gainDimensions = insights.byDimension.filter { it.gain > 0 }
                if (gainDimensions.isEmpty()) {
                    EmptyNote()
                } else {
                    gainDimensions.forEach { summary ->
                        DimensionBar(
                            summary = summary,
                            spend = false,
                            color = colors.gain,
                            selected = summary.dimension.id == state.selectedDimensionId,
                            onClick = {
                                haptics.tick()
                                viewModel.toggleDimensionFilter(summary.dimension.id)
                            },
                        )
                    }
                }
            }
        }

        if (hasCurrentInsights && state.selectedDimensionId != null) {
            item {
                SectionCard(title = stringResource(R.string.tab_daily_account)) {
                    val currentEntries = filteredEntries.filter {
                        it.dimension.id == state.selectedDimensionId &&
                            !it.date.isBefore(state.startDate) && !it.date.isAfter(state.endDate)
                    }
                    if (currentEntries.isEmpty()) {
                        EmptyNote()
                    } else {
                        val context = LocalContext.current
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            currentEntries.forEach { entry ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.activity, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = "${EnergyFormatter.formatDate(entry.date)} · " +
                                                timeSlotLabel(context, entry.timeSlot) + " · " +
                                                dimensionLabel(context, entry.dimension),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.onSurfaceMuted,
                                        )
                                    }
                                    Text(
                                        text = EnergyFormatter.formatSignedValue(
                                            entry.direction,
                                            entry.energyValue,
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (entry.direction ==
                                            com.empathie.energyaccounting.domain.model.Direction.GAIN
                                        ) {
                                            colors.gain
                                        } else {
                                            colors.spend
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (hasCurrentInsights) item {
            SectionCard(title = stringResource(R.string.by_time_slot)) {
                val showAverage = insights.startDate != insights.endDate
                insights.byTimeSlot.forEach { summary ->
                    TimeSlotRow(summary, showAverage = showAverage)
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
internal fun PeriodSummaryCard(
    insights: PeriodInsights,
    state: InsightsUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClearFilter: () -> Unit,
) {
    val colors = LocalEmpathieColors.current
    val haptics = LocalAppHaptics.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (state.period != InsightsPeriod.CUSTOM) {
                    IconButton(onClick = { haptics.tick(); onPrevious() }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.previous_period))
                    }
                }
                Text(
                    text = "${EnergyFormatter.formatDate(state.startDate)} – " +
                        EnergyFormatter.formatDate(state.endDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                if (state.period != InsightsPeriod.CUSTOM) {
                    IconButton(onClick = { haptics.tick(); onNext() }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.next_period))
                    }
                }
            }
            if (state.selectedDimensionId != null) {
                val context = LocalContext.current
                val dimension = insights.byDimension.firstOrNull { it.dimension.id == state.selectedDimensionId }?.dimension
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (dimension == null) stringResource(R.string.dimension_filter_active) else
                            stringResource(R.string.dimension_filter_label, dimensionLabel(context, dimension)),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { haptics.tick(); onClearFilter() }) {
                        Text(stringResource(R.string.clear_dimension_filter))
                    }
                }
            }
            if (insights.startDate == state.startDate && insights.endDate == state.endDate) {
                PeriodSummaryValues(insights)
            } else {
                Text(
                    text = stringResource(R.string.loading_insights),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PeriodSummaryValues(insights: PeriodInsights) {
    val colors = LocalEmpathieColors.current
    Row(modifier = Modifier.fillMaxWidth()) {
        SummaryValue(
            stringResource(R.string.total_gain),
            EnergyFormatter.formatGainTotal(insights.totalGain),
            colors.gain,
            Modifier.weight(1f),
        )
        SummaryValue(
            stringResource(R.string.total_spend),
            EnergyFormatter.formatSpendTotal(insights.totalSpend),
            colors.spend,
            Modifier.weight(1f),
        )
        SummaryValue(
            stringResource(R.string.net_balance),
            EnergyFormatter.formatNetBalance(insights.netBalance),
            colors.net,
            Modifier.weight(1f),
        )
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        SummaryValue(
            stringResource(R.string.average_daily_net),
            EnergyFormatter.formatNetBalance(insights.averageDailyNet),
            colors.net,
            Modifier.weight(1f),
        )
        Spacer(Modifier.weight(1f))
        SummaryValue(
            stringResource(R.string.active_days),
            insights.activeDaysCount.toString(),
            colors.onSurfaceMuted,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryValue(
    label: String,
    value: String,
    color: Color,
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun EmptyNote() {
    Text(
        text = stringResource(R.string.no_entries),
        style = MaterialTheme.typography.bodySmall,
        color = LocalEmpathieColors.current.onSurfaceMuted,
    )
}

@Composable
private fun DimensionBar(
    summary: DimensionImpactSummary,
    spend: Boolean,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val value = if (spend) summary.spend else summary.gain
    val share = if (spend) summary.spendSharePercent else summary.gainSharePercent
    val label = dimensionLabel(context, summary.dimension)
    val track = LocalEmpathieColors.current.surfaceSunken

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        Row {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$value · $share%",
                style = MaterialTheme.typography.labelSmall,
                color = LocalEmpathieColors.current.onSurfaceMuted,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(track),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((share.coerceIn(0, 100)) / 100f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun TimeSlotRow(summary: TimeSlotImpactSummary, showAverage: Boolean) {
    val context = LocalContext.current
    val colors = LocalEmpathieColors.current
    val netColor = when {
        summary.netBalance > 0 -> colors.gain
        summary.netBalance < 0 -> colors.spend
        else -> colors.net
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = timeSlotLabel(context, summary.timeSlot),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = EnergyFormatter.formatGainTotal(summary.totalGain),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = colors.gain,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(38.dp),
        )
        Text(
            text = " / ",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceMuted,
        )
        Text(
            text = EnergyFormatter.formatSpendTotal(summary.totalSpend),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = colors.spend,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(42.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = EnergyFormatter.formatNetBalance(summary.netBalance),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = netColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(42.dp),
        )
        if (showAverage) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = EnergyFormatter.formatNetBalance(summary.averageNetPerActiveDay) +
                    context.getString(R.string.per_day),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = colors.onSurfaceMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.width(62.dp),
            )
        }
    }
}
