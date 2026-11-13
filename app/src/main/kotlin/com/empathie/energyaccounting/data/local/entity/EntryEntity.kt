package com.empathie.energyaccounting.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stored form of [com.empathie.energyaccounting.domain.model.Entry].
 *
 * [dateIso] is ISO-8601 (`yyyy-MM-dd`) so range queries sort lexicographically;
 * [timeSlot] and [direction] store the enum `name`s.
 */
@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = EnergyDimensionEntity::class,
            parentColumns = ["id"],
            childColumns = ["dimensionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["dateIso"]),
        Index(value = ["dimensionId"]),
        Index(value = ["dateIso", "timeSlot"]),
    ],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dateIso: String,
    val timeSlot: String,
    val activity: String,
    val situationalNote: String?,
    val direction: String,
    val energyValue: Int,
    val dimensionId: Long,
    val createdAtEpochMillis: Long,
)
