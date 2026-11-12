package com.empathie.energyaccounting.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * An [EntryEntity] together with its primary and secondary dimensions.
 */
data class EntryWithDimensions(
    @Embedded val entry: EntryEntity,
    @Relation(
        parentColumn = "primaryDimensionId",
        entityColumn = "id",
    )
    val primaryDimension: EnergyDimensionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EntrySecondaryDimensionCrossRef::class,
            parentColumn = "entryId",
            entityColumn = "dimensionId",
        ),
    )
    val secondaryDimensions: List<EnergyDimensionEntity>,
)
