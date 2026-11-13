package com.empathie.energyaccounting.data.export

import android.content.Context
import com.empathie.energyaccounting.R
import com.empathie.energyaccounting.domain.model.EnergyDimension
import com.empathie.energyaccounting.domain.model.SystemDimensionKey
import com.empathie.energyaccounting.domain.model.TimeSlot

/**
 * Localized copy for the practitioner report.
 *
 * The PDF generator is Context-free so it can be tested on the JVM and called from any
 * thread; the screen builds these labels from resources and hands them over.
 */
data class PdfLabels(
    val title: String,
    val periodLabel: String,
    val periodSummary: String,
    val daysLogged: String,
    val totalGain: String,
    val totalSpend: String,
    val netBalance: String,
    val averageDailyNet: String,
    val spendByPrimaryDimension: String,
    val gainByPrimaryDimension: String,
    val byTimeSlot: String,
    val dailyTotals: String,
    val gain: String,
    val spend: String,
    val net: String,
    val columnDateSlot: String,
    val columnActivity: String,
    val columnDimension: String,
    val columnValue: String,
    val timeSlotNames: Map<TimeSlot, String>,
    val dimensionNames: Map<SystemDimensionKey, String> = emptyMap(),
) {
    fun resolveDimensionName(dimension: EnergyDimension): String =
        dimension.systemKey?.let { dimensionNames[it] } ?: dimension.name

    companion object {
        fun from(context: Context): PdfLabels = PdfLabels(
            title = context.getString(R.string.pdf_report_title),
            periodLabel = context.getString(R.string.pdf_period_label),
            periodSummary = context.getString(R.string.pdf_period_summary),
            daysLogged = context.getString(R.string.pdf_days_logged),
            totalGain = context.getString(R.string.pdf_summary_gain),
            totalSpend = context.getString(R.string.pdf_summary_spend),
            netBalance = context.getString(R.string.pdf_summary_net),
            averageDailyNet = context.getString(R.string.pdf_average_daily_net),
            spendByPrimaryDimension = context.getString(R.string.pdf_spend_by_primary),
            gainByPrimaryDimension = context.getString(R.string.pdf_gain_by_primary),
            byTimeSlot = context.getString(R.string.pdf_by_time_slot),
            dailyTotals = context.getString(R.string.pdf_daily_totals),
            gain = context.getString(R.string.pdf_summary_gain),
            spend = context.getString(R.string.pdf_summary_spend),
            net = context.getString(R.string.pdf_summary_net),
            columnDateSlot = context.getString(R.string.pdf_column_date_slot),
            columnActivity = context.getString(R.string.pdf_column_activity),
            columnDimension = context.getString(R.string.pdf_column_dimension),
            columnValue = context.getString(R.string.pdf_column_value),
            timeSlotNames = mapOf(
                TimeSlot.EARLY_MORNING to context.getString(R.string.time_slot_early_morning),
                TimeSlot.LATE_MORNING to context.getString(R.string.time_slot_late_morning),
                TimeSlot.AFTERNOON to context.getString(R.string.time_slot_afternoon),
                TimeSlot.EVENING to context.getString(R.string.time_slot_evening),
            ),
            dimensionNames = mapOf(
                SystemDimensionKey.PHYSICAL to context.getString(R.string.dimension_physical),
                SystemDimensionKey.COGNITIVE to context.getString(R.string.dimension_cognitive),
                SystemDimensionKey.SENSORY to context.getString(R.string.dimension_sensory),
                SystemDimensionKey.SOCIAL to context.getString(R.string.dimension_social),
                SystemDimensionKey.EMOTIONAL to context.getString(R.string.dimension_emotional),
            ),
        )

        val english: PdfLabels = PdfLabels(
            title = "Daily Energy Account",
            periodLabel = "Period",
            periodSummary = "Period summary",
            daysLogged = "Days logged",
            totalGain = "Total gain",
            totalSpend = "Total spend",
            netBalance = "Net balance",
            averageDailyNet = "Average daily net",
            spendByPrimaryDimension = "Spend by dimension",
            gainByPrimaryDimension = "Gain by dimension",
            byTimeSlot = "By time slot",
            dailyTotals = "Daily totals",
            gain = "Total gain",
            spend = "Total spend",
            net = "Net balance",
            columnDateSlot = "Date / Time Slot",
            columnActivity = "Activity & Situational Note",
            columnDimension = "Dimension",
            columnValue = "Value",
            timeSlotNames = mapOf(
                TimeSlot.EARLY_MORNING to "Early Morning",
                TimeSlot.LATE_MORNING to "Late Morning",
                TimeSlot.AFTERNOON to "Afternoon",
                TimeSlot.EVENING to "Evening",
            ),
            dimensionNames = mapOf(
                SystemDimensionKey.PHYSICAL to "Physical",
                SystemDimensionKey.COGNITIVE to "Cognitive",
                SystemDimensionKey.SENSORY to "Sensory",
                SystemDimensionKey.SOCIAL to "Social",
                SystemDimensionKey.EMOTIONAL to "Emotional",
            ),
        )
    }
}
