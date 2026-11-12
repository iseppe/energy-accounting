package com.empathie.energyaccounting.ui

import android.content.Intent
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.reminder.CheckInReminderReceiver
import com.empathie.energyaccounting.toAddSheetDeepLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityDeepLinkTest {

    @Test
    fun `extras from a check-in notification map to the requested slot`() {
        val intent = Intent()
            .putExtra(CheckInReminderReceiver.EXTRA_OPEN_ADD_SHEET, true)
            .putExtra(CheckInReminderReceiver.EXTRA_TIME_SLOT, TimeSlot.EVENING.name)

        val deepLink = intent.toAddSheetDeepLink()

        assertEquals(TimeSlot.EVENING, deepLink?.timeSlot)
    }

    @Test
    fun `an unknown slot still opens the sheet with the smart default`() {
        val intent = Intent()
            .putExtra(CheckInReminderReceiver.EXTRA_OPEN_ADD_SHEET, true)
            .putExtra(CheckInReminderReceiver.EXTRA_TIME_SLOT, "NOT_A_SLOT")

        val deepLink = intent.toAddSheetDeepLink()

        assertNotNull(deepLink)
        assertNull(deepLink?.timeSlot)
    }

    @Test
    fun `an ordinary launch intent produces no deep link`() {
        assertNull(Intent().toAddSheetDeepLink())
    }
}
