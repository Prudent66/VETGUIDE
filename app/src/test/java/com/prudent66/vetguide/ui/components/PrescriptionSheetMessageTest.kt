package com.prudent66.vetguide.ui.components

import com.prudent66.vetguide.domain.formulary.DoseRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `eye guidance is gated by explicit ocular involvement`() {
        val ocular = topicalGuidance(DoseRoute.TOPICAL, true)!!
        val skin = topicalGuidance(DoseRoute.TOPICAL, false)!!
        assertTrue(ocular.contains("affected eye"))
        assertTrue(skin.contains("skin or lesion"))
        assertFalse(skin.contains("eye"))
        assertEquals(null, topicalGuidance(DoseRoute.IM, true))
    }

    @Test
    fun `semisolid topicals avoid syringe volume framing`() {
        val ointment = "Topical Antibiotic Ointment (analyzed — verify dose)"
        assertTrue(isSemisolidTopical(DoseRoute.TOPICAL, ointment))
        assertEquals("Estimated Topical Amount", volumeLabel(DoseRoute.TOPICAL, true))
        val application = topicalApplicationText(DoseRoute.TOPICAL, ointment)!!
        assertTrue(application.contains("Thin layer"))
        assertFalse(application.contains("mL"))
        assertFalse(application.contains("bottle"))
        val eye = topicalApplicationText(DoseRoute.TOPICAL, "Neosporin Eye Ointment", true)!!
        assertTrue(eye.contains("affected eye"))
        assertFalse(eye.contains("skin or wound"))
        assertFalse(isSemisolidTopical(DoseRoute.TOPICAL, "Eye Drops"))
        assertFalse(isSemisolidTopical(DoseRoute.IM, "Oxytetracycline Injection"))
        assertEquals(null, topicalApplicationText(DoseRoute.TOPICAL, "Wound Spray"))
    }

    @Test
    fun `ophthalmic ointment uses estimated topical wording with eye instructions`() {
        val label = "Oxytetracycline Ophthalmic Ointment (analyzed — verify dose)"
        assertTrue(isSemisolidTopical(DoseRoute.TOPICAL, label, true))
        assertEquals("Estimated Topical Amount", volumeLabel(DoseRoute.TOPICAL, true))
        val value = topicalApplicationText(DoseRoute.TOPICAL, label, true)!!
        assertTrue(value.contains("Thin layer/ribbon (estimated)"))
        assertTrue(value.contains("affected eye"))
        assertTrue(value.contains("Do not draw this preparation into a syringe"))
        assertFalse(value.contains("mL"))
        assertFalse(value.contains("bottle"))
    }

    @Test
    fun `ophthalmic label without a preparation word is treated as an ointment`() {
        val label = "Oxytetracycline (ophthalmic)"
        assertTrue(isSemisolidTopical(DoseRoute.TOPICAL, label))
        assertTrue(isSemisolidTopical(DoseRoute.TOPICAL, label, true))
        val value = topicalApplicationText(DoseRoute.TOPICAL, label, true)!!
        assertTrue(value.contains("affected eye"))
        assertFalse(value.contains("mL"))
    }

    @Test
    fun `liquid ophthalmic preparations keep mL and bottle framing`() {
        assertFalse(isSemisolidTopical(DoseRoute.TOPICAL, "Flurbiprofen Eye Drops", true))
        assertFalse(isSemisolidTopical(
            DoseRoute.TOPICAL, "Oxytetracycline Ophthalmic Solution", true,
        ))
        assertEquals("Application Amount (mL)", volumeLabel(DoseRoute.TOPICAL, false))
        assertEquals(null, topicalApplicationText(
            DoseRoute.TOPICAL, "Oxytetracycline Ophthalmic Solution", true,
        ))
    }

    @Test
    fun `dermal wound ointments keep skin wording without syringe volume`() {
        val label = "Neosporin Ointment (wound care)"
        assertTrue(isSemisolidTopical(DoseRoute.TOPICAL, label))
        assertEquals("Estimated Topical Amount", volumeLabel(DoseRoute.TOPICAL, true))
        val value = topicalApplicationText(DoseRoute.TOPICAL, label)!!
        assertTrue(value.contains("skin or wound"))
        assertFalse(value.contains("mL"))
        assertFalse(value.contains("bottle"))
    }

    @Test
    fun `alternatives are explained as replaceable against weight`() {
        assertTrue(REPLACEABLE_ALTERNATIVES_NOTE.contains("replaceable alternatives"))
        assertTrue(REPLACEABLE_ALTERNATIVES_NOTE.contains("Weight (kg)"))
        assertTrue(REPLACEABLE_ALTERNATIVES_NOTE.contains("Choose one option"))
    }
}
