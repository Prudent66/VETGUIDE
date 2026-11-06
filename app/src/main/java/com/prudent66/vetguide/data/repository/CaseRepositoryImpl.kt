package com.prudent66.vetguide.data.repository

import com.prudent66.vetguide.database.CaseArtifactDao
import com.prudent66.vetguide.database.CaseDao
import com.prudent66.vetguide.domain.model.Case
import com.prudent66.vetguide.domain.model.CaseArtifact
import com.prudent66.vetguide.domain.model.CaseStatus
import com.prudent66.vetguide.domain.repository.CaseRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class CaseRepositoryImpl @Inject constructor(
    private val caseDao: CaseDao,
    private val caseArtifactDao: CaseArtifactDao,
) : CaseRepository {

    override fun observeCases(): Flow<List<Case>> =
        caseDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeCasesForPatient(patientId: String): Flow<List<Case>> =
        caseDao.observeByPatient(patientId).map { entities -> entities.map { it.toDomain() } }

    override fun observeCase(id: String): Flow<Case?> =
        caseDao.observeById(id).map { it?.toDomain() }

    override suspend fun getCase(id: String): Case? = caseDao.getById(id)?.toDomain()

    override suspend fun upsertCase(case: Case): String {
        val entity = case.toEntity()
        if (caseDao.getById(entity.id) == null) {
            caseDao.insert(entity)
        } else {
            caseDao.update(entity)
        }
        return entity.id
    }

    override suspend fun setCaseStatus(id: String, status: CaseStatus) {
        val now = System.currentTimeMillis()
        val updated = caseDao.updateStatus(id, status.name, now)
        require(updated > 0) { "Cannot set status on missing case id=$id" }
    }

    override suspend fun deleteCase(id: String) {
        // Artifacts are removed automatically by the ON DELETE CASCADE foreign key.
        val deleted = caseDao.deleteById(id)
        require(deleted > 0) { "Cannot delete missing case id=$id" }
    }

    override fun observeArtifactsForCase(caseId: String): Flow<List<CaseArtifact>> =
        caseArtifactDao.observeByCase(caseId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun upsertArtifact(artifact: CaseArtifact): String {
        val entity = artifact.toEntity()
        if (caseArtifactDao.getById(entity.id) == null) {
            caseArtifactDao.insert(entity)
        } else {
            caseArtifactDao.update(entity)
        }
        return entity.id
    }

    override suspend fun deleteArtifact(id: String) {
        caseArtifactDao.deleteById(id)
    }
}
