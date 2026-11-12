package com.empathie.energyaccounting.domain.model

import java.util.Locale

/**
 * A reusable blueprint learned from previously logged Activities that pre-fills
 * the defaults of a new [Entry].
 *
 * Changing an entry once never mutates these defaults implicitly.
 *
 * Written as a regular class (not a data class) because normalization must happen
 * inside the constructor, which a data class's `val`-only primary constructor forbids.
 */
class ActivityTemplate(
    val id: Long = 0L,
    activityName: String,
    val defaultDirection: Direction,
    val defaultEnergyValue: Int,
    val defaultPrimaryDimension: EnergyDimension,
    defaultSecondaryDimensions: List<EnergyDimension> = emptyList(),
    val isPinned: Boolean = false,
    val usageCount: Int = 0,
    val lastUsedEpochMillis: Long = 0L,
) {
    init {
        require(activityName.isNotBlank()) { "activityName must not be blank" }
        require(defaultEnergyValue in Entry.MIN_ENERGY_VALUE..Entry.MAX_ENERGY_VALUE) {
            "defaultEnergyValue must be between ${Entry.MIN_ENERGY_VALUE} and ${Entry.MAX_ENERGY_VALUE}, " +
                "was $defaultEnergyValue"
        }
        require(usageCount >= 0) { "usageCount must not be negative" }
    }

    val activityName: String = activityName.trim()
    val normalizedName: String = activityName.trim().lowercase(Locale.ROOT)
    val defaultSecondaryDimensions: List<EnergyDimension> = defaultSecondaryDimensions
        .filterNot { it.id == defaultPrimaryDimension.id }
        .distinctBy { it.id }

    fun copy(
        id: Long = this.id,
        activityName: String = this.activityName,
        defaultDirection: Direction = this.defaultDirection,
        defaultEnergyValue: Int = this.defaultEnergyValue,
        defaultPrimaryDimension: EnergyDimension = this.defaultPrimaryDimension,
        defaultSecondaryDimensions: List<EnergyDimension> = this.defaultSecondaryDimensions,
        isPinned: Boolean = this.isPinned,
        usageCount: Int = this.usageCount,
        lastUsedEpochMillis: Long = this.lastUsedEpochMillis,
    ): ActivityTemplate = ActivityTemplate(
        id = id,
        activityName = activityName,
        defaultDirection = defaultDirection,
        defaultEnergyValue = defaultEnergyValue,
        defaultPrimaryDimension = defaultPrimaryDimension,
        defaultSecondaryDimensions = defaultSecondaryDimensions,
        isPinned = isPinned,
        usageCount = usageCount,
        lastUsedEpochMillis = lastUsedEpochMillis,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ActivityTemplate) return false
        return id == other.id &&
            activityName == other.activityName &&
            defaultDirection == other.defaultDirection &&
            defaultEnergyValue == other.defaultEnergyValue &&
            defaultPrimaryDimension == other.defaultPrimaryDimension &&
            defaultSecondaryDimensions == other.defaultSecondaryDimensions &&
            isPinned == other.isPinned &&
            usageCount == other.usageCount &&
            lastUsedEpochMillis == other.lastUsedEpochMillis
    }

    override fun hashCode(): Int = listOf(
        id,
        activityName,
        defaultDirection,
        defaultEnergyValue,
        defaultPrimaryDimension,
        defaultSecondaryDimensions,
        isPinned,
        usageCount,
        lastUsedEpochMillis,
    ).hashCode()

    override fun toString(): String =
        "ActivityTemplate(id=$id, activityName='$activityName', defaultDirection=$defaultDirection, " +
            "defaultEnergyValue=$defaultEnergyValue, defaultPrimaryDimension=$defaultPrimaryDimension, " +
            "defaultSecondaryDimensions=$defaultSecondaryDimensions, isPinned=$isPinned, " +
            "usageCount=$usageCount, lastUsedEpochMillis=$lastUsedEpochMillis)"
}
