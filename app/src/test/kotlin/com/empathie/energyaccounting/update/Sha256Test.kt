package com.empathie.energyaccounting.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class Sha256Test {

    @Test
    fun `sha256 of known bytes is stable`() {
        val file = File.createTempFile("sha", ".txt").apply { writeText("hello") }
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", Sha256.of(file))
    }
}
