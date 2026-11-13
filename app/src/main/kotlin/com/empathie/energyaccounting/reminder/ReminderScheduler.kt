package com.empathie.energyaccounting.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.empathie.energyaccounting.domain.model.CheckInReminder
import com.empathie.energyaccounting.domain.model.TimeSlot
import java.time.ZonedDateTime

/**
 * Schedules the four per-slot check-in alarms directly with the framework
 * [AlarmManager]. No Google Play Services, no WorkManager, no network.
 *
 * Uses exact alarms when the user has granted that permission and the platform supports
 * it, otherwise falls back to `setAndAllowWhileIdle`, which may drift by a few minutes but
 * never needs extra permission.
 */
open class ReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    open fun scheduleOrCancel(reminder: CheckInReminder, now: ZonedDateTime = ZonedDateTime.now()) {
        val pendingIntent = pendingIntentFor(reminder.timeSlot)

        if (!reminder.isEnabled) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val triggerAtMillis = computeNextTriggerMillis(reminder.hour, reminder.minute, now)

        if (canUseExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    fun computeNextTriggerMillis(hour: Int, minute: Int, now: ZonedDateTime): Long {
        val todayAtTime = now
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)
        val target = if (todayAtTime.isAfter(now)) todayAtTime else todayAtTime.plusDays(1)
        return target.toInstant().toEpochMilli()
    }

    fun canScheduleExactAlarms(): Boolean = canUseExactAlarms()

    /**
     * Arms a real OS alarm a few seconds from now so Developer Mode can prove that a
     * notification arrives while the app is backgrounded or the screen is locked.
     */
    fun scheduleTestAlarmInSeconds(
        timeSlot: TimeSlot,
        seconds: Long = 5,
        forcePost: Boolean = true,
    ) {
        val intent = Intent(context, CheckInReminderReceiver::class.java).apply {
            putExtra(CheckInReminderReceiver.EXTRA_TIME_SLOT, timeSlot.name)
            putExtra(CheckInReminderReceiver.EXTRA_FORCE_POST, forcePost)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + 100 + timeSlot.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAtMillis = System.currentTimeMillis() + seconds * 1_000L

        if (canUseExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    private fun canUseExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    private fun pendingIntentFor(timeSlot: TimeSlot): PendingIntent {
        val intent = Intent(context, CheckInReminderReceiver::class.java).apply {
            putExtra(CheckInReminderReceiver.EXTRA_TIME_SLOT, timeSlot.name)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + timeSlot.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_CODE_BASE = 1000
    }
}
