package com.prudent66.vetguide.data.kb

import com.prudent66.vetguide.domain.model.Species
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the offline seed assets used by the upgrade-in-place startup check. */
class SeedCompletenessTest {

    private fun asset(name: String): String {
        val candidates = listOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Seed asset not found: $name (tried ${candidates.joinToString()})")
        return file.readText()
    }

    @Test
    fun `all eight species have active offline questionnaires`() {
        val payload = QuestionnaireSeedParser.parse(asset("questionnaire_seed.json"))
        val bySpecies = payload.questions.groupBy { it.species }

        Species.entries.filter { it != Species.OTHER }.forEach { species ->
            val questions = bySpecies[species.name].orEmpty()
            assertTrue("$species has no seeded questions", questions.isNotEmpty())
            assertTrue("$species has fewer than four questions", questions.size >= 4)
            questions.forEach { question ->
                assertTrue("question ${question.id} has no options", question.options.isNotEmpty())
            }
        }
        assertTrue("questionnaire seed version must be bumped", payload.version >= 2)
    }

    @Test
    fun `wildlife conditions carry an explicit emergency or urgent tag`() {
        val payload = KbSeedParser.parse(asset("kb_seed.json"))
        val wildlife = payload.conditions.filter { condition ->
            condition.species.any { it.startsWith("WILD_") || it == "PRIMATES" || it == "REPTILES" }
        }

        assertTrue("expected wildlife conditions", wildlife.isNotEmpty())
        wildlife.forEach { condition ->
            assertTrue(
                "${condition.id} has no usable urgency",
                condition.urgency in setOf("EMERGENCY", "URGENT"),
            )
        }
    }

    @Test
    fun `knowledge base contains every named outbreak condition`() {
        val payload = KbSeedParser.parse(asset("kb_seed.json"))
        val ids = payload.conditions.map { it.id }.toSet()

        listOf(
            "contagious-bovine-pleuropneumonia",
            "peste-des-petits-ruminants",
            "fowl-pox",
            "newcastle-disease",
            "foot-and-mouth-disease",
            "mastitis",
            "bovine-helminthosis",
            "helminthosis-multi-species",
        ).forEach { id -> assertTrue("missing KB condition $id", id in ids) }
        assertTrue("KB seed version must be bumped", payload.version >= 3)
    }
}
