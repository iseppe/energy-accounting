package com.empathie.energyaccounting.ui.daily

import com.empathie.energyaccounting.domain.model.EnergyDimension
import com.empathie.energyaccounting.domain.model.SystemDimensionKey
import org.junit.Assert.assertEquals
import org.junit.Test

class AddEditEntrySheetTest {

    private val active = EnergyDimension(1L, "Physical", SystemDimensionKey.PHYSICAL, 0)
    private val archived = EnergyDimension(2L, "Focus", null, 1, isArchived = true)

    @Test
    fun `new entries hide archived dimensions`() {
        assertEquals(
            listOf(active),
            visibleDimensions(listOf(active, archived), selectedDimensionId = null),
        )
    }

    @Test
    fun `editing keeps the draft's archived dimension visible`() {
        assertEquals(
            listOf(active, archived),
            visibleDimensions(listOf(active, archived), selectedDimensionId = archived.id),
        )
    }
}
