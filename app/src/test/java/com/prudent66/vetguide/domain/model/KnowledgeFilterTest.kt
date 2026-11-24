package com.prudent66.vetguide.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Strict domestic/wild separation and explicit urgency for wildlife entries. */
class KnowledgeFilterTest {

    private fun summary(id: String, tags: List<String>, urgency: KbUrgency) =
        KbConditionSummary(id = id, name = id, speciesTags = tags, urgency = urgency)

    private val domestic = summary(
        "newcastle-disease",
        listOf("POULTRY"),
        KbUrgency.EMERGENCY,
    )
    private val wildlife = summary(
        "wildlife-avian-influenza",
        listOf("WILD_BIRDS"),
        KbUrgency.EMERGENCY,
    )

    @Test
    fun `domestic pathway excludes wildlife conditions`() {
        assertTrue(KnowledgeFilters.matches(domestic, AnimalCategory.DOMESTIC, null))
        assertFalse(KnowledgeFilters.matches(wildlife, AnimalCategory.DOMESTIC, null))
        assertFalse(
            KnowledgeFilters.matches(
                domestic,
                AnimalCategory.DOMESTIC,
                "WILD_RUMINANTS",
            ),
        )
    }

    @Test
    fun `wild pathway excludes domestic conditions and filters by group`() {
        assertTrue(KnowledgeFilters.matches(wildlife, AnimalCategory.WILD, null))
        assertFalse(KnowledgeFilters.matches(domestic, AnimalCategory.WILD, null))
        assertTrue(
            KnowledgeFilters.matches(
                wildlife,
                AnimalCategory.WILD,
                WildlifeGroup.WILD_BIRDS.label,
            ),
        )
        assertFalse(
            KnowledgeFilters.matches(
                wildlife,
                AnimalCategory.WILD,
                WildlifeGroup.PRIMATES.label,
            ),
        )
    }

    @Test
    fun `filter options follow the selected pathway`() {
        assertEquals(
            listOf(
                "All species", "Cattle", "Goat", "Sheep", "Poultry", "Pig", "Equine", "Dog", "Cat",
            ),
            KnowledgeFilters.filterOptions(AnimalCategory.DOMESTIC),
        )
        val wild = KnowledgeFilters.filterOptions(AnimalCategory.WILD)
        assertEquals(8, wild.size)
        assertTrue(WildlifeGroup.WILD_FELIDS.label in wild)
    }
}
