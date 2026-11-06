package com.prudent66.vetguide.domain.repository

import com.prudent66.vetguide.domain.model.Case
import com.prudent66.vetguide.domain.model.CaseArtifact
import com.prudent66.vetguide.domain.model.CaseStatus
import kotlinx.coroutines.flow.Flow

/** Contract for case persistence and per-case investigation artifacts. */
interface CaseRepository {

    fun observeCases(): Flow<List<Case>>

    fun observeCasesForPatient(patientId: String): Flow<List<Case>>

    fun observeCase(id: String): Flow<Case?>

    suspend fun getCase(id: String): Case?

    /** Insert or update by id; returns the stored case id. */
    suspend fun upsertCase(case: Case): String

    suspend fun setCaseStatus(id: String, status: CaseStatus)

    suspend fun deleteCase(id: String)

    // Case artifacts (image/questionnaire/AI/SOAP)

    fun observeArtifactsForCase(caseId: String): Flow<List<CaseArtifact>>

    /** Insert or update by id; returns the stored artifact id. */
    suspend fun upsertArtifact(artifact: CaseArtifact): String

    suspend fun deleteArtifact(id: String)
}
