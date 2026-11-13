package com.empathie.energyaccounting.data.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class FakeClockState(
    val isEnabled: Boolean,
    val currentDate: LocalDate,
    val currentTime: LocalTime,
)

/**
 * A [Clock] whose date and time can be overridden from Developer Mode.
 *
 * When disabled it delegates to [systemClock]; when enabled every `LocalDate.now(clock)` /
 * `LocalTime.now(clock)` in the app sees the same simulated instant, which makes slot
 * notification behaviour reproducible at any time of day.
 */
class AdjustableClock(
    private val systemClock: Clock = Clock.systemDefaultZone(),
) : Clock() {

    private var fake: ZonedDateTime? = null

    private val _state = MutableStateFlow(stateFor(fake))
    val state: StateFlow<FakeClockState> = _state.asStateFlow()

    override fun getZone(): ZoneId = systemClock.zone

    override fun withZone(zone: ZoneId): Clock =
        if (zone == getZone()) this else AdjustableClock(systemClock.withZone(zone))

    override fun instant(): Instant = fake?.toInstant() ?: systemClock.instant()

    fun setFakeDateTime(date: LocalDate, time: LocalTime) {
        fake = ZonedDateTime.of(date, time, getZone())
        _state.value = stateFor(fake)
    }

    fun advanceMinutes(minutes: Long) {
        val current = fake ?: ZonedDateTime.now(systemClock)
        fake = current.plusMinutes(minutes)
        _state.value = stateFor(fake)
    }

    fun resetToSystem() {
        fake = null
        _state.value = stateFor(null)
    }

    private fun stateFor(value: ZonedDateTime?): FakeClockState {
        val now = value ?: ZonedDateTime.now(systemClock)
        return FakeClockState(
            isEnabled = value != null,
            currentDate = now.toLocalDate(),
            currentTime = now.toLocalTime(),
        )
    }
}
