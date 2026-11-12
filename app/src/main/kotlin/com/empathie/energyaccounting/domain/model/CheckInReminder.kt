package com.empathie.energyaccounting.domain.model

/**
 * A local, per-[TimeSlot] reminder configuration.
 *
 * [hour] / [minute] are wall-clock values in the device's current time zone.
 */
data class CheckInReminder(
    val timeSlot: TimeSlot,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean,
) {
    init {
        require(hour in 0..23) { "hour must be between 0 and 23, was $hour" }
        require(minute in 0..59) { "minute must be between 0 and 59, was $minute" }
    }
}
