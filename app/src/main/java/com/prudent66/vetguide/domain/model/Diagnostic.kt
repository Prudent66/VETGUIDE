package com.prudent66.vetguide.domain.model

/**
 * Inputs and outputs of the deterministic rule-based diagnostic engine
 * (roadmap Phase 3). Weights are DUMMY placeholder values — never clinical.
 */
data class DiagnosticCandidate(
    val conditionId: String,
    val conditionName: String,
    val urgency: KbUrgency?,
    /** symptomId → placeholder weight (dummy values, non-clinical). */
    val symptomWeights: Map<String, Double>,
)

data class DiagnosticMatch(
    val conditionId: String,
    val conditionName: String,
    val urgency: KbUrgency?,
    /** Normalized placeholder score, 0–100. Displayed as "Match Strength". */
    val matchStrengthPercent: Int,
    val matchedSigns: Int,
    val totalSigns: Int,
)
