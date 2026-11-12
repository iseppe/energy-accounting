package com.empathie.energyaccounting.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stored form of [com.empathie.energyaccounting.domain.model.ActivityTemplate].
 *
 * [normalizedName] is the lowercased, trimmed activity name and is unique, so a
 * template is matched case- and whitespace-insensitively.
 * [defaultSecondaryDimensionIdsCsv] holds comma-separated dimension ids.
 */
@Entity(
    tableName = "activity_templates",
    indices = [Index(value = ["normalizedName"], unique = true)],
)
data class ActivityTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val normalizedName: String,
    val displayName: String,
    val defaultDirection: String,
    val defaultEnergyValue: Int,
    val defaultPrimaryDimensionId: Long,
    val defaultSecondaryDimensionIdsCsv: String,
    val isPinned: Boolean,
    val usageCount: Int,
    val lastUsedEpochMillis: Long,
)
