package com.empathie.energyaccounting.domain.model

/**
 * The in-progress dimension selection of the Add/Edit Entry sheet.
 *
 * The first tapped dimension becomes [primaryId]; every further tap adds a
 * [secondaryIds] entry. Tapping the primary promotes the first secondary,
 * long-pressing a secondary swaps it into the primary position.
 */
data class DimensionChoiceState(
    val primaryId: Long?,
    val secondaryIds: Set<Long> = emptySet(),
) {
    fun onTap(dimensionId: Long): DimensionChoiceState = when {
        primaryId == null -> DimensionChoiceState(primaryId = dimensionId)

        primaryId == dimensionId -> {
            val promoted = secondaryIds.firstOrNull()
            if (promoted == null) this
            else DimensionChoiceState(primaryId = promoted, secondaryIds = secondaryIds - promoted)
        }

        dimensionId in secondaryIds ->
            copy(secondaryIds = secondaryIds - dimensionId)

        else ->
            copy(secondaryIds = secondaryIds + dimensionId)
    }

    fun onLongPress(dimensionId: Long): DimensionChoiceState {
        if (dimensionId == primaryId) return this
        if (dimensionId !in secondaryIds) return this
        val previousPrimary = primaryId
        val remaining = secondaryIds - dimensionId
        return DimensionChoiceState(
            primaryId = dimensionId,
            secondaryIds = previousPrimary?.let { remaining + it } ?: remaining,
        )
    }
}
