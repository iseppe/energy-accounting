package com.empathie.energyaccounting.data.diagnostics

import com.empathie.energyaccounting.data.local.EnergyAccountingDatabase
import java.time.OffsetDateTime

/**
 * Builds a plain-text diagnostic report the user can export and share when something
 * goes wrong.
 *
 * Contains app/build/device identity, a database summary, the in-memory [AppLogger]
 * buffer and the last logcat lines. It never contains entry text beyond counts.
 */
class DebugLogExporter(private val db: EnergyAccountingDatabase) {

    suspend fun buildLogReport(
        buildId: String,
        packageName: String,
        versionName: String,
        versionCode: Int,
        sdkInt: Int,
        deviceModel: String,
        localeTag: String,
        themeName: String,
    ): String {
        val dao = db.dao()
        val dimensions = dao.getAllDimensions()
        val templates = dao.getTemplates()
        val entries = dao.getAllEntriesWithDimensions()
        val reminders = dao.getReminderSchedules()

        return buildString {
            appendLine("Energy Accounting - debug log")
            appendLine("Build: $buildId")
            appendLine("Package: $packageName")
            appendLine("Version: $versionName ($versionCode)")
            appendLine("Android SDK: $sdkInt")
            appendLine("Device: $deviceModel")
            appendLine("Locale: $localeTag")
            appendLine("Theme: $themeName")
            appendLine("Generated: ${OffsetDateTime.now()}")
            appendLine()

            appendLine("## Database")
            appendLine("dimensions: ${dimensions.size} (archived: ${dimensions.count { it.isArchived }})")
            appendLine("templates: ${templates.size} (pinned: ${templates.count { it.isPinned }})")
            appendLine("entries: ${entries.size}")
            appendLine("oldest entry: ${entries.minOfOrNull { it.entry.dateIso } ?: "-"}")
            appendLine("newest entry: ${entries.maxOfOrNull { it.entry.dateIso } ?: "-"}")
            appendLine("reminders:")
            reminders.forEach { reminder ->
                appendLine(
                    "  ${reminder.timeSlot}: %02d:%02d enabled=${reminder.isEnabled}"
                        .format(reminder.hour, reminder.minute)
                )
            }
            appendLine()

            appendLine("## Runtime log")
            val lines = AppLogger.dumpLines()
            if (lines.isEmpty()) {
                appendLine("(empty)")
            } else {
                lines.forEach { appendLine(it) }
            }
            appendLine()

            appendLine("## logcat (last 300 lines)")
            appendLine(readLogcat())
        }
    }

    private fun readLogcat(): String = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-t", "300")
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.readText() }
            .ifBlank { "(no logcat output)" }
    }.getOrElse { "logcat unavailable: ${it.message}" }
}
