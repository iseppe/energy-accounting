package com.empathie.energyaccounting.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {

    @Test
    fun `parses tags with and without a v prefix`() {
        assertEquals(SemanticVersion(0, 4, 0), SemanticVersion.parse("v0.4.0"))
        assertEquals(SemanticVersion(0, 4, 0), SemanticVersion.parse("0.4.0"))
        assertEquals(SemanticVersion(1, 12, 3), SemanticVersion.parse("v1.12.3"))
    }

    @Test
    fun `rejects incomplete, prerelease and junk tags`() {
        listOf(null, "", "v0.4", "0.4.0-beta", "v0.4.0.1", "banana", "v1.2.x").forEach {
            assertNull("should reject $it", SemanticVersion.parse(it))
        }
    }

    @Test
    fun `orders versions numerically`() {
        assertTrue(SemanticVersion.parse("0.4.0")!! > SemanticVersion.parse("0.3.9")!!)
        assertTrue(SemanticVersion.parse("1.0.0")!! > SemanticVersion.parse("0.99.99")!!)
        assertEquals("0.4.0", SemanticVersion(0, 4, 0).toString())
    }
}
