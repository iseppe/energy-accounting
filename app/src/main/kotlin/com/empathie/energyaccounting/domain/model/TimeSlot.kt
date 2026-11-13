package com.empathie.energyaccounting.domain.model

import java.time.LocalTime

/**
 * One of four fixed chronological divisions of a [DailyAccount].
 */
enum class TimeSlot {
    EARLY_MORNING,
    LATE_MORNING,
    AFTERNOON,
    EVENING;

    companion object {
        /**
         * The slot a new entry defaults to when it is logged on [time].
         *
         * 05:00-09:59 Early Morning, 10:00-12:59 Late Morning,
         * 13:00-17:59 Afternoon, 18:00-04:59 Evening.
         */
        fun defaultForTime(time: LocalTime): TimeSlot = when {
            !time.isBefore(LocalTime.of(5, 0)) && time.isBefore(LocalTime.of(10, 0)) -> EARLY_MORNING
            !time.isBefore(LocalTime.of(10, 0)) && time.isBefore(LocalTime.of(13, 0)) -> LATE_MORNING
            !time.isBefore(LocalTime.of(13, 0)) && time.isBefore(LocalTime.of(18, 0)) -> AFTERNOON
            else -> EVENING
        }
    }
}
