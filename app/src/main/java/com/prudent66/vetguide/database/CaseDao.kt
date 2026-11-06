package com.prudent66.vetguide.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CaseDao {

    @Insert
    suspend fun insert(case: CaseEntity)

    @Update
    suspend fun update(case: CaseEntity)

    @Query("DELETE FROM cases WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM cases ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<CaseEntity>>

    @Query("SELECT * FROM cases WHERE patientId = :patientId ORDER BY updatedAtEpochMs DESC")
    fun observeByPatient(patientId: String): Flow<List<CaseEntity>>

    @Query("SELECT * FROM cases WHERE id = :id")
    fun observeById(id: String): Flow<CaseEntity?>

    @Query("SELECT * FROM cases WHERE id = :id")
    suspend fun getById(id: String): CaseEntity?

    @Query("UPDATE cases SET status = :status, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAtEpochMs: Long): Int
}
