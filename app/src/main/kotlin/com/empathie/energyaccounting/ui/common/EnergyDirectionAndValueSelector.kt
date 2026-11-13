package com.empathie.energyaccounting.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.empathie.energyaccounting.R
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.service.EnergyFormatter
import com.empathie.energyaccounting.ui.theme.LocalEmpathieColors
import kotlin.math.roundToInt

/**
 * Shared "Direction & Energy Value" block: direction pills, a [-5] value [+5] stepper
 * header, and a full-width 5-unit slider.
 *
 * Used by both the Add/Edit Entry sheet and the Manage template editor so the two screens
 * behave identically.
 */
@Composable
fun EnergyDirectionAndValueSelector(
    direction: Direction,
    energyValue: Int,
    onDirectionChange: (Direction) -> Unit,
    onEnergyValueChange: (Int) -> Unit,
) {
    val colors = LocalEmpathieColors.current
    val haptics = LocalAppHaptics.current
    val accent = if (direction == Direction.GAIN) colors.gain else colors.spend

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.direction_and_value),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceMuted,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DirectionPill(
                label = stringResource(R.string.direction_spend),
                selected = direction == Direction.SPEND,
                accent = colors.spend,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics.tick()
                    onDirectionChange(Direction.SPEND)
                },
            )
            DirectionPill(
                label = stringResource(R.string.direction_gain),
                selected = direction == Direction.GAIN,
                accent = colors.gain,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics.tick()
                    onDirectionChange(Direction.GAIN)
                },
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = {
                    haptics.tick()
                    onEnergyValueChange((energyValue - 5).coerceAtLeast(5))
                }
            ) {
                Text("-5")
            }
            Text(
                text = EnergyFormatter.formatSignedValue(direction, energyValue),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 52.dp),
            )
            FilledTonalButton(
                onClick = {
                    haptics.tick()
                    onEnergyValueChange((energyValue + 5).coerceAtMost(100))
                }
            ) {
                Text("+5")
            }
        }

        Slider(
            value = energyValue.coerceIn(5, 100).toFloat(),
            onValueChange = { value ->
                val rounded = (value / 5f).roundToInt() * 5
                if (rounded != energyValue) {
                    haptics.tick()
                    onEnergyValueChange(rounded)
                }
            },
            valueRange = 5f..100f,
            steps = 18,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DirectionPill(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalEmpathieColors.current
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) accent.copy(alpha = 0.18f) else colors.surfaceRaised,
        border = BorderStroke(1.dp, if (selected) accent else colors.border),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}
