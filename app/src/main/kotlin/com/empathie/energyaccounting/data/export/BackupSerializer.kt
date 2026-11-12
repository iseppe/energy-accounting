package com.empathie.energyaccounting.data.export

import androidx.room.withTransaction
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.local.entity.ActivityTemplateEntity
import com.empathie.energyaccounting.data.local.entity.EnergyDimensionEntity
import com.empathie.energyaccounting.data.local.entity.EntryEntity
import com.empathie.energyaccounting.data.local.entity.ReminderScheduleEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * Versioned JSON backup of every local table, plus the restore engine.
 *
 * Dimension ids are only meaningful inside one database, so both modes build an old-id
 * to new-id map first and rewrite every entry and template reference through it.
 */
class BackupSerializer(private val db: EnergyAccountingDatabase) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun exportJson(): String {
        val dao = db.dao()
        val payload = BackupPayload(
            dimensions = dao.getAllDimensions().map {
                DimensionRecord(
                    id = it.id,
                    systemKey = it.systemKey,
                    name = it.name,
                    colorIndex = it.colorIndex,
                    isArchived = it.isArchived,
                )
            },
            templates = dao.getTemplates().map {
                TemplateRecord(
                    normalizedName = it.normalizedName,
                    displayName = it.displayName,
                    defaultDirection = it.defaultDirection,
                    defaultEnergyValue = it.defaultEnergyValue,
                    defaultPrimaryDimensionId = it.defaultPrimaryDimensionId,
                    defaultSecondaryDimensionIds = decodeIds(it.defaultSecondaryDimensionIdsCsv),
                    isPinned = it.isPinned,
                    usageCount = it.usageCount,
                    lastUsedEpochMillis = it.lastUsedEpochMillis,
                )
            },
            entries = dao.getAllEntriesWithDimensions().map {
                EntryRecord(
                    dateIso = it.entry.dateIso,
                    timeSlot = it.entry.timeSlot,
                    activity = it.entry.activity,
                    situationalNote = it.entry.situationalNote,
                    direction = it.entry.direction,
                    energyValue = it.entry.energyValue,
                    primaryDimensionId = it.entry.primaryDimensionId,
                    secondaryDimensionIds = it.secondaryDimensions.map { dimension -> dimension.id },
                    createdAtEpochMillis = it.entry.createdAtEpochMillis,
                )
            },
            reminders = dao.getReminderSchedules().map {
                ReminderRecord(
                    timeSlot = it.timeSlot,
                    hour = it.hour,
                    minute = it.minute,
                    isEnabled = it.isEnabled,
                )
            },
        )
        return json.encodeToString(BackupPayload.serializer(), payload)
    }

    suspend fun restoreJson(jsonString: String, mode: RestoreMode) {
        val payload = json.decodeFromString(BackupPayload.serializer(), jsonString)
        require(payload.schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported backup schema version ${payload.schemaVersion}, expected $CURRENT_SCHEMA_VERSION"
        }

        db.withTransaction {
            val dao = db.dao()
            if (mode == RestoreMode.REPLACE) {
                dao.deleteAllEntries()
                dao.deleteAllTemplates()
                dao.deleteAllCustomDimensions()
            }

            val idMap = remapDimensions(payload.dimensions, mode)
            insertTemplates(payload.templates, idMap)
            insertEntries(payload.entries, idMap, deduplicate = mode == RestoreMode.MERGE)
            applyReminders(payload.reminders)
        }
    }

    private suspend fun remapDimensions(
        records: List<DimensionRecord>,
        mode: RestoreMode,
    ): Map<Long, Long> {
        val dao = db.dao()
        val existing = dao.getAllDimensions()
        val bySystemKey = existing.mapNotNull { dimension -> dimension.systemKey?.let { it to dimension } }.toMap()
        val byName = existing.associateBy { it.name.trim().lowercase(Locale.ROOT) }
        val map = mutableMapOf<Long, Long>()

        records.forEach { record ->
            val seededMatch = record.systemKey?.let { bySystemKey[it] }
            val nameMatch = byName[record.name.trim().lowercase(Locale.ROOT)]

            val match = when {
                seededMatch != null -> seededMatch
                mode == RestoreMode.MERGE -> nameMatch
                else -> null
            }

            if (match != null) {
                if (mode == RestoreMode.REPLACE) {
                    dao.updateDimension(
                        match.copy(
                            name = record.name,
                            colorIndex = record.colorIndex,
                            isArchived = record.isArchived,
                        )
                    )
                }
                map[record.id] = match.id
            } else {
                val newId = dao.insertDimension(
                    EnergyDimensionEntity(
                        name = record.name,
                        systemKey = record.systemKey,
                        colorIndex = record.colorIndex,
                        isArchived = record.isArchived,
                    )
                )
                map[record.id] = newId
            }
        }
        return map
    }

    private suspend fun insertTemplates(
        records: List<TemplateRecord>,
        idMap: Map<Long, Long>,
    ) {
        val dao = db.dao()
        val localNames = dao.getTemplates().map { it.normalizedName }.toSet()

        records.forEach { record ->
            val normalized = record.normalizedName.trim().lowercase(Locale.ROOT)
            if (normalized in localNames) return@forEach
            val primaryId = idMap[record.defaultPrimaryDimensionId] ?: return@forEach
            val secondaryIds = record.defaultSecondaryDimensionIds
                .mapNotNull { idMap[it] }
                .filter { it != primaryId }
                .distinct()

            dao.insertTemplate(
                ActivityTemplateEntity(
                    normalizedName = normalized,
                    displayName = record.displayName,
                    defaultDirection = record.defaultDirection,
                    defaultEnergyValue = record.defaultEnergyValue,
                    defaultPrimaryDimensionId = primaryId,
                    defaultSecondaryDimensionIdsCsv = secondaryIds.joinToString(","),
                    isPinned = record.isPinned,
                    usageCount = record.usageCount,
                    lastUsedEpochMillis = record.lastUsedEpochMillis,
                )
            )
        }
    }

    private suspend fun insertEntries(
        records: List<EntryRecord>,
        idMap: Map<Long, Long>,
        deduplicate: Boolean,
    ) {
        val dao = db.dao()
        val existingKeys = if (deduplicate) {
            dao.getAllEntriesWithDimensions().map { it.entry.naturalKey() }.toMutableSet()
        } else {
            mutableSetOf()
        }

        records.forEach { record ->
            val primaryId = idMap[record.primaryDimensionId] ?: return@forEach
            val secondaryIds = record.secondaryDimensionIds
                .mapNotNull { idMap[it] }
                .filter { it != primaryId }
                .distinct()

            val entity = EntryEntity(
                dateIso = record.dateIso,
                timeSlot = record.timeSlot,
                activity = record.activity,
                situationalNote = record.situationalNote,
                direction = record.direction,
                energyValue = record.energyValue,
                primaryDimensionId = primaryId,
                createdAtEpochMillis = record.createdAtEpochMillis,
            )

            if (deduplicate && !existingKeys.add(entity.naturalKey())) return@forEach

            dao.insertEntryWithSecondaries(entity, secondaryIds)
        }
    }

    private suspend fun applyReminders(records: List<ReminderRecord>) {
        val dao = db.dao()
        val locals = dao.getReminderSchedules().associateBy { it.timeSlot }
        records.forEach { record ->
            val local = locals[record.timeSlot] ?: return@forEach
            dao.updateReminderSchedule(
                ReminderScheduleEntity(
                    timeSlot = record.timeSlot,
                    hour = record.hour,
                    minute = record.minute,
                    isEnabled = record.isEnabled,
                )
            )
        }
    }

    private fun decodeIds(csv: String): List<Long> =
        csv.split(',').mapNotNull { it.trim().toLongOrNull() }

    private fun EntryEntity.naturalKey(): String = listOf(
        dateIso,
        timeSlot,
        activity,
        situationalNote.orEmpty(),
        direction,
        energyValue.toString(),
        primaryDimensionId.toString(),
        createdAtEpochMillis.toString(),
    ).joinToString("|")

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupPayload(
    val schemaVersion: Int = BackupSerializer.CURRENT_SCHEMA_VERSION,
    val dimensions: List<DimensionRecord> = emptyList(),
    val templates: List<TemplateRecord> = emptyList(),
    val entries: List<EntryRecord> = emptyList(),
    val reminders: List<ReminderRecord> = emptyList(),
)

@Serializable
data class DimensionRecord(
    val id: Long,
    val systemKey: String? = null,
    val name: String,
    val colorIndex: Int,
    val isArchived: Boolean = false,
)

@Serializable
data class TemplateRecord(
    val normalizedName: String,
    val displayName: String,
    val defaultDirection: String,
    val defaultEnergyValue: Int,
    val defaultPrimaryDimensionId: Long,
    val defaultSecondaryDimensionIds: List<Long> = emptyList(),
    val isPinned: Boolean = false,
    val usageCount: Int = 0,
    val lastUsedEpochMillis: Long = 0L,
)

@Serializable
data class EntryRecord(
    val dateIso: String,
    val timeSlot: String,
    val activity: String,
    val situationalNote: String? = null,
    val direction: String,
    val energyValue: Int,
    val primaryDimensionId: Long,
    val secondaryDimensionIds: List<Long> = emptyList(),
    val createdAtEpochMillis: Long,
)

@Serializable
data class ReminderRecord(
    val timeSlot: String,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean,
)
