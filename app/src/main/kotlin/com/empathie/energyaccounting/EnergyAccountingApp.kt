package com.empathie.energyaccounting

import android.app.Application
import com.empathie.energyaccounting.data.diagnostics.AppLogger
import com.empathie.energyaccounting.data.local.DatabaseProvider
import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import com.empathie.energyaccounting.data.repository.EnergyAccountingRepository
import com.empathie.energyaccounting.data.settings.AppSettings
import com.empathie.energyaccounting.reminder.CheckInReminderReceiver
import com.empathie.energyaccounting.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns the single Room database, the repository and the appearance preference for the
 * whole process.
 *
 * Startup work is intentionally tiny and off the main thread: seed the five dimensions and
 * four reminder rows if they are missing, and re-arm enabled alarms (the OS drops them on
 * reboot, and the receiver cannot fire before the app has ever run).
 */
class EnergyAccountingApp : Application() {

    lateinit var database: EnergyAccountingDatabase
        private set

    lateinit var repository: EnergyAccountingRepository
        private set

    lateinit var settings: AppSettings
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        database = DatabaseProvider.get(this)
        repository = EnergyAccountingRepository(database)
        settings = AppSettings(this)

        CheckInReminderReceiver.ensureNotificationChannel(this)

        applicationScope.launch {
            runCatching {
                database.ensureSeeded()
                val scheduler = ReminderScheduler(this@EnergyAccountingApp)
                repository.getReminders().forEach { reminder ->
                    scheduler.scheduleOrCancel(reminder)
                }
                AppLogger.info("App", "Startup complete: database seeded, reminders re-armed")
            }.onFailure { error ->
                AppLogger.error("App", "Startup failed", error)
            }
        }
    }
}
