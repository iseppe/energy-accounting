package com.empathie.energyaccounting.data.export

import androidx.room.withTransaction
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.local.entity.ActivityTemplateEntity
import com.empathie.energyaccounting.data.local.entity.EnergyDimensionEntity
import com.empathie.energyaccounting.data.local.entity.EntryEntity
import com.empathie.energyaccounting.data.local.entity.ReminderScheduleEntity
import com.empathie.energyaccounting.domain.model.CheckInReminder
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.Entry
import com.empathie.energyaccounting.domain.model.SystemDimensionKey
import com.empathie.energyaccounting.domain.model.TimeSlot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.util.Locale

/**
 * Versioned JSON backup of every local table, plus the restore engine.
 *
 * Exports schema version 3 with per-slot ordering. Restore also accepts v1 and v2
 * backups, remapping every dimension reference onto the local database.
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
                    defaultDimensionId = it.defaultDimensionId,
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
                    dimensionId = it.entry.dimensionId,
                    createdAtEpochMillis = it.entry.createdAtEpochMillis,
                    positionInSlot = it.entry.positionInSlot,
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

    suspend fun restoreJson(jsonString: String, mode: RestoreMode): List<CheckInReminder> {
        val payload = decodePayload(jsonString)
        validate(payload)

        return db.withTransaction {
            val dao = db.dao()
            if (mode == RestoreMode.REPLACE) {
                dao.deleteAllEntries()
                dao.deleteAllTemplates()
                dao.deleteAllCustomDimensions()
            }

            val idMap = remapDimensions(payload.dimensions, mode)
            insertTemplates(payload.templates, idMap)
            insertEntries(
                payload.entries,
                idMap,
                deduplicate = mode == RestoreMode.MERGE,
                hasPositions = payload.schemaVersion >= 3,
            )
            applyReminders(payload.reminders)
        }
    }

    private fun decodePayload(jsonString: String): BackupPayload {
        val root = json.parseToJsonElement(jsonString).jsonObject
        val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1

        return when (version) {
            1 -> json.decodeFromString(V1BackupPayload.serializer(), jsonString).toCurrent().copy(schemaVersion = 1)
            2, 3 -> json.decodeFromString(BackupPayload.serializer(), jsonString)
            else -> throw IllegalArgumentException("Unsupported backup schema version $version")
        }
    }

    /**
     * Rejects a corrupt or hand-edited backup before any row is written, so a failed
     * restore can never leave screens crashing on unparsable enum names or dates.
     */
    private fun validate(payload: BackupPayload) {
        val timeSlotNames = TimeSlot.entries.map { it.name }.toSet()
        val directionNames = Direction.entries.map { it.name }.toSet()
        val systemKeys = SystemDimensionKey.entries.map { it.name }.toSet()
        val dimensionIds = payload.dimensions.map { it.id }.toSet()

        payload.dimensions.forEach { record ->
            require(record.name.isNotBlank()) { "Dimension name must not be blank" }
            record.systemKey?.let { key ->
                require(key in systemKeys) { "Unknown system dimension key: $key" }
            }
        }

        payload.templates.forEach { record ->
            require(record.displayName.isNotBlank()) { "Template name must not be blank" }
            require(record.defaultDimensionId in dimensionIds) { "Template references a missing dimension" }
            require(record.defaultDirection in directionNames) {
                "Unknown direction: ${record.defaultDirection}"
            }
            require(record.defaultEnergyValue in Entry.MIN_ENERGY_VALUE..Entry.MAX_ENERGY_VALUE) {
                "Template energy value out of range: ${record.defaultEnergyValue}"
            }
        }

        payload.entries.forEach { record ->
            require(record.dimensionId in dimensionIds) { "Entry references a missing dimension" }
            require(runCatching { LocalDate.parse(record.dateIso) }.isSuccess) {
                "Invalid entry date: ${record.dateIso}"
            }
            require(record.timeSlot in timeSlotNames) { "Unknown time slot: ${record.timeSlot}" }
            require(record.direction in directionNames) { "Unknown direction: ${record.direction}" }
            require(record.energyValue in Entry.MIN_ENERGY_VALUE..Entry.MAX_ENERGY_VALUE) {
                "Entry energy value out of range: ${record.energyValue}"
            }
            require(record.activity.isNotBlank()) { "Entry activity must not be blank" }
            if (payload.schemaVersion >= 3) {
                require(record.positionInSlot != null && record.positionInSlot >= 0L) {
                    "Entry position must be a non-negative integer"
                }
            }
        }

        payload.reminders.forEach { record ->
            require(record.timeSlot in timeSlotNames) { "Unknown time slot: ${record.timeSlot}" }
            require(record.hour in 0..23) { "Reminder hour out of range: ${record.hour}" }
            require(record.minute in 0..59) { "Reminder minute out of range: ${record.minute}" }
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
                            // Seeded dimensions are never archived; a backup must not hide them.
                            isArchived = false,
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
            val dimensionId = idMap[record.defaultDimensionId] ?: return@forEach

            dao.insertTemplate(
                ActivityTemplateEntity(
                    normalizedName = normalized,
                    displayName = record.displayName,
                    defaultDirection = record.defaultDirection,
                    defaultEnergyValue = record.defaultEnergyValue,
                    defaultDimensionId = dimensionId,
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
        hasPositions: Boolean,
    ) {
        val dao = db.dao()
        val existingKeys = if (deduplicate) {
            dao.getAllEntriesWithDimensions().map { it.entry.naturalKey() }.toMutableSet()
        } else {
            mutableSetOf()
        }

        val ordered = records.sortedWith(
            compareBy<EntryRecord>(
                { it.dateIso },
                { it.timeSlot },
                { if (hasPositions) it.positionInSlot ?: 0L else it.createdAtEpochMillis },
            )
        )
        ordered.forEach { record ->
            val dimensionId = idMap[record.dimensionId] ?: return@forEach

            val entity = EntryEntity(
                dateIso = record.dateIso,
                timeSlot = record.timeSlot,
                activity = record.activity,
                situationalNote = record.situationalNote,
                direction = record.direction,
                energyValue = record.energyValue,
                dimensionId = dimensionId,
                createdAtEpochMillis = record.createdAtEpochMillis,
            )

            if (deduplicate && !existingKeys.add(entity.naturalKey())) return@forEach

            val position = dao.nextPositionForSlot(record.dateIso, record.timeSlot)
            dao.insertEntry(entity.copy(positionInSlot = position))
        }
    }

    private suspend fun applyReminders(records: List<ReminderRecord>): List<CheckInReminder> {
        val dao = db.dao()
        val locals = dao.getReminderSchedules().associateBy { it.timeSlot }
        val applied = mutableListOf<CheckInReminder>()

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
            TimeSlot.entries.firstOrNull { it.name == record.timeSlot }?.let { slot ->
                applied += CheckInReminder(
                    timeSlot = slot,
                    hour = record.hour,
                    minute = record.minute,
                    isEnabled = record.isEnabled,
                )
            }
        }
        return applied
    }

    private fun EntryEntity.naturalKey(): String = listOf(
        dateIso,
        timeSlot,
        activity,
        situationalNote.orEmpty(),
        direction,
        energyValue.toString(),
        dimensionId.toString(),
        createdAtEpochMillis.toString(),
    ).joinToString("|")

    companion object {
        const val CURRENT_SCHEMA_VERSION = 3
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
    val defaultDimensionId: Long,
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
    val dimensionId: Long,
    val createdAtEpochMillis: Long,
    val positionInSlot: Long? = null,
)

@Serializable
data class ReminderRecord(
    val timeSlot: String,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean,
)

// ---- schemaVersion 1 compatibility ------------------------------------------

@Serializable
data class V1BackupPayload(
    val schemaVersion: Int = 1,
    val dimensions: List<DimensionRecord> = emptyList(),
    val templates: List<V1TemplateRecord> = emptyList(),
    val entries: List<V1EntryRecord> = emptyList(),
    val reminders: List<ReminderRecord> = emptyList(),
) {
    fun toCurrent(): BackupPayload = BackupPayload(
        dimensions = dimensions,
        templates = templates.map { it.toCurrent() },
        entries = entries.map { it.toCurrent() },
        reminders = reminders,
    )
}

@Serializable
data class V1TemplateRecord(
    val normalizedName: String,
    val displayName: String,
    val defaultDirection: String,
    val defaultEnergyValue: Int,
    val defaultPrimaryDimensionId: Long,
    val defaultSecondaryDimensionIds: List<Long> = emptyList(),
    val isPinned: Boolean = false,
    val usageCount: Int = 0,
    val lastUsedEpochMillis: Long = 0L,
) {
    fun toCurrent(): TemplateRecord = TemplateRecord(
        normalizedName = normalizedName,
        displayName = displayName,
        defaultDirection = defaultDirection,
        defaultEnergyValue = defaultEnergyValue,
        defaultDimensionId = defaultPrimaryDimensionId,
        isPinned = isPinned,
        usageCount = usageCount,
        lastUsedEpochMillis = lastUsedEpochMillis,
    )
}

@Serializable
data class V1EntryRecord(
    val dateIso: String,
    val timeSlot: String,
    val activity: String,
    val situationalNote: String? = null,
    val direction: String,
    val energyValue: Int,
    val primaryDimensionId: Long,
    val secondaryDimensionIds: List<Long> = emptyList(),
    val createdAtEpochMillis: Long,
) {
    fun toCurrent(): EntryRecord = EntryRecord(
        dateIso = dateIso,
        timeSlot = timeSlot,
        activity = activity,
        situationalNote = situationalNote,
        direction = direction,
        energyValue = energyValue,
        dimensionId = primaryDimensionId,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}
