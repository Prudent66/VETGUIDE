package com.prudent66.vetguide.domain.repository

import com.prudent66.vetguide.domain.model.KbConditionDetail
import com.prudent66.vetguide.domain.model.KbConditionSummary
import com.prudent66.vetguide.domain.model.Species
import kotlinx.coroutines.flow.Flow

/** Offline knowledge base contracts (roadmap §9, Phase 2). */
interface KnowledgeRepository {

    fun observeConditions(query: String, species: Species?): Flow<List<KbConditionSummary>>

    fun observeConditionDetail(conditionId: String): Flow<KbConditionDetail?>
}
