package com.prudent66.vetguide.data.repository

import com.prudent66.vetguide.database.PendingCloudAnalysisDao
import com.prudent66.vetguide.database.PendingCloudAnalysisEntity
import com.prudent66.vetguide.domain.repository.PendingCloudAnalysisRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Room-backed implementation of the pending cloud analysis queue. */
@Singleton
class PendingCloudAnalysisRepositoryImpl @Inject constructor(
    private val dao: PendingCloudAnalysisDao,
) : PendingCloudAnalysisRepository {

    override suspend fun add(analysis: PendingCloudAnalysisEntity) {
        dao.insert(analysis)
    }

    override fun observeAll(): Flow<List<PendingCloudAnalysisEntity>> = dao.observeAll()

    override suspend fun remove(id: String) {
        dao.deleteById(id)
    }
}