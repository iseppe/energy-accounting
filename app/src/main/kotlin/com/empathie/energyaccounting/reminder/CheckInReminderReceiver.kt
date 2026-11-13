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
import com.empathie.energyaccounting.data.diagnostics.AppLogger
import com.empathie.energyaccounting.data.local.DatabaseProvider
import com.empathie.energyaccounting.data.local.EnergyAccountingDao
import com.empathie.energyaccounting.data.settings.AppSettings
import com.empathie.energyaccounting.data.settings.LocaleHelper
import com.empathie.energyaccounting.domain.model.CheckInReminder
import com.empathie.energyaccounting.domain.model.TimeSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * Structured outcome of a Developer Mode slot evaluation, so the UI can explain exactly
 * why a reminder would (not) appear.
 */
sealed interface SlotEvaluationResult {
    data class Posted(val date: LocalDate, val timeSlot: TimeSlot) : SlotEvaluationResult

    data class SuppressedEntriesExist(
        val date: LocalDate,
        val timeSlot: TimeSlot,
        val entryCount: Int,
    ) : SlotEvaluationResult

    data object BlockedNotificationsDisabled : SlotEvaluationResult
}

/**
 * Fires at a slot's configured time and posts a notification only when that slot has no
 * entries yet today, then re-arms the same alarm for the next day.
 *
 * Suppressing a satisfied slot is the whole anti-nag contract: a reminder never asks for
 * something already logged. Re-arming runs even if posting the notification fails, so a
 * single failed notification cannot silently kill the daily reminder.
 */
class CheckInReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val timeSlotName = intent.getStringExtra(EXTRA_TIME_SLOT) ?: return
        val timeSlot = runCatching { TimeSlot.valueOf(timeSlotName) }.getOrNull() ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = DatabaseProvider.get(context).dao()
                val app = context.applicationContext as? com.empathie.energyaccounting.EnergyAccountingApp
                val clock = app?.clock ?: Clock.systemDefaultZone()
                val today = LocalDate.now(clock)
                val forcePost = intent.getBooleanExtra(EXTRA_FORCE_POST, false)

                if (forcePost || shouldPostNotification(dao, today, timeSlot)) {
                    runCatching { postNotification(localizedContext(context), timeSlot) }
                        .onSuccess {
                            AppLogger.info("Reminder", "Posted check-in notification for ${timeSlot.name}")
                        }
                        .onFailure { AppLogger.error("Reminder", "notification failed", it) }
                } else {
                    AppLogger.info("Reminder", "Suppressed ${timeSlot.name}: entries already logged")
                }

                runCatching { reschedule(context, dao, timeSlot) }
                    .onFailure { AppLogger.error("Reminder", "reschedule failed", it) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun reschedule(
        context: Context,
        dao: EnergyAccountingDao,
        timeSlot: TimeSlot,
    ) {
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
    }

    companion object {
        const val EXTRA_TIME_SLOT = "extra_time_slot"
        const val EXTRA_OPEN_ADD_SHEET = "extra_open_add_sheet"
        const val EXTRA_FORCE_POST = "extra_force_post"
        const val NOTIFICATION_CHANNEL_ID = "energy_checkin_reminders"

        suspend fun shouldPostNotification(
            dao: EnergyAccountingDao,
            today: LocalDate,
            timeSlot: TimeSlot,
        ): Boolean = dao.countEntriesForSlot(today.toString(), timeSlot.name) == 0

        /**
         * Developer Mode evaluation: reports exactly what a real alarm would do at [date]
         * for [timeSlot], and posts the notification when it would be posted.
         */
        suspend fun evaluateSlotForDiagnostics(
            context: Context,
            dao: EnergyAccountingDao,
            date: LocalDate,
            timeSlot: TimeSlot,
            forcePost: Boolean = false,
        ): SlotEvaluationResult {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                return SlotEvaluationResult.BlockedNotificationsDisabled
            }

            val entryCount = dao.countEntriesForSlot(date.toString(), timeSlot.name)
            return if (forcePost || entryCount == 0) {
                postNotification(localizedContext(context), timeSlot)
                AppLogger.info(
                    "Reminder",
                    "Diagnostics posted ${timeSlot.name} for $date (force=$forcePost, entries=$entryCount)",
                )
                SlotEvaluationResult.Posted(date, timeSlot)
            } else {
                AppLogger.info(
                    "Reminder",
                    "Diagnostics suppressed ${timeSlot.name} for $date: $entryCount entries",
                )
                SlotEvaluationResult.SuppressedEntriesExist(date, timeSlot, entryCount)
            }
        }

        /**
         * Posts a live sample notification immediately, used by the Manage & Export
         * "Send test notification" button. Returns false when the OS has notifications
         * disabled for the app.
         */
        fun postTestNotification(context: Context, timeSlot: TimeSlot): Boolean {
            val localized = localizedContext(context)
            ensureNotificationChannel(localized)
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            postNotification(localized, timeSlot)
            return true
        }

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val localized = localizedContext(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return

            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                localized.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = localized.getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        /**
         * Notification text must follow the in-app language, because a broadcast receiver
         * has no Activity context carrying the wrapped configuration.
         */
        private fun localizedContext(context: Context): Context =
            LocaleHelper.wrap(context, AppSettings(context).appLanguage())

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

            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_BASE + timeSlot.ordinal, notification)
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
