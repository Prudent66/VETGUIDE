package com.prudent66.vetguide.ui.components

import com.prudent66.vetguide.domain.formulary.DoseRoute
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

    @Test
    fun `topicals show application guidance instead of syringe volume`() {
        assertEquals("Application Amount (mL)", volumeLabel(DoseRoute.TOPICAL))
        assertEquals("Syringe Volume to Draw (mL)", volumeLabel(DoseRoute.IM))
        assertEquals("Syringe Volume to Draw (mL)", volumeLabel(DoseRoute.DART))
    }

    @Test
    fun `alternatives are explained as replaceable against weight`() {
        assertTrue(REPLACEABLE_ALTERNATIVES_NOTE.contains("replaceable alternatives"))
        assertTrue(REPLACEABLE_ALTERNATIVES_NOTE.contains("Weight (kg)"))
        assertTrue(REPLACEABLE_ALTERNATIVES_NOTE.contains("Choose one option"))
    }
}
