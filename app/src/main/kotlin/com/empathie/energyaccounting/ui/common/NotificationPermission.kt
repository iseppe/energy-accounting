package com.empathie.energyaccounting.ui.common

import android.os.Build
import com.empathie.energyaccounting.domain.model.CheckInReminder

/**
 * True when the app should ask for the Android 13+ notification permission.
 *
 * Restored reminders can be enabled without ever passing through a reminder switch, so the
 * decision cannot live only in the Manage & Export toggle handler.
 */
internal fun shouldRequestNotificationPermission(
    sdkInt: Int,
    permissionGranted: Boolean,
    reminders: List<CheckInReminder>,
): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU &&
    !permissionGranted &&
    reminders.any { it.isEnabled }
