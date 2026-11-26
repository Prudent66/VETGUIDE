package com.prudent66.vetguide.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Split between drug prescriptions/dosages and non-drug supportive care. */
class PrescriptionPlanTest {

    private fun data(
        nextSteps: List<String> = emptyList(),
        prescriptions: List<String> = emptyList(),
    ) = AiAnalysisData(
        provider = "p",
        model = "m",
        findings = emptyList(),
        suspectedConditions = emptyList(),
        recommendedNextSteps = nextSteps,
        prescriptions = prescriptions,
    )

    @Test
    fun `explicit prescriptions win and supportive care stays separate`() {
        val plan = buildPrescriptionPlan(
            data(
                nextSteps = listOf("Isolate affected birds", "Clean drinkers"),
                prescriptions = listOf("Tylosin 50 mg/kg PO for 3 days", "   "),
            ),
        )

        assertEquals(listOf("Tylosin 50 mg/kg PO for 3 days"), plan.prescriptions)
        assertEquals(listOf("Isolate affected birds", "Clean drinkers"), plan.supportiveCare)
        assertTrue(plan.hasPrescriptions)
    }

    @Test
    fun `legacy next steps are partitioned into prescriptions and supportive care`() {
        val plan = buildPrescriptionPlan(
            data(
                nextSteps = listOf(
                    "Amoxicillin 500 mg PO twice daily for 5 days",
                    "Isolate affected birds",
                    "Give 10 ml/kg IV fluids once",
                    "Follow up in 1 week",
                ),
            ),
        )

        assertEquals(
            listOf(
                "Amoxicillin 500 mg PO twice daily for 5 days",
                "Give 10 ml/kg IV fluids once",
            ),
            plan.prescriptions,
        )
        assertEquals(
            listOf("Isolate affected birds", "Follow up in 1 week"),
            plan.supportiveCare,
        )
    }

    @Test
    fun `narrative-only offline output yields extracted prescriptions`() {
        val plan = buildPrescriptionPlan(
            analysisData = null,
            narrative = "Offline mode — cloud analysis unavailable. Findings: dehydration. " +
                "Give oxytetracycline 20 mg/kg IM once daily for 3 days. " +
                "Isolate affected birds.",
        )

        assertEquals(
            listOf("Give oxytetracycline 20 mg/kg IM once daily for 3 days"),
            plan.prescriptions,
        )
        assertTrue(plan.supportiveCare.isEmpty())
    }

    @Test
    fun `supportive-only analysis has no prescriptions`() {
        val plan = buildPrescriptionPlan(
            data(
                nextSteps = listOf("Provide shade and clean water", "Disinfect drinkers"),
            ),
        )

        assertFalse(plan.hasPrescriptions)
        assertEquals(
            listOf("Provide shade and clean water", "Disinfect drinkers"),
            plan.supportiveCare,
        )
    }

    @Test
    fun `do not use warnings are not returned as prescriptions`() {
        val plan = buildPrescriptionPlan(
            data(
                nextSteps = listOf(
                    "Apply Flurbiprofen 0.03% ophthalmic solution twice daily",
                    "Do not use prednisolone eye drops unless an ulcer has been ruled out",
                ),
            ),
        )

        assertEquals(1, plan.prescriptions.size)
        assertTrue(plan.prescriptions.first().contains("Flurbiprofen"))
        assertTrue(plan.supportiveCare.isEmpty())
    }

    @Test
    fun `narrative do not use lines are not extracted as prescriptions`() {
        val plan = buildPrescriptionPlan(
            analysisData = null,
            narrative = "Apply Flurbiprofen 0.03% ophthalmic solution twice daily. " +
                "Do not use prednisolone eye drops unless an ulcer has been ruled out.",
        )
        assertEquals(1, plan.prescriptions.size)
        assertTrue(plan.prescriptions.first().contains("Flurbiprofen"))
    }

    @Test
    fun `no structured plan and no narrative is empty`() {
        assertFalse(buildPrescriptionPlan(null).hasPrescriptions)
        assertFalse(buildPrescriptionPlan(data()).hasPrescriptions)
    }
}
