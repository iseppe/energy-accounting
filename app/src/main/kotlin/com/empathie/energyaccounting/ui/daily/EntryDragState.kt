package com.empathie.energyaccounting.ui.daily

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.empathie.energyaccounting.domain.model.DailyAccount
import com.empathie.energyaccounting.domain.model.Entry
import com.empathie.energyaccounting.domain.model.TimeSlot

internal data class EntryDropTarget(val slot: TimeSlot, val index: Int)

/** Tracks visible drop geometry in root coordinates, independently of lazy-list recycling. */
internal class EntryDragState {
    val slotBounds = mutableStateMapOf<TimeSlot, Rect>()
    val entryBounds = mutableStateMapOf<Long, Rect>()
    var draggedEntry: Entry? by mutableStateOf(null)
        private set
    var pointer: Offset by mutableStateOf(Offset.Unspecified)
        private set
    var target: EntryDropTarget? by mutableStateOf(null)
        private set

    fun begin(position: Offset, account: DailyAccount): Boolean {
        val entry = TimeSlot.entries.asSequence()
            .flatMap { account.ledgerFor(it).entries.asSequence() }
            .firstOrNull { entryBounds[it.id]?.contains(position) == true }
            ?: return false
        draggedEntry = entry
        update(position, account)
        return true
    }

    fun update(position: Offset, account: DailyAccount) {
        val dragged = draggedEntry ?: return
        pointer = position
        val slot = TimeSlot.entries.firstOrNull { slotBounds[it]?.contains(position) == true }
        if (slot == null) {
            target = null
            return
        }
        val entries = account.ledgerFor(slot).entries.filterNot { it.id == dragged.id }
        var index = 0
        for ((entryIndex, entry) in entries.withIndex()) {
            val bounds = entryBounds[entry.id] ?: continue
            if (bounds.isEmpty) continue
            index = entryIndex
            if (position.y < bounds.center.y) break
            index = entryIndex + 1
        }
        target = EntryDropTarget(slot, index)
    }

    fun clear() {
        draggedEntry = null
        pointer = Offset.Unspecified
        target = null
    }
}
