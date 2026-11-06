package com.prudent66.vetguide.domain.repository

import com.prudent66.vetguide.domain.model.DiagnosticCandidate
import com.prudent66.vetguide.domain.model.QuestionnaireQuestion
import com.prudent66.vetguide.domain.model.Species

/**
 * Placeholder questionnaire contracts (roadmap Phase 3). Content is seeded
 * from app/src/main/assets/questionnaire_seed.json and requires veterinary review.
 */
interface QuestionnaireRepository {

    /** Order-preserving question list for the species (empty when none seeded). */
    suspend fun loadQuestions(species: Species): List<QuestionnaireQuestion>

    /** All conditions known to the engine with their placeholder weights. */
    suspend fun loadCandidates(): List<DiagnosticCandidate>
}
