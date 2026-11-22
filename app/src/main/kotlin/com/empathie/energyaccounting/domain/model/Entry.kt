package com.empathie.energyaccounting.domain.model

import java.time.LocalDate

/**
 * A single atomic energy record logged within a [TimeSlot] on a [DailyAccount].
 *
 * [activity] is trimmed, [situationalNote] is trimmed to `null` when blank, and the
 * whole [energyValue] is attributed to the single [dimension].
 *
 * Written as a regular class (not a data class) because normalization must happen
 * inside the constructor, which a data class's `val`-only primary constructor forbids.
 */
class Entry(
    val id: Long = 0L,
    val date: LocalDate,
    val timeSlot: TimeSlot,
    activity: String,
    situationalNote: String?,
    val direction: Direction,
    val energyValue: Int,
    val dimension: EnergyDimension,
    val createdAtEpochMillis: Long,
    val positionInSlot: Long = 0L,
) {
    init {
        require(energyValue in MIN_ENERGY_VALUE..MAX_ENERGY_VALUE) {
            "energyValue must be between $MIN_ENERGY_VALUE and $MAX_ENERGY_VALUE, was $energyValue"
        }
        require(activity.isNotBlank()) { "activity must not be blank" }
        require(createdAtEpochMillis >= 0L) { "createdAtEpochMillis must not be negative" }
    }

    val activity: String = activity.trim()
    val situationalNote: String? = situationalNote?.trim()?.takeIf(String::isNotEmpty)

    fun copy(
        id: Long = this.id,
        date: LocalDate = this.date,
        timeSlot: TimeSlot = this.timeSlot,
        activity: String = this.activity,
        situationalNote: String? = this.situationalNote,
        direction: Direction = this.direction,
        energyValue: Int = this.energyValue,
        dimension: EnergyDimension = this.dimension,
        createdAtEpochMillis: Long = this.createdAtEpochMillis,
        positionInSlot: Long = this.positionInSlot,
    ): Entry = Entry(
        id = id,
        date = date,
        timeSlot = timeSlot,
        activity = activity,
        situationalNote = situationalNote,
        direction = direction,
        energyValue = energyValue,
        dimension = dimension,
        createdAtEpochMillis = createdAtEpochMillis,
        positionInSlot = positionInSlot,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Entry) return false
        return id == other.id &&
            date == other.date &&
            timeSlot == other.timeSlot &&
            activity == other.activity &&
            situationalNote == other.situationalNote &&
            direction == other.direction &&
            energyValue == other.energyValue &&
            dimension == other.dimension &&
            createdAtEpochMillis == other.createdAtEpochMillis &&
            positionInSlot == other.positionInSlot
    }

    override fun hashCode(): Int = listOf(
        id,
        date,
        timeSlot,
        activity,
        situationalNote,
        direction,
        energyValue,
        dimension,
        createdAtEpochMillis,
        positionInSlot,
    ).hashCode()

    override fun toString(): String =
        "Entry(id=$id, date=$date, timeSlot=$timeSlot, activity='$activity', " +
            "situationalNote=$situationalNote, direction=$direction, energyValue=$energyValue, " +
            "dimension=$dimension, createdAtEpochMillis=$createdAtEpochMillis, positionInSlot=$positionInSlot)"

    companion object {
        const val MIN_ENERGY_VALUE = 1
        const val MAX_ENERGY_VALUE = 100
    }
}
