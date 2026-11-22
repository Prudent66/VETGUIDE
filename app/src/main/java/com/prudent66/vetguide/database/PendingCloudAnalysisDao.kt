package com.prudent66.vetguide.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** DAO for the store-and-forward cloud analysis queue. */
@Dao
interface PendingCloudAnalysisDao {

    @Insert
    suspend fun insert(analysis: PendingCloudAnalysisEntity)

    @Update
    suspend fun update(analysis: PendingCloudAnalysisEntity)

    @Delete
    suspend fun delete(analysis: PendingCloudAnalysisEntity)

    @Query("DELETE FROM pending_cloud_analyses WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM pending_cloud_analyses WHERE id = :id")
    suspend fun getById(id: String): PendingCloudAnalysisEntity?

    @Query("SELECT * FROM pending_cloud_analyses ORDER BY createdAtEpochMs ASC")
    fun observeAll(): Flow<List<PendingCloudAnalysisEntity>>
}
