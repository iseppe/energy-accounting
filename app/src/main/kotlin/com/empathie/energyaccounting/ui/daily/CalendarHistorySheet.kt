package com.empathie.energyaccounting.ui.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.empathie.energyaccounting.R
import com.empathie.energyaccounting.ui.theme.LocalEmpathieColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarHistorySheet(
    month: YearMonth,
    netBalances: Map<LocalDate, Int>,
    onMonthChange: (Long) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalEmpathieColors.current
    val locale = Locale.getDefault()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.calendar_history),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonthChange(-1) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = null)
                }
                Text(
                    text = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { onMonthChange(1) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                DayOfWeek.values().forEach { dayOfWeek ->
                    Text(
                        text = dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(2),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            val firstDay = month.atDay(1)
            val leadingBlanks = firstDay.dayOfWeek.value - 1
            val cells: List<LocalDate?> =
                List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            val weeks = cells.chunked(7)
            val today = LocalDate.now()

            weeks.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (date != null) {
                                val net = netBalances[date]
                                val dotColor = when {
                                    net == null -> null
                                    net > 0 -> colors.gain
                                    net < 0 -> colors.spend
                                    else -> colors.net
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSelectDate(date) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    if (dotColor != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(dotColor),
                                        )
                                    } else {
                                        Spacer(Modifier.height(5.dp))
                                    }
                                }
                            }
                        }
                    }
                    for (missing in week.size until 7) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
