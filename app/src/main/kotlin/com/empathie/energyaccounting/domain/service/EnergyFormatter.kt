package com.empathie.energyaccounting.domain.service

import com.empathie.energyaccounting.domain.model.Direction
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Single source of truth for user-facing date and signed-value rendering.
 *
 * Dates are always shown as `dd/MM/yyyy`; data and math keep plain integers, so
 * a spend renders as `-30`, a gain as `+10`, and a zero balance as `0`.
 */
object EnergyFormatter {
    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")

    fun formatDate(date: LocalDate): String = date.format(DATE_FORMATTER)

    fun parseDate(ddMmYyyy: String): LocalDate = LocalDate.parse(ddMmYyyy, DATE_FORMATTER)

    fun formatSignedValue(direction: Direction, value: Int): String = when (direction) {
        Direction.SPEND -> "-$value"
        Direction.GAIN -> "+$value"
    }

    fun formatNetBalance(net: Int): String = when {
        net > 0 -> "+$net"
        net < 0 -> net.toString()
        else -> "0"
    }

    /**
     * Formats a day total's gain without the misleading `+0` on an empty day.
     */
    fun formatGainTotal(gain: Int): String = if (gain == 0) "0" else "+$gain"

    /**
     * Formats a day total's spend without the misleading `-0` on an empty day.
     */
    fun formatSpendTotal(spend: Int): String = if (spend == 0) "0" else "-$spend"
}
