package com.empathie.energyaccounting.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.empathie.energyaccounting.data.local.entity.ActivityTemplateEntity
import com.empathie.energyaccounting.data.local.entity.EnergyDimensionEntity
import com.empathie.energyaccounting.data.local.entity.EntryEntity
import com.empathie.energyaccounting.data.local.entity.EntryWithDimensions
import com.empathie.energyaccounting.data.local.entity.ReminderScheduleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Reactive access to the on-device ledger.
 *
 * An abstract class (rather than an interface) so the `@Transaction` helpers below are
 * plain concrete methods Room can generate an implementation for.
 */
@Dao
abstract class EnergyAccountingDao {

    // ---- Dimensions -------------------------------------------------------

    @Query("SELECT * FROM energy_dimensions ORDER BY colorIndex ASC, id ASC")
    abstract fun observeAllDimensions(): Flow<List<EnergyDimensionEntity>>

    @Query("SELECT * FROM energy_dimensions WHERE isArchived = 0 ORDER BY colorIndex ASC, id ASC")
    abstract fun observeActiveDimensions(): Flow<List<EnergyDimensionEntity>>

    @Query("SELECT * FROM energy_dimensions WHERE id = :dimensionId")
    abstract suspend fun getDimension(dimensionId: Long): EnergyDimensionEntity?

    @Query("SELECT * FROM energy_dimensions ORDER BY colorIndex ASC, id ASC")
    abstract suspend fun getAllDimensions(): List<EnergyDimensionEntity>

    @Query("DELETE FROM energy_dimensions WHERE systemKey IS NULL")
    abstract suspend fun deleteAllCustomDimensions(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertDimensions(dimensions: List<EnergyDimensionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertDimension(dimension: EnergyDimensionEntity): Long

    @Update
    abstract suspend fun updateDimension(dimension: EnergyDimensionEntity)

    @Query("DELETE FROM energy_dimensions WHERE id = :dimensionId AND systemKey IS NULL")
    abstract suspend fun deleteCustomDimension(dimensionId: Long): Int

    @Query("SELECT COUNT(*) FROM entries WHERE dimensionId = :dimensionId")
    abstract suspend fun countEntriesWithDimension(dimensionId: Long): Int

    // ---- Entries ----------------------------------------------------------

    @Transaction
    @Query("SELECT * FROM entries WHERE dateIso = :dateIso ORDER BY createdAtEpochMillis ASC, id ASC")
    abstract fun observeEntriesForDate(dateIso: String): Flow<List<EntryWithDimensions>>

    @Transaction
    @Query(
        "SELECT * FROM entries WHERE dateIso BETWEEN :startDateIso AND :endDateIso " +
            "ORDER BY dateIso ASC, createdAtEpochMillis ASC, id ASC"
    )
    abstract fun observeEntriesInRange(
        startDateIso: String,
        endDateIso: String,
    ): Flow<List<EntryWithDimensions>>

    @Query("SELECT DISTINCT dateIso FROM entries ORDER BY dateIso ASC")
    abstract fun observeActiveDates(): Flow<List<String>>

    @Transaction
    @Query("SELECT * FROM entries ORDER BY dateIso ASC, createdAtEpochMillis ASC, id ASC")
    abstract suspend fun getAllEntriesWithDimensions(): List<EntryWithDimensions>

    @Query("DELETE FROM entries")
    abstract suspend fun deleteAllEntries()

    @Query("SELECT * FROM entries WHERE id = :entryId")
    abstract suspend fun getEntry(entryId: Long): EntryEntity?

    @Query("SELECT COUNT(*) FROM entries WHERE dateIso = :dateIso AND timeSlot = :timeSlot")
    abstract suspend fun countEntriesForSlot(dateIso: String, timeSlot: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertEntry(entry: EntryEntity): Long

    @Update
    abstract suspend fun updateEntry(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :entryId")
    abstract suspend fun deleteEntryById(entryId: Long)

    // ---- Activity templates ----------------------------------------------

    @Query(
        "SELECT * FROM activity_templates " +
            "ORDER BY isPinned DESC, usageCount DESC, lastUsedEpochMillis DESC, displayName ASC"
    )
    abstract fun observeTemplates(): Flow<List<ActivityTemplateEntity>>

    @Query("SELECT * FROM activity_templates WHERE normalizedName = :normalizedName LIMIT 1")
    abstract suspend fun findTemplateByNormalizedName(normalizedName: String): ActivityTemplateEntity?

    @Query("SELECT * FROM activity_templates")
    abstract suspend fun getTemplates(): List<ActivityTemplateEntity>

    @Query("DELETE FROM activity_templates")
    abstract suspend fun deleteAllTemplates()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertTemplate(template: ActivityTemplateEntity): Long

    @Update
    abstract suspend fun updateTemplate(template: ActivityTemplateEntity)

    @Query("UPDATE activity_templates SET isPinned = :isPinned WHERE id = :templateId")
    abstract suspend fun setTemplatePinned(templateId: Long, isPinned: Boolean)

    @Query("DELETE FROM activity_templates WHERE id = :templateId")
    abstract suspend fun deleteTemplateById(templateId: Long)

    // ---- Reminder schedules ----------------------------------------------

    @Query(
        "SELECT * FROM reminder_schedules ORDER BY " +
            "CASE timeSlot " +
            "WHEN 'EARLY_MORNING' THEN 0 WHEN 'LATE_MORNING' THEN 1 " +
            "WHEN 'AFTERNOON' THEN 2 ELSE 3 END"
    )
    abstract fun observeReminderSchedules(): Flow<List<ReminderScheduleEntity>>

    @Query("SELECT * FROM reminder_schedules")
    abstract suspend fun getReminderSchedules(): List<ReminderScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertReminderSchedules(schedules: List<ReminderScheduleEntity>)

    @Update
    abstract suspend fun updateReminderSchedule(schedule: ReminderScheduleEntity)
}
