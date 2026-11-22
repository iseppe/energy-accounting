package com.empathie.energyaccounting.ui.daily

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.empathie.energyaccounting.domain.model.DailyAccount
import com.empathie.energyaccounting.domain.model.Direction
import com.empathie.energyaccounting.domain.model.EnergyDimension
import com.empathie.energyaccounting.domain.model.Entry
import com.empathie.energyaccounting.domain.model.SystemDimensionKey
import com.empathie.energyaccounting.domain.model.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EntryDragStateTest {
    @Test
    fun `clipped rows do not turn a visible insertion point into the end of a long slot`() {
        val date = LocalDate.of(2026, 11, 12)
        val dimension = EnergyDimension(1L, "Physical", SystemDimensionKey.PHYSICAL, 0)
        val entries = (1L..5L).map { id ->
            Entry(
                id = id, date = date, timeSlot = TimeSlot.LATE_MORNING, activity = "Entry $id",
                situationalNote = null, direction = Direction.SPEND, energyValue = 10,
                dimension = dimension, createdAtEpochMillis = id, positionInSlot = id - 1,
            )
        }
        val account = DailyAccount.fromEntries(date, entries)
        val state = EntryDragState()
        state.slotBounds[TimeSlot.LATE_MORNING] = Rect(0f, 0f, 100f, 100f)
        state.entryBounds[1L] = Rect(0f, 0f, 100f, 20f)
        state.entryBounds[2L] = Rect(0f, 20f, 100f, 40f)
        state.entryBounds[3L] = Rect(0f, 40f, 100f, 60f)
        state.entryBounds[4L] = Rect.Zero
        state.entryBounds[5L] = Rect.Zero

        assertTrue(state.begin(Offset(50f, 10f), account))
        state.update(Offset(50f, 70f), account)
        assertEquals(EntryDropTarget(TimeSlot.LATE_MORNING, 2), state.target)
    }
}
