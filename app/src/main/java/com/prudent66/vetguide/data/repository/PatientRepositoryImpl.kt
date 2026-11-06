package com.prudent66.vetguide.data.repository

import com.prudent66.vetguide.database.PatientDao
import com.prudent66.vetguide.domain.model.Patient
import com.prudent66.vetguide.domain.repository.PatientRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PatientRepositoryImpl @Inject constructor(
    private val patientDao: PatientDao,
) : PatientRepository {

    override fun observePatients(): Flow<List<Patient>> =
        patientDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observePatient(id: String): Flow<Patient?> =
        patientDao.observeById(id).map { it?.toDomain() }

    override suspend fun getPatient(id: String): Patient? =
        patientDao.getById(id)?.toDomain()

    override suspend fun upsertPatient(patient: Patient): String {
        val entity = patient.toEntity()
        val existing = patientDao.getById(entity.id)
        if (existing == null) {
            patientDao.insert(entity)
        } else {
            patientDao.update(entity)
        }
        return entity.id
    }

    override suspend fun deletePatient(id: String): Boolean =
        patientDao.deleteById(id) > 0
}
