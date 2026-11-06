package com.prudent66.vetguide.domain.engine

import com.prudent66.vetguide.domain.model.DiagnosticCandidate
import com.prudent66.vetguide.domain.model.DiagnosticMatch
import kotlin.math.roundToInt

/**
 * Deterministic rule-based scoring engine (roadmap Phase 3 — no AI, no network).
 *
 * SCORING MODEL (placeholder, non-clinical):
 * Each condition carries dummy symptom weights from the seed file. The match
 * strength is the share of a condition's total placeholder weight covered by
 * the selected signs, normalized to 0–100:
 *
 *     matchStrength = Σ weights(selected ∩ condition) / Σ weights(condition) × 100
 *
 * Selecting more of a condition's own signs therefore always raises its score
 * (monotonic); signs unrelated to a condition cannot inflate its score. All
 * values are placeholders — the UI must present the veterinary-review notice.
 */
object DiagnosticEngine {

    fun evaluate(
        selectedSignIds: Set<String>,
        candidates: List<DiagnosticCandidate>,
    ): List<DiagnosticMatch> {
        if (selectedSignIds.isEmpty()) return emptyList()

        return candidates
            .mapNotNull { condition -> score(condition, selectedSignIds) }
            .sortedWith(
                compareByDescending<DiagnosticMatch> { it.matchStrengthPercent }
                    .thenBy { it.conditionName },
            )
    }

    /** Returns null when nothing relevant was selected or weights are unusable. */
    private fun score(condition: DiagnosticCandidate, selected: Set<String>): DiagnosticMatch? {
        val total = condition.symptomWeights.values.sum()
        if (total <= 0.0) return null

        val matchedIds = condition.symptomWeights.keys.filter { it in selected }
        if (matchedIds.isEmpty()) return null

        val matched = matchedIds.sumOf { condition.symptomWeights.getValue(it) }
        val percent = (matched / total * 100).roundToInt().coerceIn(0, 100)

        return DiagnosticMatch(
            conditionId = condition.conditionId,
            conditionName = condition.conditionName,
            urgency = condition.urgency,
            matchStrengthPercent = percent,
            matchedSigns = matchedIds.size,
            totalSigns = condition.symptomWeights.size,
        )
    }
}
