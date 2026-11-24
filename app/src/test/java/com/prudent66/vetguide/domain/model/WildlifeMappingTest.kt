package com.prudent66.vetguide.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wildlife groups must map to the expected diseases and biosafety cautions. */
class WildlifeMappingTest {

    @Test
    fun `rabies covers canids felids primates and small mammals`() {
        val rabies = WildlifeDiseaseCatalog.diseases.first { it.id == "rabies" }
        assertTrue(WildlifeGroup.WILD_CANIDS in rabies.groups)
        assertTrue(WildlifeGroup.WILD_FELIDS in rabies.groups)
        assertTrue(WildlifeGroup.PRIMATES in rabies.groups)
        assertTrue(rabies.caution.contains("Zoonotic", ignoreCase = true))
    }

    @Test
    fun `all eight named wildlife diseases are present`() {
        val ids = WildlifeDiseaseCatalog.diseases.map { it.id }.toSet()
        listOf(
            "rabies",
            "anthrax",
            "capture-myopathy",
            "wildlife-avian-influenza",
            "wild-morbillivirus",
            "canine-distemper",
            "sarcoptic-mange",
            "wildlife-trypanosomiasis",
        ).forEach { assertTrue("missing $it", it in ids) }
        assertEquals(8, ids.size)
    }

    @Test
    fun `capture myopathy applies to every wildlife group`() {
        val myopathy = WildlifeDiseaseCatalog.diseases.first { it.id == "capture-myopathy" }
        assertEquals(WildlifeGroup.entries.toSet(), myopathy.groups)
    }

    @Test
    fun `avian influenza is restricted to wild birds`() {
        val avian = WildlifeDiseaseCatalog.forGroup(WildlifeGroup.WILD_BIRDS)
        assertTrue(avian.any { it.id == "wildlife-avian-influenza" })
        assertTrue(
            WildlifeDiseaseCatalog.forGroup(WildlifeGroup.WILD_RUMINANTS)
                .none { it.id == "wildlife-avian-influenza" },
        )
    }
}
