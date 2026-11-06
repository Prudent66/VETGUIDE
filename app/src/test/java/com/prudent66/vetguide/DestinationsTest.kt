package com.prudent66.vetguide

import com.prudent66.vetguide.ui.navigation.Destinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 0/1 smoke test: navigation model is complete and well-formed. */
class DestinationsTest {

    @Test
    fun `exactly the five planned top-level destinations exist in the required order`() {
        val expectedRoutes = listOf(
            Destinations.HOME,
            Destinations.DIAGNOSE,
            Destinations.PATIENTS,
            Destinations.KNOWLEDGE,
            Destinations.SETTINGS,
        )
        assertEquals(expectedRoutes, Destinations.items.map { it.route })
    }

    @Test
    fun `every destination has a unique route with a visible label and an icon`() {
        Destinations.items.forEach { item ->
            assertTrue(item.label.isNotBlank())
            assertNotEquals(0, item.iconRes)
        }
        assertEquals(5, Destinations.items.map { it.route }.toSet().size)
    }

    @Test
    fun `patients tab is a top-level route but drill-in routes are not`() {
        assertTrue(Destinations.isTopLevel(Destinations.PATIENTS))
        assertFalse(Destinations.isTopLevel(Destinations.PATIENTS_ADD))
        assertFalse(Destinations.isTopLevel(Destinations.casesForPatient("abc")))
        assertFalse(Destinations.isTopLevel(null))
    }

    @Test
    fun `casesForPatient embeds the patient id into the route pattern`() {
        assertEquals(
            Destinations.CASES_FOR_PATIENT.replace("{${Destinations.ARG_PATIENT_ID}}", "abc"),
            Destinations.casesForPatient("abc"),
        )
        assertTrue(Destinations.casesForPatient("p1") != Destinations.casesForPatient("p2"))
    }
}
