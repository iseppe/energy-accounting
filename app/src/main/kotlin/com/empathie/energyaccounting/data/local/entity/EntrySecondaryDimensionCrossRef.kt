package com.empathie.energyaccounting.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Join row linking an [EntryEntity] to one of its secondary dimensions.
 */
@Entity(
    tableName = "entry_secondary_dimensions",
    primaryKeys = ["entryId", "dimensionId"],
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EnergyDimensionEntity::class,
            parentColumns = ["id"],
            childColumns = ["dimensionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["dimensionId"])],
)
data class EntrySecondaryDimensionCrossRef(
    val entryId: Long,
    val dimensionId: Long,
)
