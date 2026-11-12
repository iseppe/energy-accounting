package com.empathie.energyaccounting.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.empathie.energyaccounting.data.local.entity.ActivityTemplateEntity
import com.empathie.energyaccounting.data.local.entity.EnergyDimensionEntity
import com.empathie.energyaccounting.data.local.entity.EntryEntity
import com.empathie.energyaccounting.data.local.entity.EntrySecondaryDimensionCrossRef
import com.empathie.energyaccounting.data.local.entity.ReminderScheduleEntity
import com.empathie.energyaccounting.domain.model.SystemDimensionKey
import com.empathie.energyaccounting.domain.model.TimeSlot

@Database(
    entities = [
        EnergyDimensionEntity::class,
        EntryEntity::class,
        EntrySecondaryDimensionCrossRef::class,
        ActivityTemplateEntity::class,
        ReminderScheduleEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class EnergyAccountingDatabase : RoomDatabase() {

    abstract fun dao(): EnergyAccountingDao

    /**
     * Inserts the five default dimensions and four disabled reminder schedules.
     *
     * Idempotent: both inserts ignore rows that already exist (dimensions by the unique
     * `systemKey` index, schedules by their `timeSlot` primary key), so it is safe to call
     * on every app and test start.
     */
    suspend fun ensureSeeded() {
        dao().insertDimensions(defaultDimensions())
        dao().insertReminderSchedules(defaultReminderSchedules())
    }

    private fun defaultDimensions(): List<EnergyDimensionEntity> = listOf(
        EnergyDimensionEntity(
            name = "Physical",
            systemKey = SystemDimensionKey.PHYSICAL.name,
            colorIndex = 0,
        ),
        EnergyDimensionEntity(
            name = "Cognitive",
            systemKey = SystemDimensionKey.COGNITIVE.name,
            colorIndex = 1,
        ),
        EnergyDimensionEntity(
            name = "Sensory",
            systemKey = SystemDimensionKey.SENSORY.name,
            colorIndex = 2,
        ),
        EnergyDimensionEntity(
            name = "Social",
            systemKey = SystemDimensionKey.SOCIAL.name,
            colorIndex = 3,
        ),
        EnergyDimensionEntity(
            name = "Emotional",
            systemKey = SystemDimensionKey.EMOTIONAL.name,
            colorIndex = 4,
        ),
    )

    private fun defaultReminderSchedules(): List<ReminderScheduleEntity> = listOf(
        ReminderScheduleEntity(TimeSlot.EARLY_MORNING.name, hour = 9, minute = 30, isEnabled = false),
        ReminderScheduleEntity(TimeSlot.LATE_MORNING.name, hour = 12, minute = 30, isEnabled = false),
        ReminderScheduleEntity(TimeSlot.AFTERNOON.name, hour = 17, minute = 30, isEnabled = false),
        ReminderScheduleEntity(TimeSlot.EVENING.name, hour = 21, minute = 0, isEnabled = false),
    )
}
