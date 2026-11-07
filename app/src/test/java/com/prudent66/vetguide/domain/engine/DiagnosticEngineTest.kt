package com.prudent66.vetguide.domain.engine

import com.prudent66.vetguide.domain.model.DiagnosticCandidate
import com.prudent66.vetguide.domain.model.KbUrgency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the deterministic placeholder scoring engine (roadmap Phase 3).
 * Weights below are dummy values — no clinical meaning asserted or implied.
 */
class DiagnosticEngineTest {

    private val parvovirus = DiagnosticCandidate(
        conditionId = "canine-parvovirus",
        conditionName = "Canine Parvovirus",
        urgency = KbUrgency.EMERGENCY,
        symptomWeights = mapOf(
            "vomiting" to 10.0,
            "hemorrhagic-diarrhea" to 10.0,
            "lethargy" to 6.0,
            "anorexia" to 4.0,
        ),
    )

    private val newcastle = DiagnosticCandidate(
        conditionId = "newcastle-disease",
        conditionName = "Newcastle Disease",
        urgency = KbUrgency.EMERGENCY,
        symptomWeights = mapOf(
            "respiratory-distress" to 10.0,
            "reduced-egg-production" to 10.0,
            "lethargy" to 6.0,
        ),
    )

    @Test
    fun `more signs of the condition raise its match strength`() {
        val one = DiagnosticEngine.evaluate(setOf("vomiting"), listOf(parvovirus))
        val two = DiagnosticEngine.evaluate(
            setOf("vomiting", "hemorrhagic-diarrhea"),
            listOf(parvovirus),
        )

        assertEquals(33, one[0].matchStrengthPercent) // 10 / 30
        assertEquals(67, two[0].matchStrengthPercent) // 20 / 30
        assertEquals(2, two[0].matchedSigns)
    }

    @Test
    fun `conditions rank by coverage of their own signs`() {
        val results = DiagnosticEngine.evaluate(
            setOf("vomiting", "lethargy"),
            listOf(parvovirus, newcastle),
        )

        assertEquals(2, results.size)
        assertEquals("canine-parvovirus", results[0].conditionId)
        assertEquals(53, results[0].matchStrengthPercent) // 16 / 30
        assertEquals("newcastle-disease", results[1].conditionId)
        assertEquals(23, results[1].matchStrengthPercent) // 6 / 26
    }

    @Test
    fun `conditions with no matching signs are excluded`() {
        val results = DiagnosticEngine.evaluate(setOf("fever"), listOf(parvovirus, newcastle))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `empty selection yields no results`() {
        assertTrue(DiagnosticEngine.evaluate(emptySet(), listOf(parvovirus)).isEmpty())
    }

    @Test
    fun `unrelated signs cannot inflate a condition score`() {
        val withNoise = DiagnosticEngine.evaluate(
            setOf("vomiting", "coughing", "wasting"),
            listOf(parvovirus),
        )
        val clean = DiagnosticEngine.evaluate(setOf("vomiting"), listOf(parvovirus))

        assertEquals(clean[0].matchStrengthPercent, withNoise[0].matchStrengthPercent)
    }

    @Test
    fun `ties broken alphabetically by condition name`() {
        val alpha = DiagnosticCandidate(
            conditionId = "alpha-condition",
            conditionName = "Alpha",
            urgency = null,
            symptomWeights = mapOf("lethargy" to 10.0),
        )
        val beta = DiagnosticCandidate(
            conditionId = "beta-condition",
            conditionName = "Beta",
            urgency = KbUrgency.ROUTINE,
            symptomWeights = mapOf("lethargy" to 10.0),
        )

        val results = DiagnosticEngine.evaluate(setOf("lethargy"), listOf(beta, alpha))
        assertEquals(listOf("Alpha", "Beta"), results.map { it.conditionName })
    }

    @Test
    fun `scores stay within the normalized 0-100 band`() {
        val results = DiagnosticEngine.evaluate(
            setOf("vomiting", "hemorrhagic-diarrhea", "lethargy", "anorexia"),
            listOf(parvovirus, newcastle),
        )
        results.forEach { match ->
            assertTrue(match.matchStrengthPercent in 0..100)
        }
    }

    @Test
    fun `urgency is carried through to the result`() {
        val results = DiagnosticEngine.evaluate(setOf("vomiting"), listOf(parvovirus, newcastle))
        assertEquals(KbUrgency.EMERGENCY, results.first { it.conditionId == "canine-parvovirus" }.urgency)
    }
}
