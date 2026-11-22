package com.empathie.energyaccounting.update

/**
 * When the app is allowed to look for a release and when a dismissed update stays quiet.
 *
 * Pure clock arithmetic: callers pass `System.currentTimeMillis()`, which keeps every rule
 * testable without a fake clock or an Android runtime.
 */
object UpdatePolicy {

    /** Minimum gap between background update checks: 24 hours. */
    const val CHECK_INTERVAL_MS = 86_400_000L

    /** How long "not now" hides a specific version: 24 hours. */
    const val SNOOZE_INTERVAL_MS = 86_400_000L

    /**
     * True when updates are enabled and the last check is unknown (`<= 0`), in the future
     * (the clock moved backwards), or at least [CHECK_INTERVAL_MS] old.
     */
    fun shouldCheck(now: Long, lastCheck: Long, enabled: Boolean): Boolean =
        enabled && (lastCheck <= 0 || now < lastCheck || now - lastCheck >= CHECK_INTERVAL_MS)

    /** True when [version] is the snoozed version and its snooze has not expired yet. */
    fun isSnoozed(
        version: String,
        snoozedVersion: String?,
        snoozedUntil: Long,
        now: Long,
    ): Boolean = snoozedVersion == version && now < snoozedUntil
}
