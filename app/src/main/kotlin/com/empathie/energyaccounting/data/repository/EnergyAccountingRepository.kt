package com.empathie.energyaccounting.data.repository

import androidx.room.withTransaction
import com.empathie.energyaccounting.data.diagnostics.AppLogger
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.local.entity.ActivityTemplateEntity
import com.empathie.energyaccounting.data.local.entity.EnergyDimensionEntity
import com.empathie.energyaccounting.data.local.entity.EntryEntity
import com.empathie.energyaccounting.data.local.entity.EntryWithDimensions
import com.empathie.energyaccounting.data.local.entity.ReminderScheduleEntity
import com.empathie.energyaccounting.domain.model.ActivityTemplate
import com.empathie.energyaccounting.domain.model.CheckInReminder
import com.empathie.energyaccounting.domain.model.DailyAccount
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.EnergyDimension
import com.empathie.energyaccounting.domain.model.Entry
import com.empathie.energyaccounting.domain.model.PeriodInsights
import com.empathie.energyaccounting.domain.model.SystemDimensionKey
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.domain.service.InsightsCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.Locale

/**
 * The single gateway between the UI and the on-device ledger.
 *
 * Rules that live here rather than in the UI:
 * - entries always store their activity trimmed and attribute the whole energy value to
 *   one dimension;
 * - reusing an Activity never overwrites its template's baseline defaults unless the
 *   caller explicitly asks for it;
 * - a custom dimension that historical data references is archived, never deleted;
 * - custom dimension names are unique case-insensitively.
 */
class EnergyAccountingRepository(private val db: EnergyAccountingDatabase) {

    internal val dao = db.dao()

    fun observeDailyAccount(date: LocalDate): Flow<DailyAccount> =
        dao.observeEntriesForDate(date.toString()).map { rows ->
            DailyAccount.fromEntries(date, rows.map { it.toDomainEntry() })
        }

    fun observeEntries(startDate: LocalDate, endDate: LocalDate): Flow<List<Entry>> =
        dao.observeEntriesInRange(startDate.toString(), endDate.toString()).map { rows ->
            rows.map { it.toDomainEntry() }
        }

    suspend fun getAllEntries(): List<Entry> =
        dao.getAllEntriesWithDimensions().map { it.toDomainEntry() }

    suspend fun getAllDimensions(): List<EnergyDimension> =
        dao.getAllDimensions().map { it.toDomain() }

    suspend fun setDimensionArchived(dimensionId: Long, archived: Boolean) {
        val dimension = dao.getDimension(dimensionId) ?: return
        if (dimension.systemKey != null) return
        dao.updateDimension(dimension.copy(isArchived = archived))
    }

    fun observeActiveDimensions(): Flow<List<EnergyDimension>> =
        dao.observeActiveDimensions().map { dimensions -> dimensions.map { it.toDomain() } }

    fun observeAllDimensions(): Flow<List<EnergyDimension>> =
        dao.observeAllDimensions().map { dimensions -> dimensions.map { it.toDomain() } }

    fun observeTemplates(): Flow<List<ActivityTemplate>> =
        combine(dao.observeTemplates(), dao.observeAllDimensions()) { templates, dimensions ->
            val dimensionsById = dimensions.associateBy { it.id }
            templates.mapNotNull { template ->
                dimensionsById[template.defaultDimensionId]?.let { dimension ->
                    template.toDomain(dimension)
                }
            }
        }

    fun observePeriodInsights(startDate: LocalDate, endDate: LocalDate): Flow<PeriodInsights> =
        combine(
            dao.observeEntriesInRange(startDate.toString(), endDate.toString()),
            dao.observeAllDimensions(),
        ) { rows, dimensions ->
            InsightsCalculator.calculate(
                startDate = startDate,
                endDate = endDate,
                entries = rows.map { it.toDomainEntry() },
                allDimensions = dimensions.map { it.toDomain() },
            )
        }

    fun observeDateNetBalances(startDate: LocalDate, endDate: LocalDate): Flow<Map<LocalDate, Int>> =
        dao.observeEntriesInRange(startDate.toString(), endDate.toString()).map { rows ->
            rows.groupBy { LocalDate.parse(it.entry.dateIso) }
                .mapValues { (_, dayRows) -> dayRows.sumOf { it.entry.signedValue() } }
        }

