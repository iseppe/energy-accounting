package com.empathie.energyaccounting.data.export

import com.empathie.energyaccounting.domain.model.EnergyDimension
import com.empathie.energyaccounting.domain.model.Entry
import com.empathie.energyaccounting.domain.service.EnergyFormatter

/**
 * Renders entries as RFC-4180 CSV for spreadsheet analysis.
 *
 * Fields containing a comma, double quote, CR or LF are wrapped in double quotes and
 * their inner quotes doubled, so long situational notes can never shift columns.
 */
object CsvExporter {

    private val HEADER = listOf(
        "Date",
        "Time Slot",
        "Activity",
        "Situational Note",
        "Direction",
        "Signed Value",
        "Dimension",
    )

    fun exportEntries(
        entries: List<Entry>,
        dimensionLabel: (EnergyDimension) -> String = { it.name },
    ): String = buildString {
        appendLine(HEADER.joinToString(","))
        entries
            .sortedWith(compareBy({ it.date }, { it.timeSlot.ordinal }, { it.positionInSlot }, { it.createdAtEpochMillis }, { it.id }))
            .forEach { entry ->
                appendLine(
                    listOf(
                        EnergyFormatter.formatDate(entry.date),
                        entry.timeSlot.name,
                        entry.activity,
                        entry.situationalNote.orEmpty(),
                        entry.direction.name,
                        EnergyFormatter.formatSignedValue(entry.direction, entry.energyValue),
                        dimensionLabel(entry.dimension),
                    ).joinToString(",") { escape(it) }
                )
            }
    }

    private fun escape(field: String): String {
        val needsQuoting = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }
}
