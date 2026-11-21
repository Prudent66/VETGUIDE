package com.prudent66.vetguide.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CaseArtifactDao {

    @Insert
    suspend fun insert(artifact: CaseArtifactEntity)

    @Update
    suspend fun update(artifact: CaseArtifactEntity)

    @Query("DELETE FROM case_artifacts WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM case_artifacts WHERE caseId = :caseId ORDER BY createdAtEpochMs ASC")
    fun observeByCase(caseId: String): Flow<List<CaseArtifactEntity>>

    @Query("SELECT * FROM case_artifacts WHERE id = :id")
    suspend fun getById(id: String): CaseArtifactEntity?

    @Query("SELECT * FROM case_artifacts")
    suspend fun getAll(): List<CaseArtifactEntity>

    /** Backup restore: insert or replace by primary key, never delete. */
    @Upsert
    suspend fun upsertAll(artifacts: List<CaseArtifactEntity>)
}
