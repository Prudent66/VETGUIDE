package com.prudent66.vetguide.domain.model

import com.prudent66.vetguide.domain.ai.AiAnalysisData
import com.prudent66.vetguide.domain.ai.SuspectedCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for the artifact payload codec + seed renderers (Phase 7). */
class CasePayloadsTest {

    @Test
    fun `soap payload round-trips through the codec`() {
        val soap = SoapPayload(subjective = "history", objective = "findings", plan = "follow-up")

        val decoded = CasePayloadCodec.decodeOrNull<SoapPayload>(CasePayloadCodec.encode(soap))

        assertEquals(soap, decoded)
    }

    @Test
    fun `malformed artifact content decodes to null instead of crashing`() {
        assertNull(CasePayloadCodec.decodeOrNull<SoapPayload>("not json"))
        assertNull(CasePayloadCodec.decodeOrNull<SoapPayload>(null))
        // Unknown keys are ignored: extra fields decode to a default payload.
        assertEquals(
            SoapPayload(),
            CasePayloadCodec.decodeOrNull<SoapPayload>("{\"unknown\": true}"),
        )
    }

    @Test
    fun `subjective seed renders context and answers`() {
        val seed = renderSubjectiveSeed(
            clinicalContext = "Vomiting for 2 days",
            answers = listOf(
                AnsweredQuestionPayload("Stool appearance", listOf("Bloody", "Watery")),
                AnsweredQuestionPayload("Energy level", listOf("Lethargic")),
            ),
        )

        assertTrue(seed.contains("Vomiting for 2 days"))
        assertTrue(seed.contains("Stool appearance: Bloody"))
        assertTrue(seed.contains("Energy level: Lethargic"))
    }

    @Test
    fun `differentials seed names conditions with match strength`() {
        val seed = renderDifferentialsSeed(
            listOf(
                DiagnosticMatch(
                    conditionId = "parvovirus",
                    conditionName = "Parvovirus",
                    urgency = null,
                    matchStrengthPercent = 67,
                    matchedSigns = 2,
                    totalSigns = 3,
                ),
            ),
        )

        assertTrue(seed.contains("Parvovirus (67% match)"))
        assertTrue(seed.contains("requires veterinary review"))
    }

    @Test
    fun `suspected conditions seed is null without structured data`() {
        assertNull(renderSuspectedConditionsSeed(null))
        assertNull(
            renderSuspectedConditionsSeed(
                AiAnalysisData(
                    provider = "p",
                    model = "m",
                    findings = emptyList(),
                    suspectedConditions = emptyList(),
                ),
            ),
        )
        val seed = renderSuspectedConditionsSeed(
            AiAnalysisData(
                provider = "p",
                model = "m",
                findings = listOf("lesion"),
                suspectedConditions = listOf(SuspectedCondition(name = "Dermatophytosis", confidence = 0.7)),
            ),
        )
        assertTrue(seed!!.contains("Dermatophytosis (confidence 70%)"))
    }

    @Test
    fun `clinical analysis text drops the AI prediction tag`() {
        val narrative = "[AI prediction — gemini/gemini-3.5-flash] Findings: redness. " +
            "Suspected conditions (decision support, not a diagnosis): none."

        val cleaned = clinicalAnalysisText(narrative)

        assertTrue(cleaned.startsWith("Findings: redness."))
        assertFalse(cleaned.contains("AI prediction"))
        assertFalse(cleaned.contains("gemini"))
    }

    @Test
    fun `clinical analysis text keeps untagged narrative untouched`() {
        assertEquals("Findings: redness.", clinicalAnalysisText("Findings: redness."))
    }
}
