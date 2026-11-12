package com.empathie.energyaccounting.ui.daily

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.empathie.energyaccounting.R
import com.empathie.energyaccounting.domain.model.ActivityTemplate
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.EnergyDimension
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.domain.service.EnergyFormatter
import com.empathie.energyaccounting.ui.theme.LocalEmpathieColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AddEditEntrySheet(
    draft: EntryDraftState,
    templates: List<ActivityTemplate>,
    dimensions: List<EnergyDimension>,
    onDismiss: () -> Unit,
    onActivityChange: (String) -> Unit,
    onSituationalNoteChange: (String) -> Unit,
    onTimeSlotChange: (TimeSlot) -> Unit,
    onDirectionChange: (Direction) -> Unit,
    onEnergyValueChange: (Int) -> Unit,
    onTapDimension: (Long) -> Unit,
    onLongPressDimension: (Long) -> Unit,
    onApplyTemplate: (ActivityTemplate) -> Unit,
    onUpdateTemplateDefaultsChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val colors = LocalEmpathieColors.current
    val context = LocalContext.current
    val accent = if (draft.direction == Direction.GAIN) colors.gain else colors.spend

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(
                    if (draft.editingEntryId == null) R.string.add_entry else R.string.edit_entry
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            QuickTemplates(
                templates = templates,
                onApplyTemplate = onApplyTemplate,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = draft.activity,
                    onValueChange = onActivityChange,
                    label = { Text(stringResource(R.string.activity)) },
                    placeholder = { Text(stringResource(R.string.activity_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ActivitySuggestions(
                    activity = draft.activity,
                    templates = templates,
                    onApplyTemplate = onApplyTemplate,
                )
            }

            OutlinedTextField(
                value = draft.situationalNote,
                onValueChange = onSituationalNoteChange,
                label = { Text(stringResource(R.string.situational_note)) },
                placeholder = { Text(stringResource(R.string.situational_note_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel(stringResource(R.string.time_slot))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TimeSlot.entries.forEach { timeSlot ->
                        SelectablePill(
                            label = timeSlotLabel(context, timeSlot),
                            selected = draft.timeSlot == timeSlot,
                            onClick = { onTimeSlotChange(timeSlot) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel(stringResource(R.string.direction_and_value))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectablePill(
                        label = stringResource(R.string.direction_spend),
                        selected = draft.direction == Direction.SPEND,
                        accent = colors.spend,
                        onClick = { onDirectionChange(Direction.SPEND) },
                    )
                    SelectablePill(
                        label = stringResource(R.string.direction_gain),
                        selected = draft.direction == Direction.GAIN,
                        accent = colors.gain,
                        onClick = { onDirectionChange(Direction.GAIN) },
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(onClick = { onEnergyValueChange((draft.energyValue - 5).coerceAtLeast(1)) }) {
                        Text("-5")
                    }
                    Slider(
                        value = draft.energyValue.toFloat().coerceIn(10f, 100f),
                        onValueChange = { value -> onEnergyValueChange((value / 10f).roundToInt() * 10) },
                        valueRange = 10f..100f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = { onEnergyValueChange((draft.energyValue + 5).coerceAtMost(100)) }) {
                        Text("+5")
                    }
                    Text(
                        text = EnergyFormatter.formatSignedValue(draft.direction, draft.energyValue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel(stringResource(R.string.energy_dimensions))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    dimensions.forEach { dimension ->
                        DimensionChip(
                            label = dimensionLabel(context, dimension),
                            isPrimary = draft.dimensionChoice.primaryId == dimension.id,
                            isSecondary = dimension.id in draft.dimensionChoice.secondaryIds,
                            onClick = { onTapDimension(dimension.id) },
                            onLongClick = { onLongPressDimension(dimension.id) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.dimensions_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceMuted,
                )
            }

            if (draft.matchedTemplate != null && draft.hasModifiedTemplateDefaults) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.updateTemplateDefaults,
                        onCheckedChange = onUpdateTemplateDefaultsChange,
                    )
                    Text(
                        text = stringResource(
                            R.string.update_default_template,
                            draft.matchedTemplate.activityName,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Button(
                onClick = onSave,
                enabled = draft.activity.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }

            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.delete), color = colors.spend)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivitySuggestions(
    activity: String,
    templates: List<ActivityTemplate>,
    onApplyTemplate: (ActivityTemplate) -> Unit,
) {
    if (activity.isBlank()) return
    val query = activity.trim()
    val suggestions = templates
        .filter { it.activityName.contains(query, ignoreCase = true) && !it.activityName.equals(query, ignoreCase = true) }
        .take(4)
    if (suggestions.isEmpty()) return

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { template ->
            AssistChip(
                onClick = { onApplyTemplate(template) },
                label = { Text(template.activityName) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickTemplates(
    templates: List<ActivityTemplate>,
    onApplyTemplate: (ActivityTemplate) -> Unit,
) {
    val quickTemplates = (templates.filter { it.isPinned } + templates.filterNot { it.isPinned }).take(8)
    if (quickTemplates.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(stringResource(R.string.pinned_templates))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quickTemplates.forEach { template ->
                AssistChip(
                    onClick = { onApplyTemplate(template) },
                    leadingIcon = if (template.isPinned) {
                        {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = stringResource(R.string.pin),
                                modifier = Modifier.padding(0.dp),
                            )
                        }
                    } else {
                        null
                    },
                    label = {
                        Text(
                            "${template.activityName} (${
                                EnergyFormatter.formatSignedValue(
                                    template.defaultDirection,
                                    template.defaultEnergyValue,
                                )
                            })"
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = LocalEmpathieColors.current.onSurfaceMuted,
    )
}

@Composable
private fun SelectablePill(
    label: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    val colors = LocalEmpathieColors.current
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) accent.copy(alpha = 0.18f) else colors.surfaceRaised,
        border = BorderStroke(1.dp, if (selected) accent else colors.border),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DimensionChip(
    label: String,
    isPrimary: Boolean,
    isSecondary: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = LocalEmpathieColors.current
    val accent = MaterialTheme.colorScheme.primary
    val displayLabel = when {
        isPrimary -> "1° $label"
        isSecondary -> "2° $label"
        else -> label
    }
    val background = when {
        isPrimary -> accent.copy(alpha = 0.22f)
        else -> colors.surfaceRaised
    }
    val textColor = when {
        isPrimary -> MaterialTheme.colorScheme.onSurface
        isSecondary -> MaterialTheme.colorScheme.onSurface
        else -> colors.onSurfaceMuted
    }
    val border = when {
        isPrimary -> BorderStroke(1.dp, accent)
        isSecondary -> BorderStroke(1.dp, accent.copy(alpha = 0.5f))
        else -> BorderStroke(1.dp, colors.border)
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = background,
        border = border,
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
    ) {
        Text(
            text = displayLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
        )
    }
}
