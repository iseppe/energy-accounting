package com.empathie.energyaccounting.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.empathie.energyaccounting.R
import com.empathie.energyaccounting.data.local.DatabaseProvider
import com.empathie.energyaccounting.data.local.EnergyAccountingDao
import com.empathie.energyaccounting.domain.model.CheckInReminder
import com.empathie.energyaccounting.domain.model.TimeSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Fires at a slot's configured time and posts a notification only when that slot has no
 * entries yet today, then re-arms the same alarm for the next day.
 *
 * Suppressing a satisfied slot is the whole anti-nag contract: a reminder never asks for
 * something already logged.
 */
class CheckInReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val timeSlotName = intent.getStringExtra(EXTRA_TIME_SLOT) ?: return
        val timeSlot = runCatching { TimeSlot.valueOf(timeSlotName) }.getOrNull() ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = DatabaseProvider.get(context).dao()
                val today = LocalDate.now()

                if (shouldPostNotification(dao, today, timeSlot)) {
                    postNotification(context, timeSlot)
                }

                dao.getReminderSchedules()
                    .firstOrNull { it.timeSlot == timeSlot.name }
                    ?.let { schedule ->
                        ReminderScheduler(context).scheduleOrCancel(
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

    companion object {
        const val EXTRA_TIME_SLOT = "extra_time_slot"
        const val EXTRA_OPEN_ADD_SHEET = "extra_open_add_sheet"
        const val NOTIFICATION_CHANNEL_ID = "energy_checkin_reminders"

        suspend fun shouldPostNotification(
            dao: EnergyAccountingDao,
            today: LocalDate,
            timeSlot: TimeSlot,
        ): Boolean = dao.countEntriesForSlot(today.toString(), timeSlot.name) == 0

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return

            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        private fun postNotification(context: Context, timeSlot: TimeSlot) {
            ensureNotificationChannel(context)

            val contentIntent = Intent().apply {
                setClassName(context.packageName, MAIN_ACTIVITY_CLASS)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_ADD_SHEET, true)
                putExtra(EXTRA_TIME_SLOT, timeSlot.name)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                timeSlot.ordinal,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(
                    context.getString(R.string.notification_body, context.slotLabel(timeSlot))
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOnlyAlertOnce(true)
                .build()

            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                NotificationManagerCompat.from(context)
                    .notify(NOTIFICATION_ID_BASE + timeSlot.ordinal, notification)
            }
        }

        private fun Context.slotLabel(timeSlot: TimeSlot): String = getString(
            when (timeSlot) {
                TimeSlot.EARLY_MORNING -> R.string.time_slot_early_morning
                TimeSlot.LATE_MORNING -> R.string.time_slot_late_morning
                TimeSlot.AFTERNOON -> R.string.time_slot_afternoon
                TimeSlot.EVENING -> R.string.time_slot_evening
            }
        )

        private const val NOTIFICATION_ID_BASE = 2000
        private const val MAIN_ACTIVITY_CLASS = "com.empathie.energyaccounting.MainActivity"
    }
}
