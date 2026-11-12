package com.empathie.energyaccounting.domain.model

import java.time.LocalDate

/**
 * A single atomic energy record logged within a [TimeSlot] on a [DailyAccount].
 *
 * [activity] is trimmed, [situationalNote] is trimmed to `null` when blank, and
 * [secondaryDimensions] never contains the [primaryDimension] (nor duplicates).
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
    val primaryDimension: EnergyDimension,
    secondaryDimensions: List<EnergyDimension> = emptyList(),
    val createdAtEpochMillis: Long,
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
    val secondaryDimensions: List<EnergyDimension> = secondaryDimensions
        .filterNot { it.id == primaryDimension.id }
        .distinctBy { it.id }

    fun copy(
        id: Long = this.id,
        date: LocalDate = this.date,
        timeSlot: TimeSlot = this.timeSlot,
        activity: String = this.activity,
        situationalNote: String? = this.situationalNote,
        direction: Direction = this.direction,
        energyValue: Int = this.energyValue,
        primaryDimension: EnergyDimension = this.primaryDimension,
        secondaryDimensions: List<EnergyDimension> = this.secondaryDimensions,
        createdAtEpochMillis: Long = this.createdAtEpochMillis,
    ): Entry = Entry(
        id = id,
        date = date,
        timeSlot = timeSlot,
        activity = activity,
        situationalNote = situationalNote,
        direction = direction,
        energyValue = energyValue,
        primaryDimension = primaryDimension,
        secondaryDimensions = secondaryDimensions,
        createdAtEpochMillis = createdAtEpochMillis,
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
            primaryDimension == other.primaryDimension &&
            secondaryDimensions == other.secondaryDimensions &&
            createdAtEpochMillis == other.createdAtEpochMillis
    }

    override fun hashCode(): Int = listOf(
        id,
        date,
        timeSlot,
        activity,
        situationalNote,
        direction,
        energyValue,
        primaryDimension,
        secondaryDimensions,
        createdAtEpochMillis,
    ).hashCode()

    override fun toString(): String =
        "Entry(id=$id, date=$date, timeSlot=$timeSlot, activity='$activity', " +
            "situationalNote=$situationalNote, direction=$direction, energyValue=$energyValue, " +
            "primaryDimension=$primaryDimension, secondaryDimensions=$secondaryDimensions, " +
            "createdAtEpochMillis=$createdAtEpochMillis)"

    companion object {
        const val MIN_ENERGY_VALUE = 1
        const val MAX_ENERGY_VALUE = 100
    }
}
