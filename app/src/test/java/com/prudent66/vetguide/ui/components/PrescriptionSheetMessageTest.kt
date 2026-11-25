package com.prudent66.vetguide.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Build 23: blank or non-positive weight gets a clear calculation prompt. */
class PrescriptionSheetMessageTest {

    @Test
    fun `empty weight asks the vet to enter a weight`() {
        assertEquals(ENTER_WEIGHT_MESSAGE, dosageMessage(true, "fallback"))
        assertTrue(ENTER_WEIGHT_MESSAGE.contains("Enter patient weight (kg)"))
        assertTrue(ENTER_WEIGHT_MESSAGE.contains("syringe volume"))
    }

    @Test
    fun `valid weight keeps the formulary specific message`() {
        assertEquals("fallback", dosageMessage(false, "fallback"))
    }
}
