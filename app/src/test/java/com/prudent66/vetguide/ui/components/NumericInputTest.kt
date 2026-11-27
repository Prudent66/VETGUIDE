package com.prudent66.vetguide.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Replacing a numeric value never leaves a malformed intermediate string. */
class NumericInputTest {

    @Test
    fun `keeps a single decimal separator`() {
        assertEquals("12.5", cleanNumericInput("12.5", true))
        assertEquals("12.5", cleanNumericInput("1,2.5", true))
        assertEquals("0.25", cleanNumericInput("0,25", true))
    }

    @Test
    fun `drops letters and stray separators`() {
        assertEquals("125", cleanNumericInput("12a5", true))
        assertEquals("12.5", cleanNumericInput("12..5", true))
        assertEquals("125", cleanNumericInput("1e3", true))
    }

    @Test
    fun `integer fields never accept a separator`() {
        assertEquals("125", cleanNumericInput("12.5", false))
        assertEquals("125", cleanNumericInput("1,25", false))
    }

    @Test
    fun `caps the length of a pasted value`() {
        assertEquals("123456789012", cleanNumericInput("123456789012345", true))
    }
}
