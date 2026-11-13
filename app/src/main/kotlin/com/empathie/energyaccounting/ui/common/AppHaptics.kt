package com.empathie.energyaccounting.ui.common

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Small wrapper so every tap can confirm itself tactilely when the user has not disabled
 * vibration in Manage & Export.
 */
class AppHaptics(
    private val enabled: Boolean,
    private val perform: () -> Unit,
) {
    fun tick() {
        if (enabled) perform()
    }
}

val LocalAppHaptics = staticCompositionLocalOf { AppHaptics(enabled = false) {} }
