package com.empathie.energyaccounting.data.local.entity

import androidx.room.Entity

/**
 * Per-[com.empathie.energyaccounting.domain.model.TimeSlot] local reminder configuration.
 */
@Entity(
    tableName = "reminder_schedules",
    primaryKeys = ["timeSlot"],
)
data class ReminderScheduleEntity(
    val timeSlot: String,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean,
)
