package com.empathie.energyaccounting.ui.common

import com.empathie.energyaccounting.domain.model.CheckInReminder
import com.empathie.energyaccounting.domain.model.TimeSlot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionTest {

    private fun reminder(enabled: Boolean) = CheckInReminder(
        timeSlot = TimeSlot.EARLY_MORNING,
        hour = 9,
        minute = 30,
        isEnabled = enabled,
    )

    @Test
    fun `asks on API 33 when a reminder is enabled and permission is missing`() {
        assertTrue(
            shouldRequestNotificationPermission(
                sdkInt = 33,
                permissionGranted = false,
                reminders = listOf(reminder(true)),
            )
        )
    }

    @Test
    fun `does not ask before API 33`() {
        assertFalse(
            shouldRequestNotificationPermission(
                sdkInt = 32,
                permissionGranted = false,
                reminders = listOf(reminder(true)),
            )
        )
    }

    @Test
    fun `does not ask when permission is granted`() {
        assertFalse(
            shouldRequestNotificationPermission(
                sdkInt = 33,
                permissionGranted = true,
                reminders = listOf(reminder(true)),
            )
        )
    }

    @Test
    fun `does not ask when every reminder is disabled`() {
        assertFalse(
            shouldRequestNotificationPermission(
                sdkInt = 33,
                permissionGranted = false,
                reminders = listOf(reminder(false)),
            )
        )
    }
}
