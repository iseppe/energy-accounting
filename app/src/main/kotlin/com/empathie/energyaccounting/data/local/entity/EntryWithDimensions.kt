package com.empathie.energyaccounting.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * An [EntryEntity] together with its single energy dimension.
 */
data class EntryWithDimensions(
    @Embedded val entry: EntryEntity,
    @Relation(
        parentColumn = "dimensionId",
        entityColumn = "id",
    )
    val dimension: EnergyDimensionEntity,
)
