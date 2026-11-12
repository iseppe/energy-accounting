package com.empathie.energyaccounting.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stored form of [com.empathie.energyaccounting.domain.model.EnergyDimension].
 *
 * [systemKey] is a [com.empathie.energyaccounting.domain.model.SystemDimensionKey] name for
 * the five seeded dimensions and `null` for user-created ones. The unique index tolerates
 * multiple `NULL`s, so any number of custom dimensions can coexist.
 */
@Entity(
    tableName = "energy_dimensions",
    indices = [Index(value = ["systemKey"], unique = true)],
)
data class EnergyDimensionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val systemKey: String?,
    val colorIndex: Int,
    val isArchived: Boolean = false,
)
