package com.empathie.energyaccounting.ui.daily

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.empathie.energyaccounting.ui.common.EnergyDirectionAndValueSelector
import com.empathie.energyaccounting.ui.common.LocalAppHaptics
import com.empathie.energyaccounting.ui.theme.LocalEmpathieColors

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
    onSelectDimension: (Long) -> Unit,
    onApplyTemplate: (ActivityTemplate) -> Unit,
    onUpdateTemplateDefaultsChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean = false,
    onDelete: (() -> Unit)?,
) {
    val colors = LocalEmpathieColors.current
    val context = LocalContext.current
    val haptics = LocalAppHaptics.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val slotLabel = timeSlotLabel(context, draft.timeSlot)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
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
                    if (draft.editingEntryId == null) R.string.add_entry_for_slot
                    else R.string.edit_entry_for_slot,
                    slotLabel,
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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TimeSlot.entries) { timeSlot ->
                        SelectablePill(
                            label = timeSlotLabel(context, timeSlot),
                            selected = draft.timeSlot == timeSlot,
                            onClick = { onTimeSlotChange(timeSlot) },
                        )
                    }
                }
            }

            EnergyDirectionAndValueSelector(
                direction = draft.direction,
                energyValue = draft.energyValue,
                onDirectionChange = onDirectionChange,
                onEnergyValueChange = onEnergyValueChange,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel(stringResource(R.string.energy_dimensions))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        visibleDimensions(dimensions, draft.selectedDimensionId),
                        key = { it.id },
                    ) { dimension ->
                        DimensionPill(
                            label = dimensionLabel(context, dimension),
                            selected = draft.selectedDimensionId == dimension.id,
                            onClick = { onSelectDimension(dimension.id) },
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
                        onCheckedChange = { checked ->
                            haptics.tick()
                            onUpdateTemplateDefaultsChange(checked)
                        },
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
                onClick = {
                    haptics.tick()
                    onSave()
                },
                enabled = draft.activity.isNotBlank() &&
                    draft.selectedDimensionId != null &&
                    !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }

            if (onDelete != null) {
                TextButton(
                    onClick = {
                        haptics.tick()
                        onDelete()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
        .filter {
            it.activityName.contains(query, ignoreCase = true) &&
                !it.activityName.equals(query, ignoreCase = true)
        }
        .take(4)
    if (suggestions.isEmpty()) return

    val haptics = LocalAppHaptics.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { template ->
            AssistChip(
                onClick = {
                    haptics.tick()
                    onApplyTemplate(template)
                },
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
    if (templates.isEmpty()) return

    var showAllTemplates by remember { mutableStateOf(false) }
    val visible = templates.take(MAX_VISIBLE_TEMPLATES)
    val haptics = LocalAppHaptics.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(stringResource(R.string.pinned_templates))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.id }) { template ->
                TemplateChip(template = template, onClick = { onApplyTemplate(template) })
            }
            if (templates.size > MAX_VISIBLE_TEMPLATES) {
                item(key = "more") {
                    AssistChip(
                        onClick = {
                            haptics.tick()
                            showAllTemplates = true
                        },
                        label = { Text(stringResource(R.string.more_templates)) },
                    )
                }
            }
        }
    }

    if (showAllTemplates) {
        AllTemplatesDialog(
            templates = templates,
            onSelect = { template ->
                onApplyTemplate(template)
                showAllTemplates = false
            },
            onDismiss = { showAllTemplates = false },
        )
    }
}

@Composable
private fun TemplateChip(template: ActivityTemplate, onClick: () -> Unit) {
    val haptics = LocalAppHaptics.current
    AssistChip(
        onClick = {
            haptics.tick()
            onClick()
        },
        leadingIcon = if (template.isPinned) {
            {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = stringResource(R.string.pin),
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

@Composable
private fun AllTemplatesDialog(
    templates: List<ActivityTemplate>,
    onSelect: (ActivityTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.all_templates)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                templates.forEach { template ->
                    TemplateChip(template = template, onClick = { onSelect(template) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
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
    val haptics = LocalAppHaptics.current
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) accent.copy(alpha = 0.18f) else colors.surfaceRaised,
        border = BorderStroke(1.dp, if (selected) accent else colors.border),
        modifier = Modifier.clickable {
            haptics.tick()
            onClick()
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun DimensionPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalEmpathieColors.current
    val haptics = LocalAppHaptics.current
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) accent.copy(alpha = 0.22f) else colors.surfaceRaised,
        border = BorderStroke(1.dp, if (selected) accent else colors.border),
        modifier = Modifier.clickable {
            haptics.tick()
            onClick()
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                colors.onSurfaceMuted
            },
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
        )
    }
}

private const val MAX_VISIBLE_TEMPLATES = 5

/**
 * New entries only offer active dimensions, while editing keeps the draft's own archived
 * dimension visible so its selection is never silently hidden.
 */
internal fun visibleDimensions(
    dimensions: List<EnergyDimension>,
    selectedDimensionId: Long?,
): List<EnergyDimension> = dimensions.filter { dimension ->
    !dimension.isArchived || dimension.id == selectedDimensionId
}
