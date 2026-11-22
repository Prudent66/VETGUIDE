package com.prudent66.vetguide.domain.repository

import com.prudent66.vetguide.database.PendingCloudAnalysisEntity
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for the store-and-forward cloud analysis queue.
 */
interface PendingCloudAnalysisRepository {

    /** Adds a new pending analysis to the queue. */
    suspend fun add(analysis: PendingCloudAnalysisEntity)

    /** Observes all pending analyses. */
    fun observeAll(): Flow<List<PendingCloudAnalysisEntity>>

    /** Removes a completed/failed analysis from the queue. */
    suspend fun remove(id: String)
}
