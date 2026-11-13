package com.empathie.energyaccounting.ui.insights

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.empathie.energyaccounting.R
import com.empathie.energyaccounting.domain.model.EnergyChartPoint
import com.empathie.energyaccounting.domain.model.PeriodInsights
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.ui.theme.LocalEmpathieColors
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Diverging Gain/Spend bars with a connected Net Balance trend line.
 *
 * A single-day period plots the four Time Slots; a multi-day period plots one bucket per
 * calendar day (including unlogged days), so the timeline never skips a date.
 */
@Composable
fun EnergyTrendChartCard(insights: PeriodInsights) {
    val colors = LocalEmpathieColors.current
    val context = LocalContext.current
    val points = insights.chartPoints
    val labelPaint = remember(colors.onSurfaceMuted) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = colors.onSurfaceMuted.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.energy_trend_chart),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(colors.gain, stringResource(R.string.total_gain))
                LegendDot(colors.spend, stringResource(R.string.total_spend))
                LegendDot(colors.net, stringResource(R.string.net_balance))
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                if (points.isEmpty()) return@Canvas

                val left = 4.dp.toPx()
                val right = size.width - 4.dp.toPx()
                val top = 10.dp.toPx()
                val bottom = size.height - 20.dp.toPx()
                val centerY = (top + bottom) / 2f
                val halfHeight = (bottom - top) / 2f
                val maxMagnitude = maxOf(
                    10f,
                    points.maxOfOrNull { point ->
                        maxOf(point.gain, point.spend, abs(point.netBalance))
                    }?.toFloat() ?: 10f,
                )
                val slotWidth = (right - left) / points.size
                val barWidth = (slotWidth * 0.28f)
                    .coerceIn(3f, 14.dp.toPx())

                drawLine(
                    color = colors.border,
                    start = Offset(left, centerY),
                    end = Offset(right, centerY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )

                points.forEachIndexed { index, point ->
                    val centerX = left + slotWidth * (index + 0.5f)
                    val gainHeight = (point.gain / maxMagnitude) * halfHeight
                    val spendHeight = (point.spend / maxMagnitude) * halfHeight

                    if (gainHeight > 0f) {
                        drawRoundRect(
                            color = colors.gain,
                            topLeft = Offset(centerX - barWidth - 1.dp.toPx(), centerY - gainHeight),
                            size = Size(barWidth, gainHeight),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                    }
                    if (spendHeight > 0f) {
                        drawRoundRect(
                            color = colors.spend,
                            topLeft = Offset(centerX + 1.dp.toPx(), centerY),
                            size = Size(barWidth, spendHeight),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                    }
                }

                val netPoints = points.mapIndexed { index, point ->
                    val centerX = left + slotWidth * (index + 0.5f)
                    val y = centerY - (point.netBalance / maxMagnitude) * halfHeight
                    Offset(centerX, y)
                }

                if (netPoints.size > 1) {
                    val path = Path().apply {
                        moveTo(netPoints.first().x, netPoints.first().y)
                        netPoints.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = path,
                        color = colors.net,
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
                netPoints.forEach { point ->
                    drawCircle(color = colors.net, radius = 3.5.dp.toPx(), center = point)
                }

                val labelPaint = labelPaint
                labelPaint.textSize = 10.sp.toPx()
                val labelStep = maxOf(1, ceil(points.size / 6f).toInt())
                points.forEachIndexed { index, point ->
                    if (index % labelStep == 0) {
                        val centerX = left + slotWidth * (index + 0.5f)
                        drawContext.canvas.nativeCanvas.drawText(
                            chartLabel(context, point),
                            centerX,
                            size.height - 2.dp.toPx(),
                            labelPaint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalEmpathieColors.current.onSurfaceMuted,
        )
    }
}

private fun chartLabel(context: Context, point: EnergyChartPoint): String =
    point.timeSlot?.let { shortSlotLabel(context, it) } ?: point.label

private fun shortSlotLabel(context: Context, timeSlot: TimeSlot): String = context.getString(
    when (timeSlot) {
        TimeSlot.EARLY_MORNING -> R.string.slot_short_early
        TimeSlot.LATE_MORNING -> R.string.slot_short_late
        TimeSlot.AFTERNOON -> R.string.slot_short_afternoon
        TimeSlot.EVENING -> R.string.slot_short_evening
    }
)
