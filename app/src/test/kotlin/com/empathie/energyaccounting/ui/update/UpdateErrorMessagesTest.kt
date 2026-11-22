package com.empathie.energyaccounting.ui.update

import com.empathie.energyaccounting.R
import com.empathie.energyaccounting.update.UpdateError
import com.empathie.energyaccounting.update.UpdateUiState
import com.empathie.energyaccounting.update.VerificationError
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateErrorMessagesTest {

    @Test
    fun `package mismatch uses the package message`() {
        assertEquals(
            R.string.update_error_package,
            errorMessageRes(UpdateUiState.Error(null, VerificationError.PACKAGE, UpdateError.UNKNOWN)),
        )
    }

    @Test
    fun `signature mismatch keeps the signature message`() {
        assertEquals(
            R.string.update_error_signature,
            errorMessageRes(UpdateUiState.Error(null, VerificationError.SIGNATURE, UpdateError.UNKNOWN)),
        )
    }

    @Test
    fun `checksum mismatch uses the checksum message`() {
        assertEquals(
            R.string.update_error_checksum,
            errorMessageRes(UpdateUiState.Error(null, VerificationError.CHECKSUM, UpdateError.UNKNOWN)),
        )
    }

    @Test
    fun `unreadable apk uses the unreadable message`() {
        assertEquals(
            R.string.update_error_unreadable,
            errorMessageRes(UpdateUiState.Error(null, VerificationError.UNREADABLE, UpdateError.UNKNOWN)),
        )
    }

    @Test
    fun `errors without a verification error use the matching kind message`() {
        assertEquals(
            R.string.update_error_network,
            errorMessageRes(UpdateUiState.Error(null, error = null, kind = UpdateError.NETWORK)),
        )
        assertEquals(
            R.string.update_error_storage,
            errorMessageRes(UpdateUiState.Error(null, error = null, kind = UpdateError.STORAGE)),
        )
        assertEquals(
            R.string.update_error_installer,
            errorMessageRes(UpdateUiState.Error(null, error = null, kind = UpdateError.INSTALLER)),
        )
        assertEquals(
            R.string.update_error_generic,
            errorMessageRes(UpdateUiState.Error(null, error = null, kind = UpdateError.UNKNOWN)),
        )
    }
}
