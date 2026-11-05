package com.prudent66.vetguide

import com.prudent66.vetguide.ui.navigation.Destinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 0 smoke test: bottom navigation model is complete and well-formed. */
class DestinationsTest {

    @Test
    fun `exactly the five planned top-level destinations exist in the required order`() {
        val expectedRoutes = listOf(
            Destinations.HOME,
            Destinations.DIAGNOSE,
            Destinations.CASES,
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
}
