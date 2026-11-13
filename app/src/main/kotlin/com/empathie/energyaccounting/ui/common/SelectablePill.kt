package com.empathie.energyaccounting.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.empathie.energyaccounting.ui.theme.LocalEmpathieColors

/**
 * A flush selectable chip used across the entry sheet and Manage tab.
 *
 * Unlike Material's FilterChip it has no minimum-touch-target inset, so its border lines
 * up with section titles and labels on the same content grid.
 */
@Composable
fun SelectablePill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    val colors = LocalEmpathieColors.current
    val haptics = LocalAppHaptics.current
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) accent.copy(alpha = 0.18f) else colors.surfaceRaised,
        border = BorderStroke(1.dp, if (selected) accent else colors.border),
        modifier = modifier.clickable {
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
