package com.prudent66.vetguide.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure-JVM tests for age value/unit formatting (poultry-friendly ages). */
class AgeFormatTest {

    @Test
    fun `whole values render without decimals`() {
        assertEquals("3 Days", formatAge(3.0, AgeUnit.DAYS))
        assertEquals("2 Years", formatAge(2.0, AgeUnit.YEARS))
    }

    @Test
    fun `fractional values keep their decimals`() {
        assertEquals("1.5 Years", formatAge(1.5, AgeUnit.YEARS))
        assertEquals("0.5 Weeks", formatAge(0.5, AgeUnit.WEEKS))
    }

    @Test
    fun `unknown or invalid ages render nothing`() {
        assertNull(formatAge(null, AgeUnit.DAYS))
        assertNull(formatAge(3.0, null))
        assertNull(formatAge(0.0, AgeUnit.DAYS))
    }
}
