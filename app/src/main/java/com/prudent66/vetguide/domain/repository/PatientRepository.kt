package com.prudent66.vetguide.domain.repository

import com.prudent66.vetguide.domain.model.Patient
import kotlinx.coroutines.flow.Flow

/** Contract for patient persistence. UI depends only on this interface. */
interface PatientRepository {

    fun observePatients(): Flow<List<Patient>>

    fun observePatient(id: String): Flow<Patient?>

    suspend fun getPatient(id: String): Patient?

    /** Insert or update by id; returns the stored patient id. */
    suspend fun upsertPatient(patient: Patient): String

    suspend fun deletePatient(id: String): Boolean
}
