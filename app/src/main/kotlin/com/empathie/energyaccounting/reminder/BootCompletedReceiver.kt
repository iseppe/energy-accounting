package com.empathie.energyaccounting.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.empathie.energyaccounting.data.local.DatabaseProvider
import com.empathie.energyaccounting.domain.model.CheckInReminder
import com.empathie.energyaccounting.domain.model.TimeSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms every enabled check-in alarm after a reboot, an app update, or a clock and
 * time-zone change — the three events that silently drop pending `AlarmManager` alarms.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in SUPPORTED_ACTIONS) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = DatabaseProvider.get(appContext).dao()
                val scheduler = ReminderScheduler(appContext)
                dao.getReminderSchedules().forEach { schedule ->
                    val timeSlot = runCatching { TimeSlot.valueOf(schedule.timeSlot) }
                        .getOrNull() ?: return@forEach
                    scheduler.scheduleOrCancel(
                        CheckInReminder(
                            timeSlot = timeSlot,
                            hour = schedule.hour,
                            minute = schedule.minute,
                            isEnabled = schedule.isEnabled,
                        )
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
