package com.empathie.energyaccounting.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.empathie.energyaccounting.data.local.entity.ActivityTemplateEntity
import com.empathie.energyaccounting.data.local.entity.EnergyDimensionEntity
import com.empathie.energyaccounting.data.local.entity.EntryEntity
import com.empathie.energyaccounting.data.local.entity.ReminderScheduleEntity
import com.empathie.energyaccounting.domain.model.SystemDimensionKey
import com.empathie.energyaccounting.domain.model.TimeSlot

@Database(
    entities = [
        EnergyDimensionEntity::class,
        EntryEntity::class,
        ActivityTemplateEntity::class,
        ReminderScheduleEntity::class,
    ],
    version = 2,
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

    companion object {
        /**
         * v1 stored a primary dimension plus a secondary cross-ref table; v2 keeps only
         * the primary dimension on each row. Existing entries and templates are copied
         * across so a phone upgrade never loses data.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `entries_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dateIso` TEXT NOT NULL,
                        `timeSlot` TEXT NOT NULL,
                        `activity` TEXT NOT NULL,
                        `situationalNote` TEXT,
                        `direction` TEXT NOT NULL,
                        `energyValue` INTEGER NOT NULL,
                        `dimensionId` INTEGER NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        FOREIGN KEY(`dimensionId`) REFERENCES `energy_dimensions`(`id`) ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `entries_new`
                        (`id`, `dateIso`, `timeSlot`, `activity`, `situationalNote`, `direction`,
                         `energyValue`, `dimensionId`, `createdAtEpochMillis`)
                    SELECT `id`, `dateIso`, `timeSlot`, `activity`, `situationalNote`, `direction`,
                           `energyValue`, `primaryDimensionId`, `createdAtEpochMillis`
                    FROM `entries`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `entries`")
                db.execSQL("ALTER TABLE `entries_new` RENAME TO `entries`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_dateIso` ON `entries` (`dateIso`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_dimensionId` ON `entries` (`dimensionId`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_entries_dateIso_timeSlot` " +
                        "ON `entries` (`dateIso`, `timeSlot`)"
                )
                db.execSQL("DROP TABLE IF EXISTS `entry_secondary_dimensions`")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `activity_templates_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `normalizedName` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `defaultDirection` TEXT NOT NULL,
                        `defaultEnergyValue` INTEGER NOT NULL,
                        `defaultDimensionId` INTEGER NOT NULL,
                        `isPinned` INTEGER NOT NULL,
                        `usageCount` INTEGER NOT NULL,
                        `lastUsedEpochMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `activity_templates_new`
                        (`id`, `normalizedName`, `displayName`, `defaultDirection`,
                         `defaultEnergyValue`, `defaultDimensionId`, `isPinned`, `usageCount`,
                         `lastUsedEpochMillis`)
                    SELECT `id`, `normalizedName`, `displayName`, `defaultDirection`,
                           `defaultEnergyValue`, `defaultPrimaryDimensionId`, `isPinned`, `usageCount`,
                           `lastUsedEpochMillis`
                    FROM `activity_templates`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `activity_templates`")
                db.execSQL("ALTER TABLE `activity_templates_new` RENAME TO `activity_templates`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_activity_templates_normalizedName` " +
                        "ON `activity_templates` (`normalizedName`)"
                )
            }
        }
    }
}
