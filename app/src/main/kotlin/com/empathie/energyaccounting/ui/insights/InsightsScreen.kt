package com.empathie.energyaccounting.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.empathie.energyaccounting.ui.daily.dimensionLabel
import com.empathie.energyaccounting.ui.daily.timeSlotLabel
import com.empathie.energyaccounting.ui.theme.LocalEmpathieColors

@Composable
fun InsightsScreen(viewModel: InsightsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val dimensions by viewModel.dimensions.collectAsStateWithLifecycle()
    val filteredEntries by viewModel.filteredEntries.collectAsStateWithLifecycle()
    val colors = LocalEmpathieColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.period == InsightsPeriod.LAST_7_DAYS,
                    onClick = { viewModel.selectPeriod(InsightsPeriod.LAST_7_DAYS) },
                    label = { Text(stringResource(R.string.last_7_days)) },
                )
                FilterChip(
                    selected = state.period == InsightsPeriod.LAST_30_DAYS,
                    onClick = { viewModel.selectPeriod(InsightsPeriod.LAST_30_DAYS) },
                    label = { Text(stringResource(R.string.last_30_days)) },
                )
                FilterChip(
                    selected = state.period == InsightsPeriod.CUSTOM,
                    onClick = { viewModel.selectPeriod(InsightsPeriod.CUSTOM) },
                    label = { Text(stringResource(R.string.custom_range)) },
                )
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

        item { PeriodSummaryCard(insights) }

        item {
            SectionCard(title = stringResource(R.string.spend_by_primary_dimension)) {
                val spendDimensions = insights.byDimension.filter { it.primarySpend > 0 }
                if (spendDimensions.isEmpty()) {
                    EmptyNote()
                } else {
                    spendDimensions.forEach { summary ->
                        DimensionBar(summary = summary, spend = true, color = colors.spend)
                    }
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.gain_by_primary_dimension)) {
                val gainDimensions = insights.byDimension.filter { it.primaryGain > 0 }
                if (gainDimensions.isEmpty()) {
                    EmptyNote()
                } else {
                    gainDimensions.forEach { summary ->
                        DimensionBar(summary = summary, spend = false, color = colors.gain)
                    }
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.secondary_dimension_occurrences)) {
                val secondary = insights.byDimension.filter { it.secondaryOccurrenceCount > 0 }
                if (secondary.isEmpty()) {
                    EmptyNote()
                } else {
                    SecondaryDimensionOccurrences(
                        summaries = secondary,
                        selectedDimensionId = state.selectedDimensionId,
                        onToggle = viewModel::toggleDimensionFilter,
                    )
                }
            }
        }

        if (state.selectedDimensionId != null) {
            item {
                SectionCard(title = stringResource(R.string.tab_daily_account)) {
                    if (filteredEntries.isEmpty()) {
                        EmptyNote()
                    } else {
                        val context = LocalContext.current
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredEntries.forEach { entry ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.activity, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = "${EnergyFormatter.formatDate(entry.date)} · " +
                                                timeSlotLabel(context, entry.timeSlot) + " · " +
                                                dimensionLabel(context, entry.primaryDimension),
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

        item {
            SectionCard(title = stringResource(R.string.by_time_slot)) {
                insights.byTimeSlot.forEach { summary ->
                    TimeSlotRow(summary)
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PeriodSummaryCard(insights: PeriodInsights) {
    val colors = LocalEmpathieColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "${EnergyFormatter.formatDate(insights.startDate)} – " +
                    EnergyFormatter.formatDate(insights.endDate),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceMuted,
            )
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                SummaryValue(stringResource(R.string.total_gain), "+${insights.totalGain}", colors.gain)
                SummaryValue(stringResource(R.string.total_spend), "-${insights.totalSpend}", colors.spend)
                SummaryValue(
                    stringResource(R.string.net_balance),
                    EnergyFormatter.formatNetBalance(insights.netBalance),
                    colors.net,
                )
            }
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                SummaryValue(
                    stringResource(R.string.average_daily_net),
                    EnergyFormatter.formatNetBalance(insights.averageDailyNet),
                    colors.net,
                )
                SummaryValue(
                    stringResource(R.string.active_days),
                    insights.activeDaysCount.toString(),
                    colors.onSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String, color: Color) {
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
private fun DimensionBar(summary: DimensionImpactSummary, spend: Boolean, color: Color) {
    val context = LocalContext.current
    val value = if (spend) summary.primarySpend else summary.primaryGain
    val share = if (spend) summary.primarySpendSharePercent else summary.primaryGainSharePercent
    val label = dimensionLabel(context, summary.dimension)
    val track = LocalEmpathieColors.current.surfaceSunken

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SecondaryDimensionOccurrences(
    summaries: List<DimensionImpactSummary>,
    selectedDimensionId: Long?,
    onToggle: (Long) -> Unit,
) {
    val context = LocalContext.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        summaries.forEach { summary ->
            FilterChip(
                selected = summary.dimension.id == selectedDimensionId,
                onClick = { onToggle(summary.dimension.id) },
                label = {
                    Text("${dimensionLabel(context, summary.dimension)} · ${summary.secondaryOccurrenceCount}")
                },
            )
        }
    }
}

@Composable
private fun TimeSlotRow(summary: TimeSlotImpactSummary) {
    val context = LocalContext.current
    val colors = LocalEmpathieColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = timeSlotLabel(context, summary.timeSlot),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "+${summary.totalGain} / -${summary.totalSpend}  ·  " +
                EnergyFormatter.formatNetBalance(summary.netBalance),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceMuted,
        )
    }
}
