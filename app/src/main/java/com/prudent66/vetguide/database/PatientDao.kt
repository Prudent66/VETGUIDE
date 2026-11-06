package com.prudent66.vetguide.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {

    @Insert
    suspend fun insert(patient: PatientEntity)

    @Update
    suspend fun update(patient: PatientEntity)

    @Delete
    suspend fun delete(patient: PatientEntity)

    @Query("DELETE FROM patients WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM patients ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<PatientEntity>>

    @Query("SELECT * FROM patients WHERE id = :id")
    fun observeById(id: String): Flow<PatientEntity?>

    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun getById(id: String): PatientEntity?
}
