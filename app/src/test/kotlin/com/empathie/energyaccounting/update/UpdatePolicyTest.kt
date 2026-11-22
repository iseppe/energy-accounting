package com.empathie.energyaccounting.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    private val now = 1_000_000_000_000L

    @Test
    fun `disabled checks never run`() {
        assertFalse(UpdatePolicy.shouldCheck(now, 0, enabled = false))
    }

    @Test
    fun `checks when never checked or at the 24 hour boundary`() {
        assertTrue(UpdatePolicy.shouldCheck(now, 0, enabled = true))
        assertTrue(UpdatePolicy.shouldCheck(now, now - UpdatePolicy.CHECK_INTERVAL_MS, enabled = true))
        assertFalse(UpdatePolicy.shouldCheck(now, now - UpdatePolicy.CHECK_INTERVAL_MS + 1, enabled = true))
    }

    @Test
    fun `a clock moved backwards triggers a check`() {
        assertTrue(UpdatePolicy.shouldCheck(now, now + 60_000, enabled = true))
    }

    @Test
    fun `snooze applies only to the same version before expiry`() {
        assertTrue(UpdatePolicy.isSnoozed("0.4.0", "0.4.0", now + 1, now))
        assertFalse(UpdatePolicy.isSnoozed("0.4.0", "0.4.0", now - 1, now))
        assertFalse(UpdatePolicy.isSnoozed("0.4.1", "0.4.0", now + 1, now))
        assertFalse(UpdatePolicy.isSnoozed("0.4.0", null, 0, now))
    }
}