    /**
     * Inserts or updates [entry] and keeps its Activity Template's usage statistics.
     *
     * A brand-new entry increments its template's usage count. [updateTemplateDefaults]
     * is the only way an entry's values overwrite the template's baseline defaults.
     */
    suspend fun saveEntry(entry: Entry, updateTemplateDefaults: Boolean = false): Long =
        db.withTransaction {
            val savedId = if (entry.id == 0L) {
                val newId = dao.insertEntry(entry.toEntity())
                touchTemplate(entry, incrementUsage = true, updateDefaults = updateTemplateDefaults)
                newId
            } else {
                dao.updateEntry(entry.toEntity())
                if (updateTemplateDefaults) {
                    touchTemplate(entry, incrementUsage = false, updateDefaults = true)
                }
                entry.id
            }
            AppLogger.info(
                "Repository",
                "Saved entry $savedId (${entry.date} ${entry.timeSlot.name}, ${entry.dimension.name})",
            )
            savedId
        }

    suspend fun deleteEntry(entryId: Long) {
        dao.deleteEntryById(entryId)
        AppLogger.info("Repository", "Deleted entry $entryId")
    }

    suspend fun createCustomDimension(name: String, colorIndex: Int): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "dimension name must not be blank" }
        val duplicate = dao.getAllDimensions().any {
            it.name.trim().equals(trimmed, ignoreCase = true)
        }
        require(!duplicate) { "A dimension named '$trimmed' already exists" }

        return dao.insertDimension(
            EnergyDimensionEntity(
                name = trimmed,
                systemKey = null,
                colorIndex = colorIndex,
            )
        ).also { newId ->
            AppLogger.info("Repository", "Created custom dimension $newId ('$trimmed')")
        }
    }

    /**
     * Removes a custom dimension when nothing references it, otherwise archives it so
     * historical entries and templates keep resolving it. Seeded dimensions are ignored.
     */
    suspend fun removeOrArchiveCustomDimension(dimensionId: Long) {
        db.withTransaction {
            val dimension = dao.getDimension(dimensionId) ?: return@withTransaction
            if (dimension.systemKey != null) return@withTransaction

            val usedByEntries = dao.countEntriesWithDimension(dimensionId) > 0
            val usedByTemplates = dao.getTemplates().any { it.defaultDimensionId == dimensionId }

            if (usedByEntries || usedByTemplates) {
                dao.updateDimension(dimension.copy(isArchived = true))
                AppLogger.info("Repository", "Archived custom dimension ${dimension.id}")
            } else {
                dao.deleteCustomDimension(dimensionId)
                AppLogger.info("Repository", "Deleted unused custom dimension ${dimension.id}")
            }
        }
    }

    suspend fun saveTemplate(template: ActivityTemplate): Long = db.withTransaction {
        val existing = dao.findTemplateByNormalizedName(template.normalizedName)
        if (existing != null && existing.id != template.id) {
            AppLogger.error(
                "Repository",
                "Rejected duplicate template '${template.activityName}'",
            )
            throw IllegalArgumentException(
                "A template for '${template.activityName}' already exists"
            )
        }

        val entity = template.toEntity()
        val savedId = if (template.id == 0L) {
            dao.insertTemplate(entity)
        } else {
            dao.updateTemplate(entity)
            template.id
        }
        AppLogger.info("Repository", "Saved template $savedId ('${template.activityName}')")
        savedId
    }

    suspend fun toggleTemplatePin(templateId: Long, isPinned: Boolean) {
        dao.setTemplatePinned(templateId, isPinned)
    }

    suspend fun deleteTemplate(templateId: Long) {
        dao.deleteTemplateById(templateId)
    }

    fun observeReminders(): Flow<List<CheckInReminder>> =
        dao.observeReminderSchedules().map { schedules -> schedules.mapNotNull { it.toDomainReminder() } }

    suspend fun updateReminder(reminder: CheckInReminder) {
        dao.updateReminderSchedule(
            ReminderScheduleEntity(
                timeSlot = reminder.timeSlot.name,
                hour = reminder.hour,
                minute = reminder.minute,
                isEnabled = reminder.isEnabled,
            )
        )
    }

    suspend fun getReminders(): List<CheckInReminder> =
        dao.getReminderSchedules().mapNotNull { it.toDomainReminder() }

    private suspend fun touchTemplate(entry: Entry, incrementUsage: Boolean, updateDefaults: Boolean) {
        val normalizedName = normalize(entry.activity)
        val existing = dao.findTemplateByNormalizedName(normalizedName)

        if (existing == null) {
            dao.insertTemplate(
                ActivityTemplateEntity(
                    normalizedName = normalizedName,
                    displayName = entry.activity,
                    defaultDirection = entry.direction.name,
                    defaultEnergyValue = entry.energyValue,
                    defaultDimensionId = entry.dimension.id,
                    isPinned = false,
                    usageCount = if (incrementUsage) 1 else 0,
                    lastUsedEpochMillis = entry.createdAtEpochMillis,
                )
            )
            return
        }

        dao.updateTemplate(
            existing.copy(
                displayName = if (updateDefaults) entry.activity else existing.displayName,
                defaultDirection = if (updateDefaults) entry.direction.name else existing.defaultDirection,
                defaultEnergyValue = if (updateDefaults) entry.energyValue else existing.defaultEnergyValue,
                defaultDimensionId = if (updateDefaults) entry.dimension.id else existing.defaultDimensionId,
                usageCount = existing.usageCount + if (incrementUsage) 1 else 0,
                lastUsedEpochMillis = maxOf(existing.lastUsedEpochMillis, entry.createdAtEpochMillis),
            )
        )
    }

    private fun normalize(activity: String): String = activity.trim().lowercase(Locale.ROOT)

    private fun EnergyDimensionEntity.toDomain(): EnergyDimension = EnergyDimension(
        id = id,
        name = name,
        systemKey = systemKey?.let { key -> SystemDimensionKey.entries.firstOrNull { it.name == key } },
        colorIndex = colorIndex,
        isArchived = isArchived,
    )

    private fun EntryWithDimensions.toDomainEntry(): Entry = Entry(
        id = entry.id,
        date = LocalDate.parse(entry.dateIso),
        timeSlot = TimeSlot.valueOf(entry.timeSlot),
        activity = entry.activity,
        situationalNote = entry.situationalNote,
        direction = Direction.valueOf(entry.direction),
        energyValue = entry.energyValue,
        dimension = dimension.toDomain(),
        createdAtEpochMillis = entry.createdAtEpochMillis,
    )

    private fun Entry.toEntity(): EntryEntity = EntryEntity(
        id = id,
        dateIso = date.toString(),
        timeSlot = timeSlot.name,
        activity = activity,
        situationalNote = situationalNote,
        direction = direction.name,
        energyValue = energyValue,
        dimensionId = dimension.id,
        createdAtEpochMillis = createdAtEpochMillis,
    )

    private fun ActivityTemplate.toEntity(): ActivityTemplateEntity = ActivityTemplateEntity(
        id = id,
        normalizedName = normalizedName,
        displayName = activityName,
        defaultDirection = defaultDirection.name,
        defaultEnergyValue = defaultEnergyValue,
        defaultDimensionId = defaultDimension.id,
        isPinned = isPinned,
        usageCount = usageCount,
        lastUsedEpochMillis = lastUsedEpochMillis,
    )

    private fun ActivityTemplateEntity.toDomain(dimension: EnergyDimensionEntity): ActivityTemplate =
        ActivityTemplate(
            id = id,
            activityName = displayName,
            defaultDirection = Direction.valueOf(defaultDirection),
            defaultEnergyValue = defaultEnergyValue,
            defaultDimension = dimension.toDomain(),
            isPinned = isPinned,
            usageCount = usageCount,
            lastUsedEpochMillis = lastUsedEpochMillis,
        )

    private fun EntryEntity.signedValue(): Int =
        if (Direction.valueOf(direction) == Direction.GAIN) energyValue else -energyValue

    private fun ReminderScheduleEntity.toDomainReminder(): CheckInReminder? {
        val slot = TimeSlot.entries.firstOrNull { it.name == timeSlot } ?: return null
        return CheckInReminder(
            timeSlot = slot,
            hour = hour,
            minute = minute,
            isEnabled = isEnabled,
        )
    }
}
