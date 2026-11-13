package com.empathie.energyaccounting.domain.model

import java.time.LocalDate

/**
 * The entries of one [TimeSlot] plus its zero-based gain, spend and net balance.
 */
data class TimeSlotLedger(
    val timeSlot: TimeSlot,
    val entries: List<Entry>,
    val totalGain: Int,
    val totalSpend: Int,
    val netBalance: Int,
)

/**
 * The zero-based energy ledger for one calendar day.
 *
 * Every day starts at `0`; [netBalance] is [totalGain] minus [totalSpend].
 */
data class DailyAccount(
    val date: LocalDate,
    val slots: Map<TimeSlot, TimeSlotLedger>,
    val totalGain: Int,
    val totalSpend: Int,
    val netBalance: Int,
) {
    fun ledgerFor(timeSlot: TimeSlot): TimeSlotLedger =
        slots.getValue(timeSlot)

    companion object {
        fun fromEntries(date: LocalDate, entries: List<Entry>): DailyAccount {
            val slots = TimeSlot.entries.associateWith { timeSlot ->
                val slotEntries = entries
                    .filter { it.timeSlot == timeSlot }
                    .sortedBy { it.createdAtEpochMillis }
                val gain = slotEntries.sumOf { it.signedValue().coerceAtLeast(0) }
                val spend = slotEntries.sumOf { (-it.signedValue()).coerceAtLeast(0) }
                TimeSlotLedger(
                    timeSlot = timeSlot,
                    entries = slotEntries,
                    totalGain = gain,
                    totalSpend = spend,
                    netBalance = gain - spend,
                )
            }

            val totalGain = slots.values.sumOf { it.totalGain }
            val totalSpend = slots.values.sumOf { it.totalSpend }
            return DailyAccount(
                date = date,
                slots = slots,
                totalGain = totalGain,
                totalSpend = totalSpend,
                netBalance = totalGain - totalSpend,
            )
        }
    }
}

/**
 * Positive for a GAIN, negative for a SPEND.
 */
fun Entry.signedValue(): Int = when (direction) {
    Direction.GAIN -> energyValue
    Direction.SPEND -> -energyValue
}
