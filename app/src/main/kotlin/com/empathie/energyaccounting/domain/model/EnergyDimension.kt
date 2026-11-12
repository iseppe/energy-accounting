package com.empathie.energyaccounting.domain.model

/**
 * Stable identity for the five default [EnergyDimension]s. Custom dimensions carry `null`.
 */
enum class SystemDimensionKey {
    PHYSICAL,
    COGNITIVE,
    SENSORY,
    SOCIAL,
    EMOTIONAL,
}

/**
 * A domain of human functioning impacted by an [Entry].
 *
 * [systemKey] is set for the five seeded dimensions and `null` for user-created ones.
 */
data class EnergyDimension(
    val id: Long,
    val name: String,
    val systemKey: SystemDimensionKey?,
    val colorIndex: Int,
    val isArchived: Boolean = false,
)
